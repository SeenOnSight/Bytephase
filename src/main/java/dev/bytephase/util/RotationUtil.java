package dev.bytephase.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Shared angle maths for the modules that aim for you. */
public final class RotationUtil {
    private RotationUtil() {
    }

    public static float yawTo(Vec3d from, Vec3d to) {
        return (float) (MathHelper.atan2(to.z - from.z, to.x - from.x)
                * MathHelper.DEGREES_PER_RADIAN) - 90.0f;
    }

    public static float pitchTo(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        return MathHelper.clamp((float) (-MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz))
                * MathHelper.DEGREES_PER_RADIAN), -90.0f, 90.0f);
    }

    /**
     * Point on a target's body to aim at: the neck normally, the chest when the
     * target stands a block or more above {@code myFeetY}. Aiming at the eyes
     * puts the crosshair over their head, which looks wrong and overshoots.
     */
    public static Vec3d aimPoint(LivingEntity target, double myFeetY) {
        Box box = target.getBoundingBox();
        double height = Math.max(0.4, box.getLengthY());
        double neck = box.minY + height * 0.80;
        double chest = box.minY + height * 0.60;
        // A target on higher ground is aimed at the chest, so the crosshair sits
        // on their body instead of floating above their head.
        double y = box.minY >= myFeetY + 1.0 ? chest : neck;
        return new Vec3d((box.minX + box.maxX) * 0.5, y, (box.minZ + box.maxZ) * 0.5);
    }

    /** Absolute angle between two yaw/pitch pairs, in degrees. */
    public static float angleBetween(float yawA, float pitchA, float yawB, float pitchB) {
        float yaw = MathHelper.wrapDegrees(yawA - yawB);
        float pitch = pitchA - pitchB;
        return MathHelper.sqrt(yaw * yaw + pitch * pitch);
    }
}
