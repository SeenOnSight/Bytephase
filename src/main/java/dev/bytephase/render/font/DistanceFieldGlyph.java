package dev.bytephase.render.font;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Supplier;
import net.minecraft.client.font.BakedGlyph;
import net.minecraft.client.font.Glyph;
import net.minecraft.client.font.GlyphMetrics;
import net.minecraft.client.font.UploadableGlyph;
import net.minecraft.client.font.FreeTypeUtil;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FreeType;

/** A padded signed distance field with the original font's advance and baseline. */
public final class DistanceFieldGlyph implements Glyph {
    private static final int PADDING = 8;
    private static final double FAR = 1.0e10;
    private final GlyphMetrics metrics;
    private final Supplier<FT_Face> face;
    private final int glyphIndex;
    private final float oversample;
    private Bitmap bitmap;

    public DistanceFieldGlyph(GlyphMetrics metrics, Supplier<FT_Face> face, int glyphIndex, float oversample) {
        this.metrics = metrics;
        this.face = face;
        this.glyphIndex = glyphIndex;
        this.oversample = oversample;
    }

    private static Bitmap rasterize(FT_Face face, int index, float oversample) {
        // FreeType's face has shared mutable glyph storage. Copy the coverage
        // before releasing its lock; uploads later use only immutable data.
        synchronized (FreeTypeUtil.LOCK) {
            FreeTypeUtil.checkFatalError(FreeType.FT_Load_Glyph(face, index,
                FreeType.FT_LOAD_RENDER | FreeType.FT_LOAD_NO_BITMAP | FreeType.FT_LOAD_NO_HINTING),
                "Rasterizing Bytephase distance-field glyph");
            FT_GlyphSlot slot = face.glyph();
            if (slot == null) throw new IllegalStateException("Font glyph slot is missing");
            FT_Bitmap source = slot.bitmap();
            int glyphWidth = source.width(), glyphHeight = source.rows();
            if (glyphWidth == 0 || glyphHeight == 0) return null;
            int width = glyphWidth + PADDING * 2, height = glyphHeight + PADDING * 2;
            int pitch = source.pitch();
            ByteBuffer coverage = source.buffer(Math.abs(pitch) * glyphHeight);
            if (coverage == null) throw new IllegalStateException("Font bitmap is missing");
            double[] outside = new double[width * height];
            double[] inside = new double[width * height];
            Arrays.fill(outside, FAR);
            for (int y = 0; y < glyphHeight; y++) {
                int row = (pitch < 0 ? glyphHeight - 1 - y : y) * Math.abs(pitch);
                for (int x = 0; x < glyphWidth; x++) {
                    double alpha = (coverage.get(row + x) & 255) / 255.0;
                    int pixel = (y + PADDING) * width + x + PADDING;
                    double edge = 0.5 - alpha;
                    outside[pixel] = alpha == 0 ? FAR : Math.max(0, edge) * Math.max(0, edge);
                    inside[pixel] = alpha == 1 ? FAR : Math.min(0, edge) * Math.min(0, edge);
                }
            }
            transform(outside, width, height);
            transform(inside, width, height);
            byte[] pixels = new byte[width * height];
            for (int i = 0; i < pixels.length; i++) {
                double distance = Math.sqrt(outside[i]) - Math.sqrt(inside[i]);
                pixels[i] = (byte) Math.max(0, Math.min(255, Math.round(128 - distance * 128 / PADDING)));
            }
            return new Bitmap(width, height, oversample,
                (slot.bitmap_left() - PADDING) / oversample,
                (slot.bitmap_top() + PADDING) / oversample, pixels);
        }
    }

    @Override public GlyphMetrics getMetrics() { return metrics; }

    @Override public BakedGlyph bake(AbstractGlyphBaker baker) {
        // Font discovery asks about many unused glyphs. Only rasterize a
        // distance field when a glyph is actually drawn, then reuse its data.
        if (bitmap == null) bitmap = rasterize(face.get(), glyphIndex, oversample);
        if (bitmap == null) return baker.getBlankGlyph();
        return baker.bake(metrics, bitmap);
    }

    /** Kept distinct from bitmap fallback glyphs so their atlas formats cannot mix. */
    public record Bitmap(int width, int height, float oversample, float bearingX,
                         float ascent, byte[] pixels) implements UploadableGlyph {
        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
        @Override public float getOversample() { return oversample; }
        @Override public float getBearingX() { return bearingX; }
        @Override public float getAscent() { return ascent; }
        @Override public boolean hasColor() { return false; }
        @Override public void upload(int x, int y, GpuTexture target) {
            ByteBuffer buffer = MemoryUtil.memAlloc(pixels.length);
            try {
                buffer.put(pixels).flip();
                RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                    target, buffer, NativeImage.Format.LUMINANCE, 0, 0, x, y, width, height);
            } finally {
                MemoryUtil.memFree(buffer);
            }
        }
    }

    /** Exact squared Euclidean distance transform, first columns, then rows. */
    private static void transform(double[] grid, int width, int height) {
        int length = Math.max(width, height);
        double[] input = new double[length], output = new double[length];
        double[] boundaries = new double[length + 1];
        int[] sites = new int[length];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) input[y] = grid[y * width + x];
            transformLine(input, output, sites, boundaries, height);
            for (int y = 0; y < height; y++) grid[y * width + x] = output[y];
        }
        for (int y = 0; y < height; y++) {
            System.arraycopy(grid, y * width, input, 0, width);
            transformLine(input, output, sites, boundaries, width);
            System.arraycopy(output, 0, grid, y * width, width);
        }
    }

    private static void transformLine(double[] input, double[] output, int[] sites,
                                      double[] boundaries, int length) {
        int last = 0;
        sites[0] = 0;
        boundaries[0] = Double.NEGATIVE_INFINITY;
        boundaries[1] = Double.POSITIVE_INFINITY;
        for (int q = 1; q < length; q++) {
            double crossing;
            do {
                int p = sites[last];
                crossing = ((input[q] + (double) q * q) - (input[p] + (double) p * p)) / (2 * (q - p));
                if (crossing > boundaries[last]) break;
                last--;
            } while (last >= 0);
            sites[++last] = q;
            boundaries[last] = crossing;
            boundaries[last + 1] = Double.POSITIVE_INFINITY;
        }
        last = 0;
        for (int q = 0; q < length; q++) {
            while (boundaries[last + 1] < q) last++;
            double distance = q - sites[last];
            output[q] = distance * distance + input[sites[last]];
        }
    }
}
