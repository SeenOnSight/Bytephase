package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.MovementUtil;

public final class FastSwim extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", 1.5, 1.1, 3.0, 0.05));

    public FastSwim() {
        super("FastSwim", Category.MOVEMENT, "Makes you faster in water");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || !mc.player.isTouchingWater()) {
            return;
        }
        MovementUtil.accelerateHorizontal(mc.player, 0.12 * speed.getValue(), 0.02);
    }
}
