package dev.bytephase.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.UiSettings;

/** One bounded screen-space label budget shared by all world overlays. */
public final class OverlayLabels {
    private static final int[][] RECTANGLES = new int[128][4];
    private static int count;
    private static int limit = 48;
    private static boolean avoidOverlap = true;
    private static int screenWidth;
    private static int screenHeight;

    private OverlayLabels() { }

    public static void beginFrame() {
        count = 0;
        UiSettings appearance = BytePhase.modules.get(UiSettings.class);
        limit = appearance == null ? 48 : appearance.labelLimit();
        avoidOverlap = appearance == null || appearance.avoidLabelOverlap();
        screenWidth = BytePhase.mc.getWindow().getScaledWidth();
        screenHeight = BytePhase.mc.getWindow().getScaledHeight();
    }

    public static boolean reserve(int x, int y, int width, int height) {
        if (count >= limit || width <= 0 || height <= 0 || x < 0 || y < 0
                || x + width > screenWidth || y + height > screenHeight) return false;
        if (avoidOverlap) {
            for (int i = 0; i < count; i++) {
                int[] bounds = RECTANGLES[i];
                if (x < bounds[2] + 2 && x + width + 2 > bounds[0]
                        && y < bounds[3] + 2 && y + height + 2 > bounds[1]) return false;
            }
        }
        int[] bounds = RECTANGLES[count++];
        bounds[0] = x;
        bounds[1] = y;
        bounds[2] = x + width;
        bounds[3] = y + height;
        return true;
    }
}
