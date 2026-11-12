package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.MovementUtil;

/**
 * Banks one tick of charge for every tick spent standing still, up to
 * {@code Charge}, then spends that charge as accelerated movement. It is a
 * client-side burst of speed, not a timer change, so the server sees ordinary
 * per-tick movement.
 */
public final class TickShift extends Module {
    private final NumberSetting charge = add(new NumberSetting("Charge", 20.0, 1.0, 100.0, 1.0));
    private final NumberSetting speed = add(new NumberSetting("Speed", 0.4, 0.1, 1.0, 0.01));

    private double stored;

    public TickShift() {
        super("TickShift", Category.MOVEMENT, "Banks speed while standing still and spends it when moving");
    }

    @Override
    protected void onDisable() {
        stored = 0.0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (!isEnabled() || mc == null || mc.player == null || mc.currentScreen != null) {
            return;
        }
        var player = mc.player;
        boolean moving = hasMovementInput(mc) || MovementUtil.horizontalSpeed(player.getVelocity()) > 0.02;
        if (!moving && player.isOnGround() && !player.isTouchingWater() && !player.isInLava()) {
            stored = Math.min(charge.getValue(), stored + 1.0);
            return;
        }
        if (stored <= 0.0 || !player.isOnGround()) {
            return;
        }
        MovementUtil.accelerateHorizontal(player, speed.getValue(), 0.06);
        stored -= 1.0;
    }

    public int storedTicks() {
        return (int) stored;
    }

    private static boolean hasMovementInput(net.minecraft.client.MinecraftClient mc) {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
    }
}
