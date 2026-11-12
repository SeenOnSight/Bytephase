package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Drives a ridden entity at a fixed speed from your movement keys, instead of
 * waiting for the entity's own acceleration. The look direction of the vehicle
 * is used rather than the player's, because boats and mounts turn separately.
 */
public final class EntitySpeed extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", 0.5, 0.1, 2.0, 0.05));

    public EntitySpeed() {
        super("EntitySpeed", Category.MOVEMENT, "Moves ridden boats and mounts faster");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (!isEnabled() || mc == null || mc.player == null) {
            return;
        }
        Entity vehicle = mc.player.getVehicle();
        if (vehicle == null || vehicle.isRemoved()) {
            return;
        }
        double forward = (mc.options.forwardKey.isPressed() ? 1.0 : 0.0)
                - (mc.options.backKey.isPressed() ? 1.0 : 0.0);
        double strafe = (mc.options.leftKey.isPressed() ? 1.0 : 0.0)
                - (mc.options.rightKey.isPressed() ? 1.0 : 0.0);
        if (forward == 0.0 && strafe == 0.0) {
            return;
        }
        Vec3d look = Vec3d.fromPolar(0.0f, vehicle.getYaw());
        Vec3d left = new Vec3d(look.z, 0.0, -look.x);
        Vec3d direction = look.multiply(forward).add(left.multiply(strafe));
        if (direction.lengthSquared() < 1.0e-6) {
            return;
        }
        direction = direction.normalize().multiply(speed.getValue());
        Vec3d velocity = vehicle.getVelocity();
        vehicle.setVelocityClient(new Vec3d(direction.x, velocity.y, direction.z));
    }
}
