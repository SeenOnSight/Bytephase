package dev.bytephase.render.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.bytephase.BytePhase;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Vector2f;

/** Deferred GUI quads with distance and antialiasing measured in framebuffer pixels. */
public final class LiquidGui {
    public static final int GRADIENT = 1, GLASS = 2, GLOW = 4, HIGHLIGHT = 8, PICTURE = 16, SHADOW = 64;
    private static final RenderPipeline SURFACE = pipeline(false);
    private static final RenderPipeline BACKDROP = pipeline(true);
    private static float opacity = 1;
    private static final Vector2f SNAPPED_TEXT = new Vector2f();

    private LiquidGui() { }

    private static RenderPipeline pipeline(boolean glass) {
        var builder = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.of("bytephase", "pipeline/liquid_" + (glass ? "glass" : "surface")))
            .withVertexShader(Identifier.of("bytephase", "core/liquid_gui"))
            .withFragmentShader(Identifier.of("bytephase", "core/liquid_gui"))
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS);
        if (glass) builder.withSampler("Sampler0").withShaderDefine("UI_GLASS");
        return RenderPipelines.register(builder.build());
    }

    public static void opacity(float value) { opacity = Math.clamp(value, 0, 1); }
    public static float pixelScale() { return (float) BytePhase.mc.getWindow().getScaleFactor(); }

    public record Rect(float x, float y, float w, float h) {
        public boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    /** The same snapped rectangle is used by render states and ClickGUI hit regions. */
    public static Rect rect(DrawContext ctx, float x, float y, float w, float h) {
        float pixels = pixelScale();
        var pose = ctx.getMatrices();
        float ax = pose.m00() * x + pose.m10() * y + pose.m20();
        float ay = pose.m01() * x + pose.m11() * y + pose.m21();
        float bx = pose.m00() * (x + w) + pose.m10() * (y + h) + pose.m20();
        float by = pose.m01() * (x + w) + pose.m11() * (y + h) + pose.m21();
        float left = Math.round(ax * pixels) / pixels, top = Math.round(ay * pixels) / pixels;
        float right = Math.round(bx * pixels) / pixels, bottom = Math.round(by * pixels) / pixels;
        return new Rect(left, top, right - left, bottom - top);
    }

    /** Snap a text origin; the render-thread scratch result must be consumed immediately. */
    public static Vector2f textOrigin(DrawContext ctx, float x, float y) {
        float pixels = pixelScale();
        var pose = ctx.getMatrices();
        float px = pose.m00() * x + pose.m10() * y + pose.m20();
        float py = pose.m01() * x + pose.m11() * y + pose.m21();
        px = Math.round(px * pixels) / pixels - pose.m20();
        py = Math.round(py * pixels) / pixels - pose.m21();
        float inverse = 1f / (pose.m00() * pose.m11() - pose.m01() * pose.m10());
        return SNAPPED_TEXT.set((pose.m11() * px - pose.m10() * py) * inverse,
            (-pose.m01() * px + pose.m00() * py) * inverse);
    }

    public static void surface(DrawContext ctx, float x, float y, float w, float h,
                               float radius, int fill, int border, int flags) {
        submit(ctx, x, y, w, h, radius, fill, border, flags, 1, TextureSetup.empty());
    }

    public static void glass(DrawContext ctx, float x, float y, float w, float h, float radius,
                             int tint, int border, TextureSetup backdrop) {
        submit(ctx, x, y, w, h, radius, tint, border, HIGHLIGHT | GLASS, 1, backdrop);
    }

    public static void glow(DrawContext ctx, float x, float y, float w, float h,
                            float radius, float spread, int color) {
        submit(ctx, x, y, w, h, radius, color, 0, GLOW, spread, TextureSetup.empty());
    }

    /** An offset shadow keeps its core filled; the panel covers that core. */
    public static void shadow(DrawContext ctx, float x, float y, float w, float h,
                              float radius, float spread, int color) {
        submit(ctx, x, y, w, h, radius, color, 0, GLOW | SHADOW, spread, TextureSetup.empty());
    }

    public static void picture(DrawContext ctx, float x, float y, float w, float h, float radius,
                                com.mojang.blaze3d.textures.GpuTextureView view) {
        var sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
            .get(com.mojang.blaze3d.textures.FilterMode.LINEAR);
        submit(ctx, x, y, w, h, radius, 0xFFFFFFFF, 0, PICTURE, 1, TextureSetup.of(view, sampler));
    }

    /** Local images use top-down UVs; previews retain their framebuffer orientation. */
    public static void image(DrawContext ctx, float x, float y, float w, float h, float radius,
                             com.mojang.blaze3d.textures.GpuTextureView view, int opacity, String fit) {
        var sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
            .get(com.mojang.blaze3d.textures.FilterMode.LINEAR);
        int flags = PICTURE | 32;
        if ("Cover".equals(fit)) flags |= GRADIENT;
        else if ("Contain".equals(fit)) flags |= HIGHLIGHT;
        int tint = Math.round(Math.clamp(opacity, 0, 100) * 2.55f) << 24 | 0xFFFFFF;
        submit(ctx, x, y, w, h, radius, tint, 0, flags, 1, TextureSetup.of(view, sampler));
    }

    private static int alpha(int color) { return Math.round((color >>> 24) * opacity) << 24 | color & 0xFFFFFF; }

    private static void submit(DrawContext ctx, float x, float y, float w, float h,
                               float radius, int fill, int border, int flags, float spread, TextureSetup texture) {
        if (w <= 0 || h <= 0) return;
        Rect rect = rect(ctx, x, y, w, h);
        float pixels = pixelScale();
        int pw = Math.round(rect.w * pixels), ph = Math.round(rect.h * pixels);
        if (pw <= 0 || ph <= 0) return;
        float transformScale = Math.abs(ctx.getMatrices().m00()) * pixels;
        int pr = Math.min(127, Math.round(radius * transformScale));
        int amount = (flags & GLOW) != 0 ? Math.min(127, Math.max(1, Math.round(spread * transformScale))) : 1;
        int margin = (flags & GLOW) != 0 ? amount * 3 : 1;
        ScreenRect scissor = ctx.scissorStack.peekLast();
        ScreenRect bounds = new ScreenRect((int) Math.floor(rect.x - margin / pixels),
            (int) Math.floor(rect.y - margin / pixels),
            (int) Math.ceil(rect.w + 2 * margin / pixels) + 1,
            (int) Math.ceil(rect.h + 2 * margin / pixels) + 1);
        if (scissor != null) {
            bounds = bounds.intersection(scissor);
            if (bounds == null) return;
        }
        boolean glass = (flags & (GLASS | PICTURE)) != 0 && texture.texure0() != null;
        if (!glass) flags &= ~(GLASS | PICTURE);
        ctx.state.addSimpleElement(new Quad(glass ? BACKDROP : SURFACE, glass ? texture : TextureSetup.empty(),
            rect, pw, ph, pr, amount, margin, alpha(fill), alpha(border), flags, pixels, scissor, bounds));
    }

    private record Quad(RenderPipeline pipeline, TextureSetup textureSetup, Rect rect,
                        int width, int height, int radius, int amount, int margin,
                        int color, int border, int flags, float pixels,
                        ScreenRect scissorArea, ScreenRect bounds) implements SimpleGuiElementRenderState {
        @Override public void setupVertices(VertexConsumer out) {
            float m = margin / pixels;
            vertex(out, rect.x - m, rect.y - m, -margin, -margin);
            vertex(out, rect.x - m, rect.y + rect.h + m, -margin, height + margin);
            vertex(out, rect.x + rect.w + m, rect.y + rect.h + m, width + margin, height + margin);
            vertex(out, rect.x + rect.w + m, rect.y - m, width + margin, -margin);
        }
        private void vertex(VertexConsumer out, float x, float y, float u, float v) {
            out.vertex(x, y, 0).color(color).texture(u, v)
                .overlay(border & 0xFFFF, border >>> 16).light(width, height)
                .normal((radius + 0.25f) / 127f, (amount + 0.25f) / 127f, (flags + 0.25f) / 127f);
        }
    }
}
