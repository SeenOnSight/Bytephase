package dev.bytephase.pvp.module.impl.combat.bot;

import java.util.List;
import dev.bytephase.pvp.util.pvp.Rotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

@Environment(EnvType.CLIENT)
final class PearlFlight {
    private static final double SPEED = 1.5;
    private static final double GRAVITY = 0.03;
    private static final double DRAG = 0.99;
    private static final double WATER_DRAG = 0.8;
    private static final int MAX_TICKS = 100;
    private static final int MAX_DROP = 3;
    private static final double EDGE_MARGIN = 0.8;

    private PearlFlight() {
    }

    static PearlFlight.Landing land(LocalPlayer self, float yaw, float pitch) {
        Vec3 start = self.getEyePosition().subtract(0.0, 0.1, 0.0);
        Vec3 motion = self.getKnownMovement();
        Vec3 velocity = Vec3.directionFromRotation(pitch, yaw).scale(1.5).add(motion.x, self.onGround() ? 0.0 : motion.y, motion.z);
        return fly(self.level(), self, start, velocity);
    }

    static PearlFlight.Landing fly(Level level, Entity ignored, Vec3 from, Vec3 velocity) {
        Vec3 at = from;
        Vec3 v = velocity;

        for (int tick = 1; tick <= 100; tick++) {
            BlockPos cell = BlockPos.containing(at);
            if (!level.getChunkSource().hasChunk(cell.getX() >> 4, cell.getZ() >> 4) || at.y < level.getMinY()) {
                return null;
            }

            boolean water = level.getFluidState(cell).is(FluidTags.WATER);
            v = new Vec3(v.x, v.y - 0.03, v.z).scale(water ? 0.8 : 0.99);
            Vec3 next = at.add(v);
            BlockHitResult hit = level.clip(new ClipContext(at, next, Block.COLLIDER, Fluid.NONE, ignored));
            if (hit.getType() != Type.MISS) {
                Vec3 landed = hit.getLocation();
                BlockPos floor = floorUnder(level, landed);
                return floor == null ? null : new PearlFlight.Landing(landed, floor, tick);
            }

            at = next;
        }

        return null;
    }

    static Rotation escape(LocalPlayer self, List<? extends Entity> enemies, double minAway) {
        Vec3 middle = Vec3.ZERO;

        for (Entity enemy : enemies) {
            middle = middle.add(enemy.position());
        }

        middle = middle.scale(1.0 / Math.max(1, enemies.size()));
        float away = (float)Math.toDegrees(Math.atan2(-(self.getX() - middle.x), self.getZ() - middle.z));
        Rotation best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int k = -4; k <= 4; k++) {
            float yaw = Mth.wrapDegrees(away + k * 22.5F);

            for (float pitch = -45.0F; pitch <= 5.0F; pitch += 7.0F) {
                PearlFlight.Landing landing = land(self, yaw, pitch);
                if (landing != null && safe(self.level(), landing)) {
                    double nearest = nearest(enemies, landing.at());
                    if (!(nearest < minAway)) {
                        double score = nearest - 0.5 * (landing.at().y - landing.floor().getY() - 1.0) - 0.05 * landing.ticks();
                        if (score > bestScore) {
                            bestScore = score;
                            best = new Rotation(yaw, pitch);
                        }
                    }
                }
            }
        }

