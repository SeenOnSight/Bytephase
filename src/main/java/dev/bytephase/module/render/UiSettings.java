package dev.bytephase.module.render;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.StringSetting;
import dev.bytephase.setting.ActionSetting;

/** Appearance preferences apply even when this settings entry is not enabled. */
public final class UiSettings extends Module {
    private final ColorSetting accent = add(new ColorSetting("Accent", dev.bytephase.ui.Theme.ACCENT));
    private final ColorSetting text = add(new ColorSetting("Text", dev.bytephase.ui.Theme.TEXT));
    private final ColorSetting panelColor = add(new ColorSetting("Background color", dev.bytephase.ui.Theme.PANEL));
    private final NumberSetting opacity = add(new NumberSetting("PanelOpacity", 45, 20, 95, 1));
    private final BooleanSetting blur = add(new BooleanSetting("BackgroundBlur", true));
    private final BooleanSetting imageEnabled = add(new BooleanSetting("Use background image", false));
    private final ActionSetting chooseImage = add(new ActionSetting("Background image", "Choose image…",
        () -> dev.bytephase.render.ui.UiBackgroundImage.choose(this, dev.bytephase.BytePhase.mc.currentScreen)));
    private final StringSetting imagePath = add(new StringSetting("Image path", "", 4096));
    private final ModeSetting imageFit = add(new ModeSetting("Image fit",
        new String[]{"Cover", "Contain", "Stretch"}, "Cover"));
    private final NumberSetting imageOpacity = add(new NumberSetting("Image opacity", 100, 0, 100, 1));
    private final ActionSetting reloadImage = add(new ActionSetting("Reload background image", "Reload",
        dev.bytephase.render.ui.UiBackgroundImage::reload));
    private final ActionSetting clearImage = add(new ActionSetting("Remove background image", "Remove", this::clearBackgroundImage));
    private final BooleanSetting streamProof = add(new BooleanSetting("StreamProof", false));
    private final ModeSetting uiFont = add(new ModeSetting("UIFont", dev.bytephase.render.ClientFonts.OPTIONS, "Inter"));
    private final ModeSetting hudFont = add(new ModeSetting("HUDFont", dev.bytephase.render.ClientFonts.OPTIONS, "Inter"));
    private final BooleanSetting boldHeaders = add(new BooleanSetting("BoldHeaders", true));
    private final BooleanSetting textShadow = add(new BooleanSetting("TextShadow", false));
    private final BooleanSetting avoidLabelOverlap = add(new BooleanSetting("AvoidLabelOverlap", true));
    private final NumberSetting labelLimit = add(new NumberSetting("WorldLabelLimit", 48, 4, 128, 1));
    private final dev.bytephase.setting.StructuredSetting hudLayout = add(new dev.bytephase.setting.StructuredSetting(
        "HUD Layout Data", dev.bytephase.render.HudLayout::save, dev.bytephase.render.HudLayout::load));
    private final dev.bytephase.setting.ActionSetting hudEditor = add(new dev.bytephase.setting.ActionSetting(
        "Move HUD", "Edit positions / sizes", () -> dev.bytephase.BytePhase.mc.setScreen(
            new dev.bytephase.ui.HudEditorScreen(dev.bytephase.BytePhase.mc.currentScreen))));

    public UiSettings() {
        super("Appearance", Category.UI, "ClickGUI colors and text style");
        imageFit.visibleWhen(() -> imageEnabled.getValue());
        imageOpacity.visibleWhen(() -> imageEnabled.getValue());
        reloadImage.visibleWhen(() -> !imagePath.getValue().isBlank());
        clearImage.visibleWhen(() -> !imagePath.getValue().isBlank());
    }

    public int accent() { return accent.getValue(); }
    public int accentForeground() {
        int rgb = accent.getValue();
        double light = (rgb >> 16 & 255) * 0.299 + (rgb >> 8 & 255) * 0.587 + (rgb & 255) * 0.114;
        return light >= 160 ? 0xFF101010 : 0xFFF0F0F0;
    }
    public void setAccent(int argb) { accent.setValue(argb); }
    public int text() { return text.getValue(); }
    public int panelColor() { return panelColor.getValue(); }
    public boolean imageEnabled() { return imageEnabled.getValue(); }
    public String imagePath() { return imagePath.getValue(); }
    public String imageFit() { return imageFit.getValue(); }
    public int imageOpacity() { return imageOpacity.asInt(); }
    public StringSetting imagePathSetting() { return imagePath; }
    public void setBackgroundImage(String path) {
        imagePath.setValue(path);
        imageEnabled.setValue(!path.isBlank());
        dev.bytephase.render.ui.UiBackgroundImage.reload();
        if (dev.bytephase.BytePhase.config != null) dev.bytephase.BytePhase.config.requestSave();
    }
    public void clearBackgroundImage() { setBackgroundImage(""); }
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
