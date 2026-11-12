package dev.bytephase.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/** Shared reach checks against the actual block shape instead of its center. */
public final class BlockReachUtil {
    private BlockReachUtil() { }

    public static boolean isWithinReach(MinecraftClient mc, BlockPos pos, double reach) {
        if (mc == null || mc.player == null || mc.world == null || pos == null
                || !Double.isFinite(reach) || reach < 0.0) {
            return false;
        }
        var state = mc.world.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(mc.world, pos);
        if (shape.isEmpty()) {
            shape = state.getOutlineShape(mc.world, pos);
        }
        if (shape.isEmpty()) {
            return false;
        }
        Vec3d eye = mc.player.getEyePos();
        return shape.offset(pos).getClosestPointTo(eye)
                .map(point -> eye.squaredDistanceTo(point) <= reach * reach)
                .orElse(false);
    }
}
