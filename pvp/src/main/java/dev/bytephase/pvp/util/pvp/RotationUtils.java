package dev.bytephase.pvp.util.pvp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class RotationUtils {
    private RotationUtils() {
    }

    public static Rotation fromTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float)(-(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG));
        return new Rotation(Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0F, 90.0F));
    }

    public static Rotation entityRotation(Entity entity) {
        return new Rotation(entity.getYRot(), entity.getXRot());
    }

    public static float yawDelta(float fromYaw, float toYaw) {
        return Mth.wrapDegrees(toYaw - fromYaw);
    }

    public static float pitchDelta(float fromPitch, float toPitch) {
        return toPitch - fromPitch;
    }

    public static double angleTo(Rotation from, Rotation to) {
        float yaw = yawDelta(from.yaw(), to.yaw());
        float pitch = pitchDelta(from.pitch(), to.pitch());
        return Math.hypot(yaw, pitch);
    }

    public static double mouseScale(Minecraft mc, LocalPlayer player) {
        double speed = mc.options.sensitivity().get() * 0.6F + 0.2F;
        return speed * speed * speed * (mc.options.getCameraType().isFirstPerson() && player.isScoping() ? 1.0 : 8.0);
    }
}
