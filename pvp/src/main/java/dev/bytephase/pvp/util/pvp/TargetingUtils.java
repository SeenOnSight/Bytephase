package dev.bytephase.pvp.util.pvp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class TargetingUtils {
    private TargetingUtils() {
    }

    public static Vec3 closestPoint(Vec3 point, AABB box) {
        return new Vec3(Mth.clamp(point.x, box.minX, box.maxX), Mth.clamp(point.y, box.minY, box.maxY), Mth.clamp(point.z, box.minZ, box.maxZ));
    }
}
