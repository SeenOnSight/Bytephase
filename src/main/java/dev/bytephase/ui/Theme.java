package dev.bytephase.ui;

/** Shared ClickGUI colours. Change {@link #ACCENT} to re-skin the whole GUI. */
public final class Theme {
    public static final int BG = 0xFF000000;
    public static final int BORDER = 0xFF1B1B1B;
    public static final int PANEL = 0xFF0B0B0B;
    public static final int SURFACE = 0xFF0E0E0E;
    public static final int HOVER = 0xFF1B1B1B;
    public static final int OFF = 0xFF272727;

    // Flat, translucent look: rows are plain text on a dark veil; enabled rows
    // get a translucent accent bar.
    public static final int PANEL_BG = 0xA0000000;
    public static final int SETTINGS_BG = 0x66000000;
    public static final int ROW_ON = 0x90C0C0C0;
    public static final int ROW_HOVER = 0x30FFFFFF;

    public static final int ACCENT = 0xFFC0C0C0;
    public static final int ACCENT_DEEP = 0xFF808080;
    public static final int ACCENT_BG = 0xFF151515;
    public static final int ACCENT_TEXT = 0xFFD0D0D0;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int TEXT = 0xFFF0F0F0;
    public static final int MUTED = 0xFFA6A6A6;
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
