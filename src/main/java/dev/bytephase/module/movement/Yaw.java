package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

/** Holds horizontal rotation at a fixed angle, for precise bridging or lining up. */
public final class Yaw extends Module {
    private final NumberSetting yaw = add(new NumberSetting("Yaw", 0.0, -180.0, 180.0, 5.0));

    public Yaw() {
        super("Yaw", Category.MOVEMENT, "Locks your yaw to a fixed angle");
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    public void onTick() {
        apply();
    }

    private void apply() {
        var mc = BytePhase.mc;
        if (isEnabled() && mc != null && mc.player != null) {
            mc.player.setYaw(yaw.asFloat());
        }
    }
}
