package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.module.combat.WTap;

public final class Sprint extends Module {
    private boolean wasSprinting;

    public Sprint() {
        super("Sprint", Category.MOVEMENT, "Always sprints while moving");
    }

    @Override
    protected void onEnable() {
        var mc = BytePhase.mc;
        wasSprinting = mc != null && mc.player != null && mc.player.isSprinting();
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.currentScreen != null) {
            return;
        }
        WTap wTap = BytePhase.modules == null ? null : BytePhase.modules.wTap();
        if (wTap != null && wTap.masking()) {
            mc.player.setSprinting(false);
            return;
        }
        if (mc.options.forwardKey.isPressed() && !mc.options.sneakKey.isPressed()) {
            mc.player.setSprinting(true);
        }
    }

    @Override
    protected void onDisable() {
        var mc = BytePhase.mc;
        if (mc != null && mc.player != null && !wasSprinting
                && !mc.options.sprintKey.isPressed()) {
            mc.player.setSprinting(false);
        }
        wasSprinting = false;
    }
}
