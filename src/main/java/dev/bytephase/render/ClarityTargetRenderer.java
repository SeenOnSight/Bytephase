package dev.bytephase.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.TargetFx;
import dev.bytephase.render.shader.GlShader;
import dev.bytephase.render.shader.GlState;
import dev.bytephase.render.shader.GlTextures;
import java.nio.ByteBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

/**
 * Draws the supplied {@code worldfx/target} shader as a reticle around the
 * entity under the crosshair. Geometry is rebuilt from the original Clarity
 * style definitions; the shader itself is unchanged.
 */
public final class ClarityTargetRenderer {
    public static final String[] STYLES = {
            "Plasma Ring", "Reticle", "Vortex", "Energy Shield",
            "Marker", "Triangle Slice", "Reaper", "Shatter Lock"
    };

    private static final float[] RIGHT = new float[3];
    private static final float[] UP = new float[3];
    private static final float[] FORWARD = new float[3];
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final int[] QUAD_ORDER = {0, 1, 2, 0, 2, 3};

    private static GlShader shader;
    private static boolean failed;
    private static int vao;
    private static int vbo;
    private static int vertices;
    private static int lastTargetId = -1;
    private static float enter;
    private static long startNanos;
    private static long lastNanos;
    private static boolean bufferDisposed;

    private ClarityTargetRenderer() {
    }

