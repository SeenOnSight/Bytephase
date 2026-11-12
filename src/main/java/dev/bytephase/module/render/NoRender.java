package dev.bytephase.module.render;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;

/** Hides selected screen effects that get in the way during fights. */
public final class NoRender extends Module {
    private final BooleanSetting fireOverlay = add(new BooleanSetting("Fire", true));
    private final BooleanSetting blockOverlay = add(new BooleanSetting("BlockOverlay", true));
    private final BooleanSetting blockBreakParticles = add(new BooleanSetting("BlockBreakParticles", true));
    private final BooleanSetting totemAnimation = add(new BooleanSetting("TotemAnimation", true));
    private final BooleanSetting hurtCamera = add(new BooleanSetting("HurtCamera", true));

    public NoRender() {
        super("NoRender", Category.RENDER, "Hides the fire, in-wall block overlay and hurt effects");
    }

    public boolean fireOverlay() {
        return isEnabled() && fireOverlay.getValue();
    }

    /** Hides the block texture drawn over the whole screen while inside a block. */
    public boolean blockOverlay() {
        return isEnabled() && blockOverlay.getValue();
    }

    public boolean blockBreakParticles() {
        return isEnabled() && blockBreakParticles.getValue();
    }

    public boolean totemAnimation() {
        return isEnabled() && totemAnimation.getValue();
    }

    public boolean hurtCamera() {
        return isEnabled() && hurtCamera.getValue();
    }
}
