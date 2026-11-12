package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.MovementUtil;

public final class FastFall extends Module {
    private final NumberSetting multiplier = add(new NumberSetting("Multiplier", 2.0, 1.0, 4.0, 0.1));

    public FastFall() {
        super("FastFall", Category.MOVEMENT, "Makes you descend faster");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.player.isOnGround()) {
            return;
        }
        if (mc.player.getVelocity().y >= 0.0) {
            return;
        }
        MovementUtil.setMinimumFallSpeed(mc.player, -0.5 * multiplier.getValue());
    }
}
