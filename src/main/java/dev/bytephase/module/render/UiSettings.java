package dev.bytephase.module.render;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;

/** Appearance preferences apply even when this settings entry is not enabled. */
public final class UiSettings extends Module {
    private final ColorSetting accent = add(new ColorSetting("Accent", 0xFF6466E9));
    private final ColorSetting text = add(new ColorSetting("Text", 0xFFD2D5DB));
    private final BooleanSetting boldHeaders = add(new BooleanSetting("BoldHeaders", true));
    private final BooleanSetting textShadow = add(new BooleanSetting("TextShadow", false));

    public UiSettings() {
        super("Appearance", Category.UI, "ClickGUI colors and text style");
    }

    public int accent() { return accent.getValue(); }
    public int text() { return text.getValue(); }
    public boolean boldHeaders() { return boldHeaders.getValue(); }
    public boolean textShadow() { return textShadow.getValue(); }
}
