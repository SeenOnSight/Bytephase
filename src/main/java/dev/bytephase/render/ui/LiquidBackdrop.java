package dev.bytephase.render.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.bytephase.BytePhase;
import dev.bytephase.render.shader.FullscreenQuad;
import dev.bytephase.render.shader.GlSamplers;
import dev.bytephase.render.shader.GlShader;
import dev.bytephase.render.shader.GlState;
import dev.bytephase.render.shader.GlTarget;
import dev.bytephase.render.shader.GlTextures;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.TextureSetup;
import org.lwjgl.opengl.GL11;

/** Cached 30 Hz backdrop with one scene copy and four dual-Kawase passes. */
public final class LiquidBackdrop {
    private static final GlTarget[] LEVELS = {new GlTarget(), new GlTarget(), new GlTarget()};
    private static GlShader down, up;
    private static GpuTextureView view;
    private static int viewTexture;
    private static boolean failed;
    private static TextureSetup cached = TextureSetup.empty();
    private static int cachedWidth, cachedHeight;
    private static long preparedAt;
    private LiquidBackdrop() { }

    private static final String VERTEX = """
        #version 150
        in vec3 Position;
        out vec2 uv;
        void main() { gl_Position = vec4(Position, 1.0); uv = Position.xy * 0.5 + 0.5; }
        """;
    private static final String DOWN = """
        #version 150
        uniform sampler2D scene;
        uniform vec2 stepPixel;
        in vec2 uv;
        out vec4 color;
        void main() {
            vec3 sum = texture(scene, uv).rgb * 4.0;
            sum += texture(scene, uv + stepPixel * vec2(-1,-1)).rgb;
            sum += texture(scene, uv + stepPixel * vec2(1,-1)).rgb;
            sum += texture(scene, uv + stepPixel * vec2(-1,1)).rgb;
            sum += texture(scene, uv + stepPixel * vec2(1,1)).rgb;
            color = vec4(sum / 8.0, 1.0);
        }
        """;
    private static final String UP = """
        #version 150
        uniform sampler2D scene;
        uniform vec2 stepPixel;
        in vec2 uv;
        out vec4 color;
        void main() {
            vec3 sum = texture(scene, uv + stepPixel * vec2(-2,0)).rgb;
            sum += texture(scene, uv + stepPixel * vec2(-1,1)).rgb * 2.0;
            sum += texture(scene, uv + stepPixel * vec2(0,2)).rgb;
            sum += texture(scene, uv + stepPixel * vec2(1,1)).rgb * 2.0;
            sum += texture(scene, uv + stepPixel * vec2(2,0)).rgb;
            sum += texture(scene, uv + stepPixel * vec2(1,-1)).rgb * 2.0;
            sum += texture(scene, uv + stepPixel * vec2(0,-2)).rgb;
            sum += texture(scene, uv + stepPixel * vec2(-1,-1)).rgb * 2.0;
            color = vec4(sum / 12.0, 1.0);
        }
        """;

    public static TextureSetup prepare(boolean enabled) {
        if (!enabled || failed) return TextureSetup.empty();
        int width = GlTextures.mainWidth(), height = GlTextures.mainHeight();
        if (width <= 0 || height <= 0) return TextureSetup.empty();
        long now = System.nanoTime();
        if (view != null && !view.texture().isClosed() && width == cachedWidth && height == cachedHeight
                && now - preparedAt < 33333333L) return cached;
        try (GlState ignored = GlState.capture()) {
            if (down == null) down = GlShader.compile("ui-kawase-down", VERTEX, DOWN);
            if (up == null) up = GlShader.compile("ui-kawase-up", VERTEX, UP);
            for (int i = 0; i < LEVELS.length; i++) {
                if (!LEVELS[i].ensure(Math.max(1, width >> (i + 1)), Math.max(1, height >> (i + 1)), false))
                    return TextureSetup.empty();
            }
            if (!GlTextures.blitMainTo(LEVELS[0].texture(), LEVELS[0].width(), LEVELS[0].height()))
                return TextureSetup.empty();
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            // Offset is calibrated to approximately 16 physical pixels at 1080p.
            float radius = Math.max(0.5f, height / 1080f) * 1.5f;
            for (int i = 1; i < LEVELS.length; i++) pass(down, LEVELS[i - 1], LEVELS[i], radius);
            for (int i = LEVELS.length - 2; i >= 0; i--) pass(up, LEVELS[i + 1], LEVELS[i], radius);
            if (view == null || viewTexture != LEVELS[0].texture()) {
                if (view != null) view.close();
                GlTarget target = LEVELS[0];
                var bridge = new GlTexture(GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    "bytephase-ui-backdrop", TextureFormat.RGBA8, target.width(), target.height(), 1, 1, target.texture()) {
                    @Override public void close() { } // GlTarget owns the texture.
                };
                view = RenderSystem.getDevice().createTextureView(bridge);
                viewTexture = target.texture();
            }
            cached = TextureSetup.of(view, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            cachedWidth = width;
            cachedHeight = height;
            preparedAt = now;
            return cached;
        } catch (RuntimeException | LinkageError error) {
            failed = true;
            BytePhase.LOGGER.error("Could not prepare ClickGUI glass backdrop", error);
            return TextureSetup.empty();
        }
    }

    private static void pass(GlShader program, GlTarget source, GlTarget destination, float radius) {
        destination.bind();
        program.bind();
        program.set1i("scene", 0);
        program.set2f("stepPixel", radius / source.width(), radius / source.height());
        GlSamplers.bind(0, source.texture(), GlSamplers.linearClamp());
        FullscreenQuad.draw();
    }

    public static void dispose() {
        if (view != null) { view.close(); view = null; }
        viewTexture = 0;
        cached = TextureSetup.empty();
        preparedAt = 0;
        cachedWidth = cachedHeight = 0;
        for (GlTarget target : LEVELS) target.close();
        if (down != null) { down.delete(); down = null; }
        if (up != null) { up.delete(); up = null; }
        failed = false;
    }
}
