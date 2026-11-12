package dev.bytephase.render;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * 2D drawing helpers for HUD overlays and the ClickGUI.
 *
 * <p>Vanilla only exposes axis-aligned fills, so rounded shapes are built from
 * a body plus per-row spans in the corners. That keeps the fill count low
 * (roughly {@code 2 * radius + 3} per rectangle) instead of one fill per pixel
 * row, which matters because every fill becomes a GUI render-state entry.</p>
 */
public final class Render2D {
    private Render2D() {
    }

    /** Draws a thin line as one transformed quad instead of one fill per pixel. */
    public static void drawLine(DrawContext context, double x0, double y0, double x1, double y1, int argb) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.5) {
            int x = (int) Math.round(x0);
            int y = (int) Math.round(y0);
            context.fill(x, y, x + 1, y + 1, argb);
            return;
        }

        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) ((x0 + x1) * 0.5), (float) ((y0 + y1) * 0.5));
        matrices.rotate((float) Math.atan2(dy, dx));
        int halfLength = (int) Math.ceil(dist * 0.5);
        context.fill(-halfLength, 0, halfLength, 1, argb);
        matrices.popMatrix();
    }

    /** Filled rectangle with rounded corners. */
    public static void roundedRect(DrawContext context, int x, int y, int width, int height, int radius, int argb) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            context.fill(x, y, x + width, y + height, argb);
            return;
        }

        context.fill(x + r, y + r, x + width - r, y + height - r, argb);
        context.fill(x, y + r, x + r, y + height - r, argb);
        context.fill(x + width - r, y + r, x + width, y + height - r, argb);

        for (int row = 0; row < r; row++) {
            int inset = cornerInset(r, row);
            context.fill(x + inset, y + row, x + width - inset, y + row + 1, argb);
            context.fill(x + inset, y + height - 1 - row, x + width - inset, y + height - row, argb);
        }
    }

    /** Soft drop shadow: a few progressively larger, fainter rounded rectangles. */
    public static void shadow(DrawContext context, int x, int y, int width, int height, int radius, int spread, int argb) {
        if (spread <= 0) {
            return;
        }
        int alpha = (argb >>> 24) & 0xFF;
        int rgb = argb & 0xFFFFFF;
        for (int layer = spread; layer >= 1; layer--) {
            int layerAlpha = (int) (alpha * (1.0f - (float) layer / (spread + 1)));
            if (layerAlpha <= 0) {
                continue;
            }
            roundedRect(context, x - layer, y - layer, width + layer * 2, height + layer * 2,
                    radius + layer, (layerAlpha << 24) | rgb);
        }
    }

    /** Rounded filled rect that only draws its 1px border. */
    public static void roundedOutline(DrawContext context, int x, int y, int width, int height, int radius, int argb) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            context.fill(x, y, x + width, y + 1, argb);
            context.fill(x, y + height - 1, x + width, y + height, argb);
            context.fill(x, y, x + 1, y + height, argb);
            context.fill(x + width - 1, y, x + width, y + height, argb);
            return;
        }

        context.fill(x + r, y, x + width - r, y + 1, argb);
        context.fill(x + r, y + height - 1, x + width - r, y + height, argb);
        context.fill(x, y + r, x + 1, y + height - r, argb);
        context.fill(x + width - 1, y + r, x + width, y + height - r, argb);

        for (int row = 0; row < r; row++) {
            int inset = cornerInset(r, row);
            context.fill(x + inset, y + row, x + inset + 1, y + row + 1, argb);
            context.fill(x + width - inset - 1, y + row, x + width - inset, y + row + 1, argb);
            context.fill(x + inset, y + height - 1 - row, x + inset + 1, y + height - row, argb);
            context.fill(x + width - inset - 1, y + height - 1 - row, x + width - inset, y + height - row, argb);
        }
    }

    private static int cornerInset(int radius, int row) {
        double dy = radius - row - 0.5;
        double dx = Math.sqrt(Math.max(0.0, (double) radius * radius - dy * dy));
        return (int) Math.round(radius - dx);
    }

    /** Draws text at a fractional scale, for compact subtitles and secondary labels. */
    public static void scaledText(DrawContext context, TextRenderer textRenderer,
                                  String text, int x, int y, float scale, int argb) {
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) x, (float) y);
        matrices.scale(scale, scale);
        context.drawText(textRenderer, text, 0, 0, argb, false);
        matrices.popMatrix();
    }

    public static int lerpColor(int from, int to, float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        int a = lerpChannel(from >>> 24, to >>> 24, clamped);
        int r = lerpChannel((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, clamped);
        int g = lerpChannel((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, clamped);
        int b = lerpChannel(from & 0xFF, to & 0xFF, clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }
}
