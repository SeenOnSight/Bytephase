package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.MovementUtil;

/**
 * Raises the per-tick change in horizontal speed, so the player reaches the
 * target speed sooner. Vanilla adds about 0.02 per tick; this adds the
 * configured amount instead.
 */
public final class FastAccel extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", 0.29, 0.1, 1.0, 0.01));
    private final NumberSetting acceleration = add(new NumberSetting("Acceleration", 0.08, 0.01, 0.2, 0.01));

    public FastAccel() {
        super("FastAccel", Category.MOVEMENT, "Accelerates to your movement speed faster");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (!isEnabled() || mc == null || mc.player == null || mc.currentScreen != null) {
            return;
        }
        var player = mc.player;
        if (!player.isOnGround() || player.isSneaking() || player.getAbilities().flying || player.isGliding()) {
            return;
        }
        if (player.isTouchingWater() || player.isInLava()) {
            return;
        }
        if (!hasMovementInput(mc)) {
            return;
        }
        MovementUtil.accelerateHorizontal(player, speed.getValue(), acceleration.getValue());
    }

    private static boolean hasMovementInput(net.minecraft.client.MinecraftClient mc) {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
    }
}
