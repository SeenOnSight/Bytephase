package dev.bytephase.module.render;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.setting.ModeSetting;

/** Appearance preferences apply even when this settings entry is not enabled. */
public final class UiSettings extends Module {
    private final ColorSetting accent = add(new ColorSetting("Accent", 0xFF6C6FF5));
    private final ColorSetting text = add(new ColorSetting("Text", 0xFFF0F1FF));
    private final NumberSetting opacity = add(new NumberSetting("PanelOpacity", 45, 20, 95, 1));
    private final BooleanSetting blur = add(new BooleanSetting("BackgroundBlur", true));
    private final BooleanSetting streamProof = add(new BooleanSetting("StreamProof", false));
    private final ModeSetting uiFont = add(new ModeSetting("UIFont", dev.bytephase.render.ClientFonts.OPTIONS, "Inter"));
    private final ModeSetting hudFont = add(new ModeSetting("HUDFont", dev.bytephase.render.ClientFonts.OPTIONS, "Inter"));
    private final BooleanSetting boldHeaders = add(new BooleanSetting("BoldHeaders", true));
    private final BooleanSetting textShadow = add(new BooleanSetting("TextShadow", false));
    private final BooleanSetting avoidLabelOverlap = add(new BooleanSetting("AvoidLabelOverlap", true));
    private final NumberSetting labelLimit = add(new NumberSetting("WorldLabelLimit", 48, 4, 128, 1));

    public UiSettings() {
        super("Appearance", Category.UI, "ClickGUI colors and text style");
    }

    public int accent() { return accent.getValue(); }
    public void setAccent(int argb) { accent.setValue(argb); }
    public int text() { return text.getValue(); }
    public int panelOpacity() { return opacity.asInt(); }
    public boolean backgroundBlur() { return blur.getValue(); }
    public boolean streamProof() { return streamProof.getValue(); }
    public void setStreamProof(boolean enabled) { streamProof.setValue(enabled); }
    public String uiFont() { return uiFont.getValue(); }
    public String hudFont() { return hudFont.getValue(); }
    public boolean boldHeaders() { return boldHeaders.getValue(); }
    public boolean textShadow() { return textShadow.getValue(); }
    public boolean avoidLabelOverlap() { return avoidLabelOverlap.getValue(); }
    public int labelLimit() { return labelLimit.asInt(); }
}
