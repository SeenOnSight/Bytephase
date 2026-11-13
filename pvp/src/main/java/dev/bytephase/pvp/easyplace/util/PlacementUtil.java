package dev.bytephase.pvp.easyplace.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class PlacementUtil {
    private PlacementUtil() {
    }

    public static Vec3 faceCenterCursor(BlockPos targetPos, Direction placeSide) {
        return targetPos.getCenter().add(placeSide.getStepX() * 0.5, placeSide.getStepY() * 0.5, placeSide.getStepZ() * 0.5);
    }

    public static float[] calcRotation(Vec3 eyePos, Vec3 target) {
        double dx = target.x - eyePos.x;
        double dy = target.y - eyePos.y;
        double dz = target.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float)Math.toDegrees(-Math.atan2(dy, dist));
        return new float[]{Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0F, 90.0F)};
    }

    public static BlockHitResult rayTraceTowards(Player player, float yaw, float pitch, double reach) {
        return rayTraceFrom(player, getEyePos(player), yaw, pitch, reach);
    }

    public static BlockHitResult rayTraceFrom(Player player, Vec3 eyePos, float yaw, float pitch, double reach) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }

        double yawRad = Math.toRadians(yaw);
        double cosPitch = Math.cos(Math.toRadians(pitch));
        double sinPitch = Math.sin(Math.toRadians(pitch));
        Vec3 direction = new Vec3(-Math.sin(yawRad) * cosPitch, -sinPitch, Math.cos(yawRad) * cosPitch);
        Vec3 end = eyePos.add(direction.scale(reach));
        return level.clip(new ClipContext(eyePos, end, Block.OUTLINE, Fluid.NONE, player));
    }

    public static Vec3 getEyePos(Player player) {
        return new Vec3(player.getX(), player.getEyeY(), player.getZ());
    }

    public static Direction getClickFace(Direction placeSide) {
        return placeSide.getOpposite();
    }

    public static Direction getPlaceSide(BlockPos targetPos, Vec3 eyePos) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }

        Direction best = null;
        double bestScore = -Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            BlockPos againstPos = targetPos.relative(dir);
            BlockState againstState = level.getBlockState(againstPos);
            if (!againstState.isAir() && !againstState.getCollisionShape(level, againstPos).isEmpty()) {
                Vec3 diff = eyePos.subtract(againstPos.getCenter());
                double score = diff.x * dir.getStepX() + diff.y * dir.getStepY() + diff.z * dir.getStepZ();
                if (score > bestScore) {
                    bestScore = score;
                    best = dir;
                }
            }
        }

        return best;
    }

    public static boolean faceMatchesAxis(BlockState expected, Direction clickedFace) {
        return !expected.hasProperty(BlockStateProperties.AXIS) || expected.getValue(BlockStateProperties.AXIS) == clickedFace.getAxis();
    }

    public static boolean isFaceVisibleToEye(BlockPos pos, Direction clickedFace, Vec3 eyePos) {
        double buffer = 0.05;

        return switch (clickedFace) {
            case NORTH -> eyePos.z < pos.getZ() - 0.05;
            case SOUTH -> eyePos.z > pos.getZ() + 1.0 + 0.05;
            case WEST -> eyePos.x < pos.getX() - 0.05;
            case EAST -> eyePos.x > pos.getX() + 1.0 + 0.05;
            case UP -> eyePos.y > pos.getY() + 1.0 + 0.05;
            case DOWN -> eyePos.y < pos.getY() - 0.05;
        };
    }
}