    public static void dispose() {
        if (shader != null) {
            shader.delete();
            shader = null;
        }
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
            vao = 0;
        }
        if (vbo != 0) {
            GL15.glDeleteBuffers(vbo);
            vbo = 0;
        }
        vertices = 0;
        lastTargetId = -1;
        if (!bufferDisposed) {
            MemoryUtil.memFree(LAST_BUFFER);
            bufferDisposed = true;
        }
    }

    public static void render(TargetFx module, float tickDelta) {
        if (failed) {
            return;
        }
        MinecraftClient mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        Entity target = mc.crosshairTarget instanceof EntityHitResult hit ? hit.getEntity() : null;
        if (target == null || !target.isAlive() || target.isRemoved()) {
            lastTargetId = -1;
            return;
        }
        if (target.squaredDistanceTo(mc.player) > module.range() * module.range()) {
            lastTargetId = -1;
            return;
        }
        if (!(target instanceof LivingEntity)) {
            return;
        }
        if (!ensureShader()) {
            return;
        }

        long now = System.nanoTime();
        if (startNanos == 0L) {
            startNanos = now;
        }
        float delta = lastNanos == 0L ? 0.016f
                : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;
        if (target.getId() != lastTargetId) {
            lastTargetId = target.getId();
            enter = 0.0f;
        }
        enter += (1.0f - enter) * Math.min(1.0f, delta * 8.0f);

        Vec3d pos = target.getLerpedPos(tickDelta);
        float width = target.getWidth() * module.scale();
        float height = target.getHeight() * module.scale();
        float x = (float) (pos.x - RenderProjection.cameraPos().x);
        float y = (float) (pos.y - RenderProjection.cameraPos().y);
        float z = (float) (pos.z - RenderProjection.cameraPos().z);

        RenderProjection.cameraAxes(RIGHT, UP, FORWARD);
        RenderProjection.viewProjection(VIEW_PROJECTION);
        if (!buildGeometry(module.styleIndex(), x, y, z, width, height,
                module.primary().redF(), module.primary().greenF(), module.primary().blueF())) {
            return;
        }

        try (GlState ignored = GlState.capture()) {
            if (!GlTextures.bindMain()) {
                return;
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(false);
            if (module.throughWalls()) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            } else {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
            }
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);

            shader.bind();
            shader.setMatrix4f("uViewProj", VIEW_PROJECTION);
            shader.set3f("uRight", RIGHT[0], RIGHT[1], RIGHT[2]);
            shader.set3f("uUp", UP[0], UP[1], UP[2]);
            shader.set3f("uFwd", FORWARD[0], FORWARD[1], FORWARD[2]);
            shader.set1f("uTime", (float) ((now - startNanos) / 1.0e9 % 3600.0));
            shader.set1i("uStyle", module.styleIndex());
            shader.set3f("uColor2", module.secondary().rgb());
            shader.set1f("uFlash", target instanceof LivingEntity living
                    ? Math.max(0.0f, Math.min(1.0f, living.hurtTime / 10.0f)) : 0.0f);
            shader.set1f("uIn", enter);

            GlStateManagerBridge.bindVertexArray(vao);
            GlStateManagerBridge.bindBuffer(vbo);
            GlStateManagerBridge.upload(LAST_BUFFER);
            GlStateManagerBridge.draw(vertices);
            GlStateManagerBridge.bindVertexArray(0);
            shader.unbind();
        } catch (Throwable error) {
            failed = true;
            BytePhase.LOGGER.error("Clarity target shader pass failed", error);
        }
    }

    private static final ByteBuffer LAST_BUFFER = MemoryUtil.memAlloc(8192 * Float.BYTES);

    private static boolean buildGeometry(int style, float x, float y, float z,
                                         float width, float height, float r, float g, float b) {
        vertices = 0;
        LAST_BUFFER.clear();
        switch (style) {
            case 0 -> {
                float size = (width * 0.7f + 0.35f) * 1.25f;
                quad(x, y + 0.02f, z, 1, 0, 0, 0, 0, 1, size, size,
                        -1.25f, -1.25f, 1.25f, 1.25f, r, g, b);
            }
            case 1 -> {
                float size = Math.max(width, height) * 0.62f * 1.3f
                        * (1.0f + (1.0f - enter) * 0.8f);
                quad(x, y + height * 0.5f, z, RIGHT, UP, size, size,
                        -1.3f, -1.3f, 1.3f, 1.3f, r, g, b);
            }
            case 2 -> {
                float radius = width * 0.85f + 0.1f;
                float bodyHeight = height * 1.2f;
                int segments = 24;
                for (int i = 0; i < segments; i++) {
                    double a0 = Math.PI * 2.0 * i / segments;
                    double a1 = Math.PI * 2.0 * (i + 1) / segments;
                    float x0 = x + (float) Math.cos(a0) * radius;
                    float z0 = z + (float) Math.sin(a0) * radius;
                    float x1 = x + (float) Math.cos(a1) * radius;
                    float z1 = z + (float) Math.sin(a1) * radius;
                    float u0 = (float) i / segments;
                    float u1 = (float) (i + 1) / segments;
                    vertex(x0, y, z0, u0, 0, r, g, b);
                    vertex(x1, y, z1, u1, 0, r, g, b);
                    vertex(x1, y + bodyHeight, z1, u1, 1, r, g, b);
                    vertex(x0, y, z0, u0, 0, r, g, b);
                    vertex(x1, y + bodyHeight, z1, u1, 1, r, g, b);
                    vertex(x0, y + bodyHeight, z0, u0, 1, r, g, b);
                }
            }
            case 3 -> {
                float size = Math.max(width, height) * 0.62f;
                quad(x, y + height * 0.5f, z, RIGHT, UP, size, size,
                        -1.0f, -1.0f, 1.0f, 1.0f, r, g, b);
            }
            case 4 -> {
                float size = 0.364f;
                quad(x, y + height + 0.55f, z, RIGHT, UP, size, size,
                        -1.3f, -1.3f, 1.3f, 1.3f, r, g, b);
            }
            case 5, 6 -> {
                float rx = RIGHT[0];
                float rz = RIGHT[2];
                float rl = (float) Math.sqrt(rx * rx + rz * rz);
                if (rl < 1.0e-4f) {
                    rx = 1.0f;
                    rz = 0.0f;
                } else {
                    rx /= rl;
                    rz /= rl;
                }
                float fx = FORWARD[0];
                float fz = FORWARD[2];
                float fl = (float) Math.sqrt(fx * fx + fz * fz);
                if (fl < 1.0e-4f) {
                    fx = 0.0f;
                    fz = 1.0f;
                } else {
                    fx /= fl;
                    fz /= fl;
                }
                float angle = 0.6f;
                float c = (float) Math.cos(angle);
                float s = (float) Math.sin(angle);
                float size = (width * 0.9f + 0.5f) * 1.3f;
                quad(x, y + height * 0.55f, z,
                        rx, 0, rz, fx * c, s, fz * c, size, size,
                        -1.3f, -1.3f, 1.3f, 1.3f, r, g, b);
            }
            default -> {
                float size = Math.max(width, height) * 0.62f * 1.3f;
                quad(x, y + height * 0.5f, z, RIGHT, UP, size, size,
                        -1.3f, -1.3f, 1.3f, 1.3f, r, g, b);
            }
        }
        LAST_BUFFER.flip();
        return vertices != 0;
    }

    private static void quad(float x, float y, float z, float[] right, float[] up,
                             float w, float h, float u0, float v0, float u1, float v1,
                             float r, float g, float b) {
        quad(x, y, z, right[0], right[1], right[2], up[0], up[1], up[2],
                w, h, u0, v0, u1, v1, r, g, b);
    }

    private static void quad(float x, float y, float z,
                             float rx, float ry, float rz,
                             float ux, float uy, float uz,
                             float w, float h,
                             float u0, float v0, float u1, float v1,
                             float r, float g, float b) {
        float cx = (u0 + u1) * 0.5f;
        float cy = (v0 + v1) * 0.5f;
        float dx = (u1 - u0) * 0.5f;
        float dy = (v1 - v0) * 0.5f;
        for (int i = 0; i < QUAD_ORDER.length; i++) {
            int corner = QUAD_ORDER[i];
            float u = corner == 0 || corner == 3 ? u0 : u1;
            float v = corner < 2 ? v0 : v1;
            float nx = (u - cx) / dx;
            float ny = (v - cy) / dy;
            vertex(x + rx * w * nx + ux * h * ny,
                    y + ry * w * nx + uy * h * ny,
                    z + rz * w * nx + uz * h * ny,
                    u, v, r, g, b);
        }
    }

    private static void vertex(float x, float y, float z, float u, float v,
                               float r, float g, float b) {
        LAST_BUFFER.putFloat(x).putFloat(y).putFloat(z)
                .putFloat(u).putFloat(v)
                .putFloat(r).putFloat(g).putFloat(b).putFloat(1.0f)
                .putFloat(0.0f).putFloat(0.0f);
        vertices++;
    }

    private static boolean ensureShader() {
        if (shader != null) {
            return true;
        }
        try {
            shader = GlShader.load("worldfx", "target");
            vao = GL30.glGenVertexArrays();
            vbo = GL15.glGenBuffers();
            int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            int stride = 11 * Float.BYTES;
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 3L * Float.BYTES);
            GL20.glEnableVertexAttribArray(2);
            GL20.glVertexAttribPointer(2, 4, GL11.GL_FLOAT, false, stride, 5L * Float.BYTES);
            GL20.glEnableVertexAttribArray(3);
            GL20.glVertexAttribPointer(3, 2, GL11.GL_FLOAT, false, stride, 9L * Float.BYTES);
            GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
            return true;
        } catch (Throwable error) {
            failed = true;
            BytePhase.LOGGER.error("Could not load supplied target shader", error);
            return false;
        }
    }

    private static final class GlStateManagerBridge {
        private static void bindVertexArray(int value) {
            com.mojang.blaze3d.opengl.GlStateManager._glBindVertexArray(value);
        }

        private static void bindBuffer(int value) {
            com.mojang.blaze3d.opengl.GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, value);
        }

        private static void upload(ByteBuffer data) {
            com.mojang.blaze3d.opengl.GlStateManager._glBufferData(
                    GL15.GL_ARRAY_BUFFER, data, GL15.GL_DYNAMIC_DRAW);
        }

        private static void draw(int vertexCount) {
            com.mojang.blaze3d.opengl.GlStateManager._drawArrays(
                    GL11.GL_TRIANGLES, 0, vertexCount);
        }
    }
}
