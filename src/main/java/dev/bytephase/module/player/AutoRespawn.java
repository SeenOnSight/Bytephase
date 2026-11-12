package dev.bytephase.module.player;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

public final class AutoRespawn extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", 1.0, 0.0, 40.0, 1.0));
    private int ticks;

    public AutoRespawn() {
        super("AutoRespawn", Category.PLAYER, "Respawns automatically on death");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) {
            return;
        }
        if (!mc.player.isDead()) {
            ticks = delay.asInt();
            return;
        }
        if (ticks > 0) {
            ticks--;
            return;
        }
        ticks = delay.asInt();
        mc.player.requestRespawn();
    }
}
