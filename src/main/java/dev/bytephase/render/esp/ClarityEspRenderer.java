package dev.bytephase.render.esp;

import dev.bytephase.BytePhase;
import dev.bytephase.render.RenderProjection;
import dev.bytephase.render.shader.FullscreenQuad;
import dev.bytephase.render.shader.GlSamplers;
import dev.bytephase.render.shader.GlShader;
import dev.bytephase.render.shader.GlState;
import dev.bytephase.render.shader.GlTarget;
import dev.bytephase.render.shader.GlTextures;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

/**
 * Applies the supplied Clarity ESP shaders to box masks.
 *
 * <p>Clarity extracts an entity outline mask from vanilla's entity outline
 * pass. BytePhase already has its own batched box renderer, so this adapter
 * rasterises those boxes into the same mask/downsample/blur/composite shape
 * the supplied shaders expect. The GLSL files are unchanged.</p>
 */
public final class ClarityEspRenderer {
    public static final String STYLE_GLOW = "Shader Glow";
    public static final String STYLE_BLUR = "Shader Blur";
    public static final String STYLE_GALAXY = "Shader Galaxy";
    public static final String STYLE_PRISM = "Shader Prism";
    public static final String STYLE_NEON = "Shader Neon";
    public static final String STYLE_SCAN = "Shader Scan";

    private static final float SKIP_R = 0.99607843f;
    private static final int MAX_POOLED_MASK_ENTRIES = 4096;
    private static final long HIGH_RESOLUTION_PIXEL_THRESHOLD = 2560L * 1440L;

    // Keep through-wall masks and full-resolution depth-tested masks separate.
    // Reusing one target made mixed shader batches resize/reallocate its
    // textures repeatedly in the same frame.
    private static final GlTarget throughWallMask = new GlTarget();
    private static final GlTarget depthTestedMask = new GlTarget();
    private static final GlTarget g2a = new GlTarget();
    private static final GlTarget g2b = new GlTarget();
    private static final GlTarget g4a = new GlTarget();
    private static final GlTarget g4b = new GlTarget();
    private static final GlTarget s2a = new GlTarget();
    private static final GlTarget s2b = new GlTarget();
    private static final GlTarget s4a = new GlTarget();
    private static final GlTarget s4b = new GlTarget();

    // Shader compilation is expensive and must stay off the ordinary box ESP
    // path. Initialize this set only when an actual shader style is queued.
    private static GlShader downShader;
    private static GlShader blurShader;
    private static GlShader glowShader;
    private static GlShader blurModeShader;
    private static GlShader galaxyShader;
    private static GlShader prismShader;
    private static GlShader neonShader;
    private static GlShader scanShader;
    private static GlShader maskShader;
    private static boolean baseShadersInitialized;
    private static final Set<String> attemptedStyles = new HashSet<>();

    private static final Map<BatchKey, List<MaskEntry>> QUEUED = new LinkedHashMap<>();
    private static final List<MaskEntry> POOL = new ArrayList<>();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final Vector4f[] CLIP_CORNERS = {
            new Vector4f(), new Vector4f(), new Vector4f(), new Vector4f(),
            new Vector4f(), new Vector4f(), new Vector4f(), new Vector4f()
    };
    private static final Vector4f CLIP_INTERSECTION = new Vector4f();
    private static final int[][] BOX_EDGES = {
            {0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3},
            {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}
    };
    private static final int[] EFFECT_SCISSOR = new int[4];
    private static int effectMainWidth;
    private static int effectMainHeight;
    private static int maskVao;
    private static int maskVbo;
    private static ByteBuffer maskData;
    // Growing the mask buffer must not free the previous allocation: the macOS
    // driver can still be reading it from the last upload, and a read of
    // unmapped memory is a native SIGBUS inside libGLImage.
    private static final List<ByteBuffer> RETIRED_MASKS = new ArrayList<>();
    private static double animTime;
    private static long lastNanos;

    private ClarityEspRenderer() {
    }

