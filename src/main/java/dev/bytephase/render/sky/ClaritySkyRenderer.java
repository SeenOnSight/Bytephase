package dev.bytephase.render.sky;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.SkyShaders;
import dev.bytephase.render.RenderProjection;
import dev.bytephase.render.shader.FullscreenQuad;
import dev.bytephase.render.shader.GlSamplers;
import dev.bytephase.render.shader.GlShader;
import dev.bytephase.render.shader.GlState;
import dev.bytephase.render.shader.GlTarget;
import dev.bytephase.render.shader.GlTextures;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.system.MemoryUtil;

/**
 * Adapter for the supplied Clarity sky shaders. It renders after vanilla's
 * sky and before terrain, which is where the visual source expects a sky
 * replacement to run.
 */
public final class ClaritySkyRenderer {
    public static final String[] PRESETS = {
            "Aurora", "Black Hole", "Blood Moon", "Dreamscape", "Galaxy",
            "Nebula", "Prism", "Synthwave", "Thunderstorm"
    };
    private static final Vector3f CELESTIAL_AXIS = new Vector3f(0.0f, 0.766f, 0.643f);
    private static final Map<String, GlShader> PROGRAMS = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final Matrix4f INVERSE = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix3f CELESTIAL = new Matrix3f();
    private static final Vector4f NEAR_POINT = new Vector4f();
    private static final Vector4f FAR_POINT = new Vector4f();
    private static final float[] COLOR1 = new float[3];
    private static final float[] COLOR2 = new float[3];
    private static final float[] HORIZON = new float[3];
    private static final GlTarget LOW = new GlTarget();

    private static GlShader upscale;
    private static boolean upscaleFailed;
    private static int noiseTexture;
    // Apple's GL-on-Metal driver may still be reading this buffer when the
    // upload call returns; freeing it straight away faults inside
    // libGLImage (SIGBUS in glgVectorCopy). Keep it alive with the texture.
    private static ByteBuffer noisePixels;
    private static long lastNanos;
    private static double animationTime;
    private static double celestialAngle;

    private ClaritySkyRenderer() {
    }

    public static void dispose() {
        for (GlShader shader : PROGRAMS.values()) {
            shader.delete();
        }
        PROGRAMS.clear();
        FAILED.clear();
        if (upscale != null) {
            upscale.delete();
            upscale = null;
        }
        if (noiseTexture != 0) {
            GL11.glDeleteTextures(noiseTexture);
            noiseTexture = 0;
        }
        if (noisePixels != null) {
            MemoryUtil.memFree(noisePixels);
            noisePixels = null;
        }
        LOW.close();
        upscaleFailed = false;
        lastNanos = 0L;
        animationTime = 0.0;
        celestialAngle = 0.0;
    }

    public static void render(SkyShaders module) {
        MinecraftClient mc = BytePhase.mc;
        if (mc == null || mc.world == null || mc.player == null) {
            return;
        }
        if (!module.allDimensions() && !mc.world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD)) {
            return;
        }
        GlShader program = program(module.preset());
        if (program == null || !ensureNoise()) {
            return;
        }

        int width = GlTextures.mainWidth();
        int height = GlTextures.mainHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        if (!computeMatrices()) {
            return;
        }
        tick(module.speed(), module.celestialSpeed());

        int lowWidth = width;
        int lowHeight = height;
        GlShader up = null;
        boolean lowResolution = module.quality() < 0.999f;
        if (lowResolution) {
            up = upscale();
            lowResolution = up != null;
        }
        if (lowResolution) {
            lowWidth = Math.max(1, Math.round(width * module.quality()));
            lowHeight = Math.max(1, Math.round(height * module.quality()));
            if (!LOW.ensure(lowWidth, lowHeight, true)) {
                lowResolution = false;
                lowWidth = width;
                lowHeight = height;
            }
        }

