package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.Render2D;
import dev.bytephase.render.ShaderCrosshairRenderer;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;

/**
 * The Clarity shader crosshair, with a small primitive fallback for the rare
 * case where raw GL shader compilation fails.
 */
public final class Crosshair extends Module {
    public static final String[] STYLES = {
            "Cross", "Dot", "Circle", "Cross + Dot", "Segments", "Chevron", "Diamond", "Brackets", "Vanilla"
    };
    public static final String[] COLOR_MODES = {"Solid", "Gradient", "Chroma"};

    private final ModeSetting style = add(new ModeSetting("Style", STYLES, "Cross"));
    private final NumberSetting gap = add(new NumberSetting("Gap", 3.0, 0.0, 16.0, 0.5));
    private final NumberSetting length = add(new NumberSetting("Length", 4.0, 1.0, 20.0, 0.5));
    private final NumberSetting thickness = add(new NumberSetting("Thickness", 1.0, 0.5, 5.0, 0.1));
    private final BooleanSetting dot = add(new BooleanSetting("Dot", true));
    private final NumberSetting dotSize = add(new NumberSetting("DotSize", 1.0, 0.5, 5.0, 0.1));
    private final ModeSetting colorMode = add(new ModeSetting("ColorMode", COLOR_MODES, "Solid"));
    private final ColorSetting primary = add(new ColorSetting("Primary", 0xFF7AA2F7));
    private final ColorSetting secondary = add(new ColorSetting("Secondary", 0xFF7A8CFF));
    private final ColorSetting hitColor = add(new ColorSetting("HitColor", 0xFFFF3B3B));
    private final ColorSetting targetColor = add(new ColorSetting("TargetColor", 0xFFFF5050));
    private final BooleanSetting outline = add(new BooleanSetting("Outline", false));
    private final NumberSetting outlineWidth = add(new NumberSetting("OutlineWidth", 1.0, 0.5, 4.0, 0.1));
    private final NumberSetting glow = add(new NumberSetting("Glow", 1.0, 0.0, 4.0, 0.1));
    private final NumberSetting rotation = add(new NumberSetting("Rotation", 0.0, 0.0, 360.0, 5.0));
    private final NumberSetting spinSpeed = add(new NumberSetting("SpinSpeed", 0.0, 0.0, 4.0, 0.1));
    private final BooleanSetting dynamicSpread = add(new BooleanSetting("DynamicSpread", true));
    private final BooleanSetting targetTint = add(new BooleanSetting("TargetTint", true));
    private final BooleanSetting showCooldown = add(new BooleanSetting("Cooldown", true));
    private final BooleanSetting hitmarker = add(new BooleanSetting("Hitmarker", true));
    private final BooleanSetting expandOnHit = add(new BooleanSetting("ExpandOnHit", true));
    private final BooleanSetting flashOnHit = add(new BooleanSetting("FlashOnHit", true));
    private final BooleanSetting shockwave = add(new BooleanSetting("Shockwave", true));
    private final BooleanSetting hideVanilla = add(new BooleanSetting("HideVanilla", true));

    private long hitAt;
    private long killAt;
    private Entity lastTarget;
    private boolean shaderFailed;

    public Crosshair() {
        super("Crosshair", Category.RENDER, "Draws the supplied Clarity shader crosshair");
    }

    public boolean hideVanilla() {
        return !style.getValue().equals("Vanilla") && hideVanilla.getValue();
    }

    public String style() {
        return style.getValue();
    }

    public String colorMode() {
        return colorMode.getValue();
    }

    public float gap() {
        return gap.asFloat();
    }

    public float length() {
        return length.asFloat();
    }

    public float thickness() {
        return thickness.asFloat();
    }

    public boolean dot() {
        return dot.getValue();
    }

    public float dotSize() {
        return dotSize.asFloat();
    }

    public ColorSetting primary() {
        return primary;
    }

    public ColorSetting secondary() {
        return secondary;
    }

    public ColorSetting hitColor() {
        return hitColor;
    }

    public ColorSetting targetColor() {
        return targetColor;
    }

    public boolean outline() {
        return outline.getValue();
    }

    public float outlineWidth() {
        return outlineWidth.asFloat();
    }

    public float glow() {
        return glow.asFloat();
    }

    public float rotation() {
        return rotation.asFloat();
    }

    public float spinSpeed() {
        return spinSpeed.asFloat();
    }

    public boolean dynamicSpread() {
        return dynamicSpread.getValue();
    }

    public boolean targetTint() {
        return targetTint.getValue();
    }

    public boolean showCooldown() {
        return showCooldown.getValue();
    }

    public boolean hitmarker() {
        return hitmarker.getValue();
    }

    public boolean expandOnHit() {
        return expandOnHit.getValue();
    }

    public boolean flashOnHit() {
        return flashOnHit.getValue();
    }

    public boolean shockwave() {
        return shockwave.getValue();
    }

    public float hitAgeSeconds(long now) {
        return hitAt == 0L ? -1.0f : (now - hitAt) / 1.0e9f;
    }

    public float killAgeSeconds(long now) {
        return killAt == 0L ? -1.0f : (now - killAt) / 1.0e9f;
    }

    @Override
    protected void onEnable() {
        // Allow a fresh shader attempt after a transient first-frame failure
        // (for example, a render target allocation failure while resizing).
        shaderFailed = false;
    }

    @Override
    protected void onDisable() {
        hitAt = 0L;
        killAt = 0L;
        lastTarget = null;
    }

    @Override
    public void onTick() {
        if (lastTarget != null && (!lastTarget.isAlive() || lastTarget.isRemoved())) {
            killAt = System.nanoTime();
            lastTarget = null;
        }
    }

    @Override
    public void onAttack(net.minecraft.entity.player.PlayerEntity self, Entity target) {
        if (!hitmarker.getValue() || target == null) {
            return;
        }
        hitAt = System.nanoTime();
        lastTarget = target;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.options.hudHidden || style.getValue().equals("Vanilla")) {
            return;
        }
        if (!shaderFailed && ShaderCrosshairRenderer.render(context, this)) {
            return;
        }
        shaderFailed = true;
        drawFallback(context, mc);
    }

    private void drawFallback(DrawContext context, net.minecraft.client.MinecraftClient mc) {
        int cx = mc.getWindow().getScaledWidth() / 2;
        int cy = mc.getWindow().getScaledHeight() / 2;
        int color = 0xFF000000 | primary.rgb();
        double g = gap.getValue();
        double l = length.getValue();
        Render2D.drawLine(context, cx - g - l, cy, cx - g, cy, color);
        Render2D.drawLine(context, cx + g, cy, cx + g + l, cy, color);
        Render2D.drawLine(context, cx, cy - g - l, cx, cy - g, color);
        Render2D.drawLine(context, cx, cy + g, cx, cy + g + l, color);
        if (dot.getValue()) {
            context.fill(cx, cy, cx + 1, cy + 1, color);
        }
    }
}