    public static void queue(Collection<Box> boxes, int argb, String style, boolean throughWalls) {
        String resolved = styleName(style);
        if (resolved == null || boxes.isEmpty()) {
            return;
        }
        BatchKey key = new BatchKey(resolved, throughWalls);
        List<MaskEntry> entries = QUEUED.computeIfAbsent(key, ignored -> new ArrayList<>());
        int color = argb & 0xFFFFFF;
        for (Box box : boxes) {
            MaskEntry entry = POOL.isEmpty() ? new MaskEntry() : POOL.remove(POOL.size() - 1);
            entry.box = box;
            entry.color = color;
            entries.add(entry);
        }
    }

    public static boolean hasQueued() {
        return !QUEUED.isEmpty();
    }

    public static void clearQueued() {
        clear();
    }

    public static void dispose() {
        clear();
        POOL.clear();
        close(throughWallMask, depthTestedMask, g2a, g2b, g4a, g4b, s2a, s2b, s4a, s4b);
        delete(downShader, blurShader, glowShader, blurModeShader, galaxyShader,
                prismShader, neonShader, scanShader, maskShader);
        downShader = null;
        blurShader = null;
        glowShader = null;
        blurModeShader = null;
        galaxyShader = null;
        prismShader = null;
        neonShader = null;
        scanShader = null;
        maskShader = null;
        if (maskVao != 0) {
            GL30.glDeleteVertexArrays(maskVao);
            maskVao = 0;
        }
        if (maskVbo != 0) {
            GL15.glDeleteBuffers(maskVbo);
            maskVbo = 0;
        }
        if (maskData != null) {
            MemoryUtil.memFree(maskData);
            maskData = null;
        }
        for (ByteBuffer retired : RETIRED_MASKS) {
            MemoryUtil.memFree(retired);
        }
        RETIRED_MASKS.clear();
        attemptedStyles.clear();
        baseShadersInitialized = false;
    }

    private static void close(GlTarget... targets) {
        for (GlTarget target : targets) {
            target.close();
        }
    }

    private static void delete(GlShader... shaders) {
        for (GlShader shader : shaders) {
            if (shader != null) {
                shader.delete();
            }
        }
    }

    /**
     * Draws all queued shader batches. Called once after the normal batched box
     * layers have been submitted for the frame.
     */
    public static void flush() {
        if (QUEUED.isEmpty()) {
            return;
        }
        if (!ensureShaders()) {
            clear();
            return;
        }
        int width = GlTextures.mainWidth();
        int height = GlTextures.mainHeight();
        if (width <= 0 || height <= 0) {
            clear();
            return;
        }

        try (GlState ignored = GlState.capture()) {
            tick();
            // Scissor bounds must use this frame's matrices, not those left by
            // the previous mask pass (or the identity matrix on the first frame).
            RenderProjection.viewProjection(VIEW_PROJECTION);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColorMask(true, true, true, true);
            for (Map.Entry<BatchKey, List<MaskEntry>> batch : QUEUED.entrySet()) {
                if (batch.getValue().isEmpty()) {
                    continue;
                }
                boolean throughWalls = batch.getKey().throughWalls();
                // Depth testing needs the main world's full-resolution depth
                // attachment. Through-wall masks use half resolution on normal
                // windows and quarter resolution on high-resolution windows;
                // the soft glow does not need full pixel detail, and these
                // targets dominate the mask and blur bandwidth for large
                // crowds. The final composite still runs at display resolution.
                boolean highResolution = (long) width * height >= HIGH_RESOLUTION_PIXEL_THRESHOLD;
                int maskDivisor = throughWalls && highResolution ? 4 : throughWalls ? 2 : 1;
                int maskWidth = Math.max(1, width / maskDivisor);
                int maskHeight = Math.max(1, height / maskDivisor);
                GlTarget maskTarget = throughWalls ? throughWallMask : depthTestedMask;
                if (!maskTarget.ensure(maskWidth, maskHeight, false)) {
                    continue;
                }
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                if (!computeEffectScissor(batch.getValue(), width, height)) {
                    continue;
                }
                if (renderMask(batch.getValue(), maskTarget, maskWidth, maskHeight, throughWalls)) {
                    drawComposite(batch.getKey().style(), width, height, maskTarget);
                }
            }
        } catch (Throwable error) {
            BytePhase.LOGGER.error("Clarity ESP shader pass failed", error);
        } finally {
            clear();
        }
    }

