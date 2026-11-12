package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

/** Redirects existing horizontal momentum toward the movement keys while airborne. */
public final class Strafe extends Module {
    private final NumberSetting control = add(new NumberSetting("Control", 100.0, 0.0, 100.0, 5.0));

    public Strafe() {
        super("Strafe", Category.MOVEMENT, "Improves directional control while airborne");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.currentScreen != null) return;
        var player = mc.player;
        var fly = BytePhase.modules == null ? null : BytePhase.modules.get(Fly.class);
        if (player.isSpectator() || player.isOnGround() || player.getAbilities().flying || player.isGliding()
                || player.isTouchingWater() || player.isInLava() || player.isClimbing() || player.hasVehicle()
                || fly != null && fly.isEnabled()) return;

        double forward = (mc.options.forwardKey.isPressed() ? 1.0 : 0.0)
                - (mc.options.backKey.isPressed() ? 1.0 : 0.0);
        double sideways = (mc.options.rightKey.isPressed() ? 1.0 : 0.0)
                - (mc.options.leftKey.isPressed() ? 1.0 : 0.0);
        double inputLength = Math.hypot(forward, sideways);
        Vec3d velocity = player.getVelocity();
        double speed = Math.hypot(velocity.x, velocity.z);
        double strength = control.getValue() / 100.0;
        // Do not invent speed at rest or stop residual momentum without input.
        if (inputLength == 0.0 || speed < 1.0e-6 || strength <= 0.0) return;

        forward /= inputLength;
        sideways /= inputLength;
        double yaw = Math.toRadians(player.getYaw());
        double targetX = -Math.sin(yaw) * forward - Math.cos(yaw) * sideways;
        double targetZ = Math.cos(yaw) * forward - Math.sin(yaw) * sideways;
        // Turn by a fraction of the shortest angle. Unlike interpolating vectors,
        // this also works when reversing direction at exactly 50% control.
        double currentAngle = Math.atan2(velocity.z, velocity.x);
        double targetAngle = Math.atan2(targetZ, targetX);
        double difference = Math.atan2(Math.sin(targetAngle - currentAngle), Math.cos(targetAngle - currentAngle));
        double angle = currentAngle + difference * strength;
        player.setVelocityClient(new Vec3d(Math.cos(angle) * speed, velocity.y, Math.sin(angle) * speed));
    }
}