        try (GlState ignored = GlState.capture()) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);

            if (lowResolution) {
                LOW.bind();
            } else if (!GlTextures.bindMain()) {
                return;
            }

            srgb(module.primary().rgb(), COLOR1);
            srgb(module.secondary().rgb(), COLOR2);
            for (int channel = 0; channel < 3; channel++) {
                HORIZON[channel] = COLOR2[channel] * 0.72f + COLOR1[channel] * 0.08f;
            }

            program.bind();
            program.set1i("uNoise", 0);
            program.set1f("uTime", (float) (animationTime % 10000.0));
            program.set3f("uColor1", linear(COLOR1[0]), linear(COLOR1[1]), linear(COLOR1[2]));
            program.set3f("uColor2", linear(COLOR2[0]), linear(COLOR2[1]), linear(COLOR2[2]));
            program.set1f("uIntensity", module.intensity());
            program.set1f("uScale", module.scale());
            program.set1f("uStars", module.stars());
            program.set1f("uBrightness", module.brightness());
            program.set3f("uHorizon", HORIZON[0], HORIZON[1], HORIZON[2]);
            program.set1f("uHorizonFog", module.horizonFog());
            program.set1f("uDirSign", directionSign());
            Matrix3f celestial = CELESTIAL.rotation((float) celestialAngle, CELESTIAL_AXIS);
            program.setMatrix3f("uCelestial", celestial);
            float focal = Math.abs(RenderProjection.projection(PROJECTION).m11());
            program.set1f("uPixel", focal > 1.0e-4f
                    ? 2.0f / (focal * lowHeight) : 0.002f);
            program.setMatrix4f("uInvViewProj", INVERSE);
            GlSamplers.bind(0, noiseTexture, GlSamplers.linearRepeat());
            FullscreenQuad.draw();
            program.unbind();

            if (lowResolution) {
                if (up != null && GlTextures.bindMain()) {
                    up.bind();
                    up.set1i("uSource", 0);
                    GlSamplers.bind(0, LOW.texture(), GlSamplers.linearClamp());
                    FullscreenQuad.draw();
                    up.unbind();
                }
            }
        } catch (Throwable error) {
            BytePhase.LOGGER.error("Clarity sky shader pass failed", error);
        }
    }

    private static GlShader program(String preset) {
        String file = presetFile(preset);
        if (file == null || FAILED.contains(file)) {
            return null;
        }
        GlShader cached = PROGRAMS.get(file);
        if (cached != null) {
            return cached;
        }
        try {
            String fragment = "#version 330 core\n" + GlShader.read("sky/common.glsl")
                    + "\n" + GlShader.read("sky/" + file + ".fsh");
            GlShader shader = GlShader.compile("sky/" + file,
                    GlShader.read("sky/sky.vsh"), fragment);
            PROGRAMS.put(file, shader);
            return shader;
        } catch (Throwable error) {
            FAILED.add(file);
            BytePhase.LOGGER.error("Could not compile supplied sky preset {}", preset, error);
            return null;
        }
    }

    private static GlShader upscale() {
        if (upscale != null || upscaleFailed) {
            return upscale;
        }
        try {
            upscale = GlShader.load("sky", "upscale");
        } catch (Throwable error) {
            upscaleFailed = true;
            BytePhase.LOGGER.error("Could not load supplied sky upscale pass", error);
        }
        return upscale;
    }

    private static String presetFile(String preset) {
        return switch (preset) {
            case "Aurora" -> "aurora";
            case "Black Hole" -> "blackhole";
            case "Blood Moon" -> "bloodmoon";
            case "Dreamscape" -> "dreamscape";
            case "Galaxy" -> "galaxy";
            case "Nebula" -> "nebula";
            case "Prism" -> "prism";
            case "Synthwave" -> "synthwave";
            case "Thunderstorm" -> "thunderstorm";
            default -> null;
        };
    }

    private static boolean computeMatrices() {
        RenderProjection.viewProjection(VIEW_PROJECTION);
        if (!VIEW_PROJECTION.isFinite() || Math.abs(VIEW_PROJECTION.determinant()) < 1.0e-12f) {
            return false;
        }
        VIEW_PROJECTION.invert(INVERSE);
        return INVERSE.isFinite();
    }

    private static float directionSign() {
        Vector4f near = INVERSE.transform(NEAR_POINT.set(0.0f, 0.0f, -1.0f, 1.0f));
        Vector4f far = INVERSE.transform(FAR_POINT.set(0.0f, 0.0f, 1.0f, 1.0f));
        float x = far.x * near.w - near.x * far.w;
        float y = far.y * near.w - near.y * far.w;
        float z = far.z * near.w - near.z * far.w;
        Matrix4f view = RenderProjection.viewRotation(VIEW);
        float dot = x * -view.m02() + y * -view.m12() + z * -view.m22();
        return dot < 0.0f ? -1.0f : 1.0f;
    }

    private static void tick(float speed, float celestialSpeed) {
        long now = System.nanoTime();
        double delta = lastNanos == 0L ? 0.0 : (now - lastNanos) / 1.0e9;
        lastNanos = now;
        delta = Math.max(0.0, Math.min(delta, 0.1));
        animationTime += delta * speed;
        celestialAngle = (celestialAngle + delta * celestialSpeed * 0.010471975511965976) % (Math.PI * 2.0);
    }

    private static float linear(float channel) {
        return (float) Math.pow(Math.max(channel, 0.0f), 2.2f);
    }

    private static void srgb(int rgb, float[] destination) {
        destination[0] = (rgb >> 16 & 0xFF) / 255.0f;
        destination[1] = (rgb >> 8 & 0xFF) / 255.0f;
        destination[2] = (rgb & 0xFF) / 255.0f;
    }

    private static boolean ensureNoise() {
        if (noiseTexture != 0) {
            return true;
        }
        byte[] r = new byte[65536];
        byte[] g = new byte[65536];
        byte[] b = new byte[65536];
        int state = -1640531527;
        for (int i = 0; i < 65536; i++) {
            int a = state ^ state << 13;
            int c = a ^ a >>> 17;
            int d = c ^ c << 5;
            r[i] = (byte) (d >>> 24);
            int e = d ^ d << 13;
            int f = e ^ e >>> 17;
            int h = f ^ f << 5;
            g[i] = (byte) (h >>> 24);
            int j = h ^ h << 13;
            int k = j ^ j >>> 17;
            state = k ^ k << 5;
            b[i] = (byte) (state >>> 24);
        }

        ByteBuffer pixels = noisePixels;
        if (pixels == null) {
            pixels = MemoryUtil.memAlloc(256 * 256 * 4);
            for (int y = 0; y < 256; y++) {
                for (int x = 0; x < 256; x++) {
                    int index = y * 256 + x;
                    int shifted = ((y - 17) & 255) * 256 + ((x - 37) & 255);
                    pixels.put(r[index]).put(r[shifted]).put(g[index]).put(b[index]);
                }
            }
            pixels.flip();
            noisePixels = pixels;
        }
        int previousActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousTexture = 0;
        boolean textureUnitCaptured = false;
        int createdTexture = 0;
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            com.mojang.blaze3d.opengl.GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            textureUnitCaptured = true;
            createdTexture = com.mojang.blaze3d.opengl.GlStateManager._genTexture();
            if (createdTexture == 0) {
                throw new IllegalStateException("OpenGL did not allocate the sky noise texture");
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, createdTexture);
            com.mojang.blaze3d.opengl.GlStateManager._bindTexture(createdTexture);
            com.mojang.blaze3d.opengl.GlStateManager._texParameter(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            com.mojang.blaze3d.opengl.GlStateManager._texParameter(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            com.mojang.blaze3d.opengl.GlStateManager._texParameter(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            com.mojang.blaze3d.opengl.GlStateManager._texParameter(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            com.mojang.blaze3d.opengl.GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0,
                    GL11.GL_RGBA8, 256, 256, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            noiseTexture = createdTexture;
            return true;
        } catch (Throwable error) {
            if (createdTexture != 0) {
                GL11.glDeleteTextures(createdTexture);
            }
            noiseTexture = 0;
            BytePhase.LOGGER.error("Could not build sky noise texture", error);
            return false;
        } finally {
            if (textureUnitCaptured) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
                com.mojang.blaze3d.opengl.GlStateManager._bindTexture(previousTexture);
            }
            GL13.glActiveTexture(previousActive);
            com.mojang.blaze3d.opengl.GlStateManager._activeTexture(previousActive);
            // The pixel buffer is intentionally not freed here; see
            // noisePixels. It is released together with the texture.
        }
    }
}
