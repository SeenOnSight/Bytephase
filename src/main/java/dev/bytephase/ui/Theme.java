package dev.bytephase.ui;

/** Shared ClickGUI colours. Change {@link #ACCENT} to re-skin the whole GUI. */
public final class Theme {
    public static final int BG = 0xFF000000;
    public static final int BORDER = 0xFF1B1B1D;
    public static final int PANEL = 0xFF0B0B0B;
    public static final int SURFACE = 0xFF0E0E10;
    public static final int HOVER = 0xFF1B1B1D;
    public static final int OFF = 0xFF27272A;

    // Flat, translucent look: rows are plain text on a dark veil; enabled rows
    // get a translucent accent bar.
    public static final int PANEL_BG = 0xA0000000;
    public static final int SETTINGS_BG = 0x66000000;
    public static final int ROW_ON = 0x906466E9;
    public static final int ROW_HOVER = 0x30FFFFFF;

    public static final int ACCENT = 0xFF6466E9;
    public static final int ACCENT_DEEP = 0xFF4E46DD;
    public static final int ACCENT_BG = 0xFF13112C;
    public static final int ACCENT_TEXT = 0xFFA6B3F9;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int TEXT = 0xFFD2D5DB;
    public static final int MUTED = 0xFF9F9FA8;
    public static final int DIVIDER = 0x1FFFFFFF;

    public static int accent() {
        var settings = dev.bytephase.BytePhase.modules == null ? null
                : dev.bytephase.BytePhase.modules.get(dev.bytephase.module.render.UiSettings.class);
        return settings == null ? ACCENT : settings.accent();
    }

    public static int text() {
        var settings = dev.bytephase.BytePhase.modules == null ? null
                : dev.bytephase.BytePhase.modules.get(dev.bytephase.module.render.UiSettings.class);
        return settings == null ? TEXT : settings.text();
    }

    private Theme() {
    }
}
