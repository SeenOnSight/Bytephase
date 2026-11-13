package dev.bytephase.pvp.easyplace.builder;

import java.util.Random;
import dev.bytephase.pvp.mixin.AccessorMouseHandler;
import dev.bytephase.pvp.easyplace.util.PlacementUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.VoxelShape;

@Environment(EnvType.CLIENT)
public final class Aim {
    private static final Direction[] ORDER = new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};
    private static final double[][] FACE_POINTS = new double[][]{{0.5, 0.5}, {0.5, 0.85}, {0.5, 0.15}, {0.15, 0.5}, {0.85, 0.5}};
    private static final float SETTLED = 0.6F;
    private static final float RETARGET = 25.0F;
    private static Aim.Motion motion;
    private static final Random HAND = new Random();
    public static final float HAND_TURN = 22.0F;

    private Aim() {
    }

    static Aim.Shot at(Minecraft mc, BlockPos pos) {
        LocalPlayer player = mc.player;
        if (player != null && mc.level != null) {
            BlockState state = mc.level.getBlockState(pos);
            VoxelShape shape = state.getShape(mc.level, pos);
            if (shape.isEmpty()) {
                return null;
            }

            AABB box = shape.bounds().move(pos);
            Vec3 eye = player.getEyePosition();
            double reach = player.blockInteractionRange();

            for (Direction face : ORDER) {
                if (faces(box, face, eye)) {
                    Vec3 point = faceCenter(box, face);
                    if (!(point.distanceTo(eye) > reach)) {
                        float[] rot = PlacementUtil.calcRotation(eye, point);
                        BlockHitResult hit = trace(mc, player, rot[0], rot[1], reach);
                        if (hit != null && hit.getType() == Type.BLOCK && hit.getBlockPos().equals(pos) && hit.getDirection() == face) {
                            return new Aim.Shot(hit, rot[0], rot[1]);
                        }
                    }
                }
            }

            return null;
        } else {
            return null;
        }
    }

    static Aim.Shot against(Minecraft mc, BlockPos cell, Fluid fluid) {
        return mc.player == null ? null : against(mc, cell, fluid, mc.player.getEyePosition());
    }

    static Aim.Shot against(Minecraft mc, BlockPos cell, Fluid fluid, Vec3 eye) {
        LocalPlayer player = mc.player;
        if (player != null && mc.level != null) {
            double reach = player.blockInteractionRange();

            for (Direction direction : ORDER) {
                BlockPos next = cell.relative(direction.getOpposite());
                BlockState state = mc.level.getBlockState(next);
                if (!state.canBeReplaced()
                    && !clickable(mc, next, state)
                    && (
                        fluid == null
                            || !(state.getBlock() instanceof LiquidBlockContainer container && container.canPlaceLiquid(player, mc.level, next, state, fluid))
                    )) {
                    VoxelShape shape = state.getShape(mc.level, next);
                    if (!shape.isEmpty()) {
                        AABB box = shape.bounds().move(next);
                        if (faces(box, direction, eye)) {
                            for (double[] at : FACE_POINTS) {
                                Vec3 point = facePoint(box, direction, at[0], at[1]);
                                if (!(point.distanceTo(eye) > reach)) {
                                    float[] rot = PlacementUtil.calcRotation(eye, point);
                                    BlockHitResult hit = PlacementUtil.rayTraceFrom(player, eye, rot[0], rot[1], reach);
                                    if (hit != null && hit.getType() == Type.BLOCK && hit.getBlockPos().equals(next) && hit.getDirection() == direction) {
                                        return new Aim.Shot(hit, rot[0], rot[1]);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private static Vec3 facePoint(AABB box, Direction face, double u, double v) {
        double x = box.minX + (box.maxX - box.minX) * u;
        double z = box.minZ + (box.maxZ - box.minZ) * (face.getAxis() == Axis.Y ? v : u);
        double y = box.minY + (box.maxY - box.minY) * v;

        return switch (face) {
            case UP -> new Vec3(x, box.maxY, z);
            case DOWN -> new Vec3(x, box.minY, z);
            case NORTH -> new Vec3(x, y, box.minZ);
            case SOUTH -> new Vec3(x, y, box.maxZ);
            case WEST -> new Vec3(box.minX, y, z);
            case EAST -> new Vec3(box.maxX, y, z);
        };
    }

    static Aim.Shot side(Minecraft mc, BlockPos block, Direction face) {
        return side(mc, block, face, new double[][]{{0.5, 0.85}, {0.5, 0.5}, {0.5, 0.2}});
    }

    static Aim.Shot mine(Minecraft mc, BlockPos block, Direction face, double u, double v) {
        return side(mc, block, face, new double[][]{{u, v}, {0.5, 0.5}, {0.5, 0.85}, {0.5, 0.2}});
    }

    private static Aim.Shot side(Minecraft mc, BlockPos block, Direction face, double[][] spots) {
        LocalPlayer player = mc.player;
        if (player != null && mc.level != null) {
            VoxelShape shape = mc.level.getBlockState(block).getShape(mc.level, block);
            if (shape.isEmpty()) {
                return null;
            }

            AABB box = shape.bounds().move(block);
            Vec3 eye = player.getEyePosition();
            double reach = player.blockInteractionRange();
            if (!faces(box, face, eye)) {
                return null;
            }

            for (double[] spot : spots) {
                Vec3 point = facePoint(box, face, spot[0], spot[1]);
                if (!(point.distanceTo(eye) > reach)) {
                    float[] rot = PlacementUtil.calcRotation(eye, point);
                    BlockHitResult hit = trace(mc, player, rot[0], rot[1], reach);
                    if (hit != null && hit.getType() == Type.BLOCK && hit.getBlockPos().equals(block) && hit.getDirection() == face) {
                        return new Aim.Shot(hit, rot[0], rot[1]);
                    }
                }
            }

            return null;
        } else {
            return null;
        }
    }

    static Aim.Shot water(Minecraft mc, BlockPos pos) {
        LocalPlayer player = mc.player;
        if (player != null && mc.level != null) {
            Vec3 eye = player.getEyePosition();
            double reach = player.blockInteractionRange();
            Vec3 point = new Vec3(pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5);
            if (!(eye.y <= point.y) && !(point.distanceTo(eye) > reach)) {
                float[] rot = PlacementUtil.calcRotation(eye, point);
                BlockHitResult hit = trace(mc, player, rot[0], rot[1], reach, net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY);
                return hit != null && hit.getType() == Type.BLOCK && hit.getBlockPos().equals(pos) ? new Aim.Shot(hit, rot[0], rot[1]) : null;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    static boolean clickable(Minecraft mc, BlockPos pos) {
        return clickable(mc, pos, mc.level.getBlockState(pos));
    }

    private static boolean clickable(Minecraft mc, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        return mc.level.getBlockEntity(pos) != null
            || block instanceof DoorBlock
            || block instanceof TrapDoorBlock
            || block instanceof FenceGateBlock
            || block instanceof ButtonBlock
            || block instanceof LeverBlock
            || block instanceof DiodeBlock
            || block instanceof NoteBlock;
    }

    static void look(Minecraft mc, float yaw, float pitch) {
        LocalPlayer player = mc.player;
        if (player != null) {
            clearMouse(mc);
            motion = null;
            float current = player.getYRot();
            float step = mouseStep(mc);
            player.setYRot(current + mouse(Mth.wrapDegrees(yaw - current), step));
            player.setXRot(Mth.clamp(player.getXRot() + mouse(pitch - player.getXRot(), step), -90.0F, 90.0F));
        }
    }

    static float mouseStep(Minecraft mc) {
        double s = mc.options.sensitivity().get() * 0.6 + 0.2;
        return (float)(s * s * s * 8.0 * 0.15);
    }

    private static float mouse(float degrees, float step) {
        return step <= 0.0F ? degrees : Math.round(degrees / step) * step;
    }

    public static float turn(Minecraft mc, float yaw, float pitch, float maxStep) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return 0.0F;
        }

        clearMouse(mc);
        float yawNow = player.getYRot();
        float pitchNow = player.getXRot();
        float yawLeft = Mth.wrapDegrees(yaw - yawNow);
        float pitchLeft = pitch - pitchNow;
        float yawStep;
        float pitchStep;
        if (Math.abs(yawLeft) < 0.6F && Math.abs(pitchLeft) < 0.6F) {
            motion = null;
            yawStep = yawLeft;
            pitchStep = pitchLeft;
        } else {
            if (motion != null && !motion.done() && !motion.drifted(yaw, pitch)) {
                motion.retarget(yaw, pitch);
            } else {
                motion = Aim.Motion.start(yawNow, pitchNow, yaw, pitch, maxStep);
            }

            float[] want = motion.next();
            yawStep = Mth.clamp(Mth.wrapDegrees(want[0] - yawNow), -maxStep, maxStep);
            pitchStep = Mth.clamp(want[1] - pitchNow, -maxStep, maxStep);
        }

        float step = mouseStep(mc);
        player.setYRot(yawNow + mouse(yawStep, step));
        player.setXRot(Mth.clamp(pitchNow + mouse(pitchStep, step), -90.0F, 90.0F));
        return Math.abs(Mth.wrapDegrees(yaw - player.getYRot()));
    }

    private static void clearMouse(Minecraft mc) {
        if (mc.mouseHandler != null) {
            AccessorMouseHandler mouse = (AccessorMouseHandler)mc.mouseHandler;
            mouse.easyplace$setAccumulatedDX(0.0);
            mouse.easyplace$setAccumulatedDY(0.0);
        }
    }

    static boolean looking(LocalPlayer player, float yaw, float pitch) {
        return Math.abs(Mth.wrapDegrees(player.getYRot() - yaw)) < 0.5F && Math.abs(player.getXRot() - pitch) < 0.5F;
    }

    static BlockHitResult trace(Minecraft mc, LocalPlayer player, float yaw, float pitch, double reach) {
        return trace(mc, player, yaw, pitch, reach, net.minecraft.world.level.ClipContext.Fluid.NONE);
    }

    private static BlockHitResult trace(
        Minecraft mc, LocalPlayer player, float yaw, float pitch, double reach, net.minecraft.world.level.ClipContext.Fluid fluids
    ) {
        Vec3 eye = player.getEyePosition();
        double yawRad = Math.toRadians(yaw);
        double cosPitch = Math.cos(Math.toRadians(pitch));
        Vec3 direction = new Vec3(-Math.sin(yawRad) * cosPitch, -Math.sin(Math.toRadians(pitch)), Math.cos(yawRad) * cosPitch);
        return mc.level.clip(new ClipContext(eye, eye.add(direction.scale(reach)), net.minecraft.world.level.ClipContext.Block.OUTLINE, fluids, player));
    }

    private static boolean faces(AABB box, Direction face, Vec3 eye) {
        double gap = 0.02;

        return switch (face) {
            case UP -> eye.y > box.maxY + gap;
            case DOWN -> eye.y < box.minY - gap;
            case NORTH -> eye.z < box.minZ - gap;
            case SOUTH -> eye.z > box.maxZ + gap;
            case WEST -> eye.x < box.minX - gap;
            case EAST -> eye.x > box.maxX + gap;
        };
    }

    private static Vec3 faceCenter(AABB box, Direction face) {
        Vec3 center = box.getCenter();

        return switch (face) {
            case UP -> new Vec3(center.x, box.maxY, center.z);
            case DOWN -> new Vec3(center.x, box.minY, center.z);
            case NORTH -> new Vec3(center.x, center.y, box.minZ);
            case SOUTH -> new Vec3(center.x, center.y, box.maxZ);
            case WEST -> new Vec3(box.minX, center.y, center.z);
            case EAST -> new Vec3(box.maxX, center.y, center.z);
        };
    }

    @Environment(EnvType.CLIENT)
    private static final class Motion {
        private final float startYaw;
        private final float startPitch;
        private final float bend;
        private final int ticks;
        private float endYaw;
        private float endPitch;
        private float targetYaw;
        private float targetPitch;
        private int elapsed;

        private Motion(float startYaw, float startPitch, float endYaw, float endPitch, float targetYaw, float targetPitch, float bend, int ticks) {
            this.startYaw = startYaw;
            this.startPitch = startPitch;
            this.endYaw = endYaw;
            this.endPitch = endPitch;
            this.targetYaw = targetYaw;
            this.targetPitch = targetPitch;
            this.bend = bend;
            this.ticks = ticks;
        }

        static Aim.Motion start(float yaw0, float pitch0, float yaw, float pitch, float maxStep) {
            float dy = Mth.wrapDegrees(yaw - yaw0);
            float dp = pitch - pitch0;
            double distance = Math.hypot(dy, dp);
            double seconds = (0.07 + 0.055 * Math.log(1.0 + distance / 3.0) / Math.log(2.0)) * Math.exp(Aim.HAND.nextGaussian() * 0.22);
            int ticks = (int)Math.max(Math.ceil(distance * 1.9 / maxStep), Math.max(1L, Math.round(seconds * 20.0)));
            double miss = distance > 6.0 ? Aim.HAND.nextGaussian() * 0.045 : 0.0;
            double scatter = Math.min(1.2, distance * 0.015);
            float endYaw = (float)(yaw0 + dy * (1.0 + miss) + Aim.HAND.nextGaussian() * scatter);
            float endPitch = (float)(pitch0 + dp * (1.0 + miss) + Aim.HAND.nextGaussian() * scatter * 0.7);
            float bend = (float)(Aim.HAND.nextGaussian() * 0.06);
            return new Aim.Motion(yaw0, pitch0, endYaw, endPitch, yaw0 + dy, pitch, bend, ticks);
        }

        float[] next() {
            this.elapsed++;
            double t = Math.min(1.0, (double)this.elapsed / this.ticks);
            double s = t * t * t * (10.0 - 15.0 * t + 6.0 * t * t);
            double bulge = Math.sin(Math.PI * t) * this.bend;
            float dy = this.endYaw - this.startYaw;
            float dp = this.endPitch - this.startPitch;
            return new float[]{(float)(this.startYaw + dy * s - dp * bulge), (float)(this.startPitch + dp * s + dy * bulge)};
        }

        boolean done() {
            return this.elapsed >= this.ticks;
        }

        boolean drifted(float yaw, float pitch) {
            return Math.hypot(Mth.wrapDegrees(yaw - this.targetYaw), pitch - this.targetPitch) > 25.0;
        }

        void retarget(float yaw, float pitch) {
            float dy = Mth.wrapDegrees(yaw - this.targetYaw);
            float dp = pitch - this.targetPitch;
            this.endYaw += dy;
            this.endPitch += dp;
            this.targetYaw += dy;
            this.targetPitch = pitch;
        }
    }

    @Environment(EnvType.CLIENT)
    record Shot(BlockHitResult hit, float yaw, float pitch) {
    }
}