        return best;
    }

    static Rotation toward(LocalPlayer self, Vec3 spot, double near, double far) {
        Vec3 eye = self.getEyePosition();
        double straight = Math.hypot(spot.x - eye.x, spot.z - eye.z);
        float yaw0 = (float)Math.toDegrees(Math.atan2(-(spot.x - eye.x), spot.z - eye.z));
        double middle = (near + far) / 2.0;

        for (boolean lob : new boolean[]{false, true}) {
            for (double past : lob ? new double[]{0.0, middle, -middle} : new double[]{middle, -middle}) {
                double wanted = straight + past;
                if (!(wanted < 1.0)) {
                    for (float off : new float[]{0.0F, -1.5F, 1.5F, -3.0F, 3.0F}) {
                        Rotation found = pitchFor(self, eye, Mth.wrapDegrees(yaw0 + off), wanted, lob);
                        if (found != null && reaches(self, found.yaw(), found.pitch(), spot, lob ? 0.0 : near, far)) {
                            return found;
                        }
                    }
                }
            }
        }

        return null;
    }

    private static Rotation pitchFor(LocalPlayer self, Vec3 eye, float yaw, double wanted, boolean lob) {
        float shortOf = Float.NaN;
        float from = lob ? -88.0F : 15.0F;
        float to = lob ? -40.0F : -60.0F;
        float step = lob ? 4.0F : -5.0F;

        for (float pitch = from; lob ? pitch <= to : pitch >= to; pitch += step) {
            if (!(carry(self, eye, yaw, pitch) < wanted)) {
                if (Float.isNaN(shortOf)) {
                    return null;
                }

                float low = shortOf;
                float high = pitch;

                for (int i = 0; i < 8; i++) {
                    float mid = (low + high) / 2.0F;
                    if (carry(self, eye, yaw, mid) >= wanted) {
                        high = mid;
                    } else {
                        low = mid;
                    }
                }

                return new Rotation(yaw, (low + high) / 2.0F);
            }

            shortOf = pitch;
        }

        return null;
    }

    private static double carry(LocalPlayer self, Vec3 eye, float yaw, float pitch) {
        PearlFlight.Landing landing = land(self, yaw, pitch);
        return landing == null ? Double.MAX_VALUE : Math.hypot(landing.at().x - eye.x, landing.at().z - eye.z);
    }

    static boolean escapes(LocalPlayer self, float yaw, float pitch, List<? extends Entity> enemies, double minAway) {
        PearlFlight.Landing landing = land(self, yaw, pitch);
        return landing != null && safe(self.level(), landing) && nearest(enemies, landing.at()) >= minAway;
    }

    static boolean reaches(LocalPlayer self, float yaw, float pitch, Vec3 spot, double near, double far) {
        PearlFlight.Landing landing = land(self, yaw, pitch);
        if (landing != null && safe(self.level(), landing)) {
            double distance = horizontal(landing.at(), spot);
            return distance >= near && distance <= far && Math.abs(landing.at().y - spot.y) <= 3.0;
        } else {
            return false;
        }
    }

    static int flightTicks(double distance) {
        if (distance <= 6.0) {
            return 5;
        } else if (distance <= 10.0) {
            return 5 + (int)Math.round((distance - 6.0) / 2.0);
        } else if (distance <= 14.0) {
            return 7 + (int)Math.round((distance - 10.0) * 0.75);
        } else {
            return distance <= 20.0 ? 10 + (int)Math.round((distance - 14.0) * 0.83) : 15 + (int)Math.round((distance - 20.0) * 0.8);
        }
    }

    static double nearest(List<? extends Entity> others, Vec3 at) {
        double nearest = Double.MAX_VALUE;

        for (Entity other : others) {
            nearest = Math.min(nearest, other.position().distanceTo(at));
        }

        return nearest;
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static BlockPos floorUnder(Level level, Vec3 landed) {
        BlockPos at = BlockPos.containing(landed.x, landed.y + 0.05, landed.z);

        for (int down = 0; down <= 4; down++) {
            BlockPos pos = at.below(down);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return pos;
            }
        }

        return null;
    }

    private static boolean safe(Level level, PearlFlight.Landing landing) {
        BlockPos floor = landing.floor();
        if (landing.at().y - (floor.getY() + 1.0) > 3.0) {
            return false;
        }

        for (double[] off : new double[][]{{0.8, 0.0}, {-0.8, 0.0}, {0.0, 0.8}, {0.0, -0.8}}) {
            BlockPos beside = floorUnder(level, landing.at().add(off[0], 0.0, off[1]));
            if (beside == null || beside.getY() != floor.getY()) {
                return false;
            }
        }

        if (!harmful(level.getBlockState(floor)) && !level.getBlockState(floor).is(Blocks.MAGMA_BLOCK)) {
            for (int up = 1; up <= 2; up++) {
                BlockPos pos = floor.above(up);
                BlockState state = level.getBlockState(pos);
                if (harmful(state) || !state.getCollisionShape(level, pos).isEmpty() || !level.getFluidState(pos).isEmpty()) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private static boolean harmful(BlockState state) {
        return state.is(Blocks.LAVA)
            || state.is(BlockTags.FIRE)
            || state.is(Blocks.CACTUS)
            || state.is(Blocks.SWEET_BERRY_BUSH)
            || state.is(Blocks.COBWEB)
            || state.is(Blocks.POWDER_SNOW)
            || state.is(Blocks.WITHER_ROSE);
    }

    @Environment(EnvType.CLIENT)
    record Landing(Vec3 at, BlockPos floor, int ticks) {
    }
}
