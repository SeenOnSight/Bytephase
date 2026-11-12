package dev.bytephase.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public final class MovementUtil {
    private MovementUtil() {
    }

    public static double horizontalSpeed(Vec3d velocity) {
        return Math.hypot(velocity.x, velocity.z);
    }

    /** Scales the current horizontal velocity toward {@code target} by at most {@code acceleration}. */
    public static void accelerateHorizontal(Entity entity, double target, double acceleration) {
        Vec3d velocity = entity.getVelocity();
        double speed = horizontalSpeed(velocity);
        if (speed < 1.0e-4 || speed >= target) {
            return;
        }
        double newSpeed = Math.min(target, speed + acceleration);
        double factor = newSpeed / speed;
        entity.setVelocityClient(new Vec3d(velocity.x * factor, velocity.y, velocity.z * factor));
    }

    public static void setMinimumFallSpeed(Entity entity, double minY) {
        Vec3d velocity = entity.getVelocity();
        if (velocity.y > minY) {
            entity.setVelocityClient(new Vec3d(velocity.x, minY, velocity.z));
        }
    }
}
