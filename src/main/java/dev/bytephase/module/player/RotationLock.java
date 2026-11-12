package dev.bytephase.module.player;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;

public final class RotationLock extends Module {
    private final BooleanSetting lockYaw = add(new BooleanSetting("LockYaw", true));
    private final BooleanSetting lockPitch = add(new BooleanSetting("LockPitch", false));
    private final NumberSetting yaw = add(new NumberSetting("Yaw", 0.0, -180.0, 180.0, 5.0));
    private final NumberSetting pitch = add(new NumberSetting("Pitch", 0.0, -90.0, 90.0, 5.0));

    public RotationLock() {
        super("RotationLock", Category.PLAYER, "Locks your rotations to fixed angles");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.currentScreen != null) {
            return;
        }
        if (lockYaw.getValue()) {
            mc.player.setYaw(yaw.asFloat());
        }
        if (lockPitch.getValue()) {
            mc.player.setPitch(pitch.asFloat());
        }
    }
}
