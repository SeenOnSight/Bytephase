package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.input.KeyOverride;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;

public final class AutoWalk extends Module {
    private final BooleanSetting autoJump = add(new BooleanSetting("AutoJump", false));
    public AutoWalk() {
        super("AutoWalk", Category.MOVEMENT, "Keeps you walking forward");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) {
            KeyOverride.releaseAll(this);
            return;
        }
        boolean screen = mc.currentScreen != null;
        KeyOverride.set(this, mc.options.forwardKey, !screen);
        if (autoJump.getValue()) {
            KeyOverride.set(this, mc.options.jumpKey, !screen);
        } else {
            KeyOverride.release(this, mc.options.jumpKey);
        }
    }

    @Override
    protected void onDisable() {
        KeyOverride.releaseAll(this);
    }
}