    private static boolean renderMask(List<MaskEntry> entries, GlTarget maskTarget,
                                      int width, int height, boolean throughWalls) {
        if (maskVao == 0) {
            createMaskGeometry();
        }
        if (maskVao == 0) {
            return false;
        }
        RenderProjection.viewProjection(VIEW_PROJECTION);
        Vec3d camera = RenderProjection.cameraPos();

        int vertexCount = entries.size() * 18;
        int requiredBytes = vertexCount * 7 * Float.BYTES;
        if (maskData == null || maskData.capacity() < requiredBytes) {
            if (maskData != null) {
                RETIRED_MASKS.add(maskData);
            }
            int capacity = Math.max(requiredBytes, maskData == null ? 8192 : maskData.capacity() * 2);
            maskData = MemoryUtil.memAlloc(capacity);
        }
        ByteBuffer data = maskData;
        data.clear();
        {
            for (MaskEntry entry : entries) {
                Box box = entry.box;
                float r = ((entry.color >> 16) & 0xFF) / 255.0f;
                float g = ((entry.color >> 8) & 0xFF) / 255.0f;
                float b = (entry.color & 0xFF) / 255.0f;
                float x1 = (float) (box.minX - camera.x);
                float y1 = (float) (box.minY - camera.y);
                float z1 = (float) (box.minZ - camera.z);
                float x2 = (float) (box.maxX - camera.x);
                float y2 = (float) (box.maxY - camera.y);
                float z2 = (float) (box.maxZ - camera.z);
                // Draw only the three camera-facing faces. For through-wall
                // masks this avoids hidden-face overdraw; for depth-tested
                // masks it still covers the visible surface of the box.
                double centerX = (box.minX + box.maxX) * 0.5;
                double centerY = (box.minY + box.maxY) * 0.5;
                double centerZ = (box.minZ + box.maxZ) * 0.5;
                if (camera.z <= centerZ) {
                    quad(data, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b);
                } else {
                    quad(data, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2, r, g, b);
                }
                if (camera.x >= centerX) {
                    quad(data, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b);
                } else {
                    quad(data, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, r, g, b);
                }
                if (camera.y >= centerY) {
                    quad(data, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b);
                } else {
                    quad(data, x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2, r, g, b);
                }
            }
            data.flip();

            if (!throughWalls && !maskTarget.attachDepthTexture(GlTextures.mainDepthId())) {
                return false;
            }
            maskTarget.bind();
            if (throughWalls) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            } else {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                // The scene depth buffer already contains the visible entity
                // surface. Equal-depth fragments must remain in the mask.
                GL11.glDepthFunc(GL11.GL_LEQUAL);
                GL11.glDepthMask(false);
            }
            // The ESP mask is only needed beneath and around the projected
            // targets. Scissor both the clear and mask rasterization so a
            // sparse ESP does not clear/fill the entire mask target
            // every frame.
            applyEffectScissor(maskTarget, effectMainWidth, effectMainHeight);
            maskTarget.clear();
            maskShader.bind();
            maskShader.setMatrix4f("uViewProj", VIEW_PROJECTION);
            GlStateManagerBridge.bindArrayBuffer(maskVbo);
            GlStateManagerBridge.bufferData(data);
            maskShader.set1i("Mask", 0);
            GlStateManagerBridge.bindVertexArray(maskVao);
            GlStateManagerBridge.drawArrays(vertexCount);
            GlStateManagerBridge.bindVertexArray(0);
            maskShader.unbind();
            return true;
        }
    }

    private static boolean ensureShaders() {
        if (!baseShadersInitialized) {
            baseShadersInitialized = true;
            downShader = load("esp", "esp_down", "ESP downsample");
            blurShader = load("hand", "fx_blur", "ESP blur");
            maskShader = loadMaskShader();
        }
        if (maskShader == null || downShader == null || blurShader == null) {
            return false;
        }
        for (BatchKey batch : QUEUED.keySet()) {
            String style = batch.style();
            if (attemptedStyles.add(style)) {
                switch (style) {
                    case STYLE_BLUR -> blurModeShader = load("esp", "esp_blur", "ESP blur composite");
                    case STYLE_GALAXY -> galaxyShader = load("esp", "esp_galaxy", "ESP galaxy");
                    case STYLE_PRISM -> prismShader = load("esp", "esp_prism", "ESP prism");
                    case STYLE_NEON -> neonShader = load("esp", "esp_neon", "ESP neon");
                    case STYLE_SCAN -> scanShader = load("esp", "esp_scan", "ESP scan");
                    default -> glowShader = load("esp", "esp_glow", "ESP glow");
                }
            }
            if (shaderForStyle(style) == null) {
                return false;
            }
        }
        return true;
    }

    private static GlShader shaderForStyle(String style) {
        return switch (style) {
            case STYLE_BLUR -> blurModeShader;
            case STYLE_GALAXY -> galaxyShader;
            case STYLE_PRISM -> prismShader;
            case STYLE_NEON -> neonShader;
            case STYLE_SCAN -> scanShader;
            default -> glowShader;
        };
    }

    private static void drawComposite(String style, int width, int height, GlTarget maskTarget) {
        float pxScale = height / 1080.0f;
        float size = 2.0f;
        boolean blurStyle = STYLE_BLUR.equals(style);
        float glowSize = blurStyle ? Math.min(size, 8.0f) : size;
        float sigma = Math.max(0.5f, glowSize * pxScale / 1.6f);
        int divisor = sigma <= 6.0f ? 2 : 4;
        // Keep the blur target at the mask's resolution. In particular, do not
        // upsample a high-resolution through-wall mask back to half size just
        // to blur it; that would throw away the bandwidth saved above.
        if (maskTarget.width() <= width / 4 && maskTarget.height() <= height / 4) {
            divisor = 4;
        } else if (maskTarget.width() <= width / 2 && maskTarget.height() <= height / 2) {
            divisor = Math.max(divisor, 2);
        }
        GlTarget glowA = divisor == 2 ? g2a : g4a;
        GlTarget glowB = divisor == 2 ? g2b : g4b;
        if (!glowA.ensure(width / divisor, height / divisor, true)
                || !glowB.ensure(width / divisor, height / divisor, true)) {
            return;
        }

        // Downsample the mask four times.
        glowA.bind();
        applyEffectScissor(glowA, width, height);
        downShader.bind();
        GlSamplers.bind(0, maskTarget.texture(), GlSamplers.linearClamp());
        downShader.set1i("Mask", 0);
        downShader.set3f("uSkip", SKIP_R, 0.0f, SKIP_R);
        downShader.set2f("srcTexel", 1.0f / maskTarget.width(), 1.0f / maskTarget.height());
        downShader.set1f("spread", divisor == 2 ? 0.5f : 1.0f);
        FullscreenQuad.draw();
        downShader.unbind();

        blur(glowA, glowB, sigma / divisor);

        GlTarget scene = null;
        if (blurStyle) {
            float sceneSize = 2.0f * pxScale;
            int sceneDivisor = sceneSize <= 8.0f ? 2 : 4;
            scene = sceneDivisor == 2 ? s2a : s4a;
            GlTarget sceneB = sceneDivisor == 2 ? s2b : s4b;
            if (!scene.ensure(width / sceneDivisor, height / sceneDivisor, true)
                    || !sceneB.ensure(width / sceneDivisor, height / sceneDivisor, true)) {
                return;
            }
            applyEffectScissor(scene, width, height);
            GlTextures.blitMainTo(scene.texture(), scene.width(), scene.height());
            float sceneBlur = sceneSize / sceneDivisor;
            if (sceneBlur > 0.3f) {
                blur(scene, sceneB, sceneBlur);
            }
        }

        if (!GlTextures.bindMain()) {
            return;
        }
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(EFFECT_SCISSOR[0], EFFECT_SCISSOR[1], EFFECT_SCISSOR[2], EFFECT_SCISSOR[3]);
        GL11.glDisable(GL11.GL_CULL_FACE);

        GlShader shader = switch (style) {
            case STYLE_BLUR -> blurModeShader;
            case STYLE_GALAXY -> galaxyShader;
            case STYLE_PRISM -> prismShader;
            case STYLE_NEON -> neonShader;
            case STYLE_SCAN -> scanShader;
            default -> glowShader;
        };

        GlSamplers.bind(0, maskTarget.texture(), GlSamplers.linearClamp());
        GlSamplers.bind(2, glowA.texture(), GlSamplers.linearClamp());
        if (scene != null) {
            GlSamplers.bind(1, scene.texture(), GlSamplers.linearClamp());
        }
        shader.bind();
        shader.set1i("Mask", 0);
        shader.set1i("Scene", 1);
        shader.set1i("Glow", 2);
        shader.set2f("texel", 1.0f / width, 1.0f / height);
        shader.set1f("pxScale", pxScale);
        shader.set1f("time", (float) (animTime % 3600.0));
        shader.set1i("chroma", 0);
        shader.set1f("outline", 1.0f);
        shader.set1f("intensity", 1.0f);
        shader.set1f("fill", 0.3f);
        shader.set3f("uSkip", SKIP_R, 0.0f, SKIP_R);
        shader.set1f("tintAmount", 0.0f);
        shader.set1f("aspect", (float) width / Math.max(1, height));
        FullscreenQuad.draw();
        shader.unbind();
    }

    private static void blur(GlTarget source, GlTarget destination, float sigma) {
        sigma = Math.min(sigma, 16.0f);
        blurShader.bind();
        blurShader.set1i("Source", 2);
        blurShader.set1f("sigma", sigma);

        destination.bind();
        applyEffectScissor(destination, effectMainWidth, effectMainHeight);
        GlSamplers.bind(2, source.texture(), GlSamplers.linearClamp());
        blurShader.set2f("direction", 1.0f / source.width(), 0.0f);
        FullscreenQuad.draw();

        source.bind();
        applyEffectScissor(source, effectMainWidth, effectMainHeight);
        GlSamplers.bind(2, destination.texture(), GlSamplers.linearClamp());
        blurShader.set2f("direction", 0.0f, 1.0f / destination.height());
        FullscreenQuad.draw();
        blurShader.unbind();
    }

    /** Computes a padded screen-space bound for all boxes in this shader batch. */
    private static boolean computeEffectScissor(List<MaskEntry> entries, int width, int height) {
        effectMainWidth = width;
        effectMainHeight = height;
        Vec3d camera = RenderProjection.cameraPos();
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        boolean front = false;
        for (MaskEntry entry : entries) {
            Box box = entry.box;
            float boxMinX = Float.POSITIVE_INFINITY;
            float boxMinY = Float.POSITIVE_INFINITY;
            float boxMaxX = Float.NEGATIVE_INFINITY;
            float boxMaxY = Float.NEGATIVE_INFINITY;
            int boxPoints = 0;
            for (int ix = 0; ix < 2; ix++) {
                float x = (float) ((ix == 0 ? box.minX : box.maxX) - camera.x);
                for (int iy = 0; iy < 2; iy++) {
                    float y = (float) ((iy == 0 ? box.minY : box.maxY) - camera.y);
                    for (int iz = 0; iz < 2; iz++) {
                        float z = (float) ((iz == 0 ? box.minZ : box.maxZ) - camera.z);
                        int index = (ix << 2) | (iy << 1) | iz;
                        VIEW_PROJECTION.transform(CLIP_CORNERS[index].set(x, y, z, 1.0f));
                    }
                }
            }
            // Clip the box edges in homogeneous space before dividing by w.
            // Merely ignoring behind-camera corners underestimates the bounds
            // when a box crosses the near plane or enters through a viewport
            // edge, cutting off the blur/glow mask at those boundaries.
            for (Vector4f corner : CLIP_CORNERS) {
                if (insideClipVolume(corner)) {
                    boxPoints++;
                    boxMinX = Math.min(boxMinX, screenX(corner, width));
                    boxMinY = Math.min(boxMinY, screenY(corner, height));
                    boxMaxX = Math.max(boxMaxX, screenX(corner, width));
                    boxMaxY = Math.max(boxMaxY, screenY(corner, height));
                }
            }
            for (int[] edge : BOX_EDGES) {
                Vector4f a = CLIP_CORNERS[edge[0]];
                Vector4f b = CLIP_CORNERS[edge[1]];
                for (int plane = 0; plane < 6; plane++) {
                    float da = planeDistance(a, plane);
                    float db = planeDistance(b, plane);
                    if ((da < 0.0f && db > 0.0f) || (da > 0.0f && db < 0.0f)) {
                        float t = da / (da - db);
                        CLIP_INTERSECTION.set(a.x + (b.x - a.x) * t,
                                a.y + (b.y - a.y) * t,
                                a.z + (b.z - a.z) * t,
                                a.w + (b.w - a.w) * t);
                        if (insideClipVolume(CLIP_INTERSECTION)) {
                            boxPoints++;
                            boxMinX = Math.min(boxMinX, screenX(CLIP_INTERSECTION, width));
                            boxMinY = Math.min(boxMinY, screenY(CLIP_INTERSECTION, height));
                            boxMaxX = Math.max(boxMaxX, screenX(CLIP_INTERSECTION, width));
                            boxMaxY = Math.max(boxMaxY, screenY(CLIP_INTERSECTION, height));
                        }
                    }
                }
            }
            if (boxPoints > 0) {
                front = true;
                minX = Math.min(minX, boxMinX);
                minY = Math.min(minY, boxMinY);
                maxX = Math.max(maxX, boxMaxX);
                maxY = Math.max(maxY, boxMaxY);
            } else if (RenderProjection.isVisible(box)) {
                // Handles a large box enclosing the view volume, which has
                // no clipped corners or edge intersections of its own.
                minX = 0.0f;
                minY = 0.0f;
                maxX = width;
                maxY = height;
                front = true;
            }
        }
        if (!front) return false;

        // 64 screen pixels covers the supplied glow and blur kernels, including
        // the largest supported framebuffer scale, so scissoring cannot clip
        // the effect around an entity.
        int padding = 64;
        int x0 = Math.max(0, (int) Math.floor(minX) - padding);
        int y0 = Math.max(0, (int) Math.floor(minY) - padding);
        int x1 = Math.min(width, (int) Math.ceil(maxX) + padding);
        int y1 = Math.min(height, (int) Math.ceil(maxY) + padding);
        if (x1 <= x0 || y1 <= y0) return false;
        EFFECT_SCISSOR[0] = x0;
        EFFECT_SCISSOR[1] = y0;
        EFFECT_SCISSOR[2] = x1 - x0;
        EFFECT_SCISSOR[3] = y1 - y0;
        return true;
    }

    private static float screenX(Vector4f point, int width) {
        return Math.max(0.0f, Math.min(width,
                (point.x / point.w * 0.5f + 0.5f) * width));
    }

    private static float screenY(Vector4f point, int height) {
        return Math.max(0.0f, Math.min(height,
                (point.y / point.w * 0.5f + 0.5f) * height));
    }

    private static float planeDistance(Vector4f point, int plane) {
        return switch (plane) {
            case 0 -> point.w + point.x;
            case 1 -> point.w - point.x;
            case 2 -> point.w + point.y;
            case 3 -> point.w - point.y;
            case 4 -> point.w + point.z;
            default -> point.w - point.z;
        };
    }

    private static boolean insideClipVolume(Vector4f point) {
        for (int plane = 0; plane < 6; plane++) {
            if (planeDistance(point, plane) < 0.0f) return false;
        }
        return point.w > 0.0f;
    }

    private static void applyEffectScissor(GlTarget target, int mainWidth, int mainHeight) {
        int x0 = EFFECT_SCISSOR[0] * target.width() / Math.max(1, mainWidth);
        int y0 = EFFECT_SCISSOR[1] * target.height() / Math.max(1, mainHeight);
        int x1 = (EFFECT_SCISSOR[0] + EFFECT_SCISSOR[2]) * target.width() / Math.max(1, mainWidth);
        int y1 = (EFFECT_SCISSOR[1] + EFFECT_SCISSOR[3]) * target.height() / Math.max(1, mainHeight);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
    }

    private static void tick() {
        long now = System.nanoTime();
        double delta = lastNanos == 0L ? 0.0 : Math.min(0.1, (now - lastNanos) / 1.0e9);
        lastNanos = now;
        animTime += delta;
    }

    private static void createMaskGeometry() {
        maskVao = GL30.glGenVertexArrays();
        maskVbo = GL15.glGenBuffers();
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL30.glBindVertexArray(maskVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, maskVbo);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 7 * Float.BYTES, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, 7 * Float.BYTES, 3L * Float.BYTES);
        GL30.glBindVertexArray(previousVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
    }

    private static void quad(ByteBuffer data, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float x4, float y4, float z4, float r, float g, float b) {
        vertex(data, x1, y1, z1, r, g, b);
        vertex(data, x2, y2, z2, r, g, b);
        vertex(data, x3, y3, z3, r, g, b);
        vertex(data, x1, y1, z1, r, g, b);
        vertex(data, x3, y3, z3, r, g, b);
        vertex(data, x4, y4, z4, r, g, b);
    }

    private static void vertex(ByteBuffer data, float x, float y, float z,
                               float r, float g, float b) {
        data.putFloat(x).putFloat(y).putFloat(z).putFloat(r).putFloat(g).putFloat(b).putFloat(1.0f);
    }

    private static String styleName(String style) {
        if (style == null) {
            return null;
        }
        return switch (style) {
            case "Shader", STYLE_GLOW -> STYLE_GLOW;
            case STYLE_BLUR -> STYLE_BLUR;
            case STYLE_GALAXY -> STYLE_GALAXY;
            case STYLE_PRISM -> STYLE_PRISM;
            case STYLE_NEON -> STYLE_NEON;
            case STYLE_SCAN -> STYLE_SCAN;
            default -> null;
        };
    }

    private static GlShader load(String directory, String name, String label) {
        try {
            return GlShader.load(directory, name);
        } catch (Throwable error) {
            BytePhase.LOGGER.error("Could not load supplied {} shader", label, error);
            return null;
        }
    }

    private static GlShader loadMaskShader() {
        String vertex = """
                #version 330 core
                layout(location = 0) in vec3 Position;
                layout(location = 1) in vec4 Color;
                uniform mat4 uViewProj;
                out vec4 vColor;
                void main() {
                    gl_Position = uViewProj * vec4(Position, 1.0);
                    vColor = Color;
                }
                """;
        String fragment = """
                #version 330 core
                in vec4 vColor;
                out vec4 outColor;
                void main() {
                    outColor = vColor;
                }
                """;
        try {
            return GlShader.compile("bytephase-esp-mask", vertex, fragment);
        } catch (Throwable error) {
            BytePhase.LOGGER.error("Could not compile ESP mask adapter", error);
            return null;
        }
    }

    private static void clear() {
        for (List<MaskEntry> entries : QUEUED.values()) {
            for (MaskEntry entry : entries) {
                if (POOL.size() < MAX_POOLED_MASK_ENTRIES) {
                    POOL.add(entry);
                }
            }
            entries.clear();
        }
        QUEUED.clear();
    }

    private static final class MaskEntry {
        Box box;
        int color;
    }

    private record BatchKey(String style, boolean throughWalls) { }

    /** Keeps the raw GlStateManager calls out of the shader flow above. */
    private static final class GlStateManagerBridge {
        private static void bindArrayBuffer(int buffer) {
            com.mojang.blaze3d.opengl.GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        }

        private static void bufferData(ByteBuffer data) {
            com.mojang.blaze3d.opengl.GlStateManager._glBufferData(
                    GL15.GL_ARRAY_BUFFER, data, GL15.GL_DYNAMIC_DRAW);
        }

        private static void bindVertexArray(int array) {
            com.mojang.blaze3d.opengl.GlStateManager._glBindVertexArray(array);
        }

        private static void drawArrays(int count) {
            com.mojang.blaze3d.opengl.GlStateManager._drawArrays(GL11.GL_TRIANGLES, 0, count);
        }
    }
}
