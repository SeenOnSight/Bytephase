package dev.bytephase.pvp.module.impl.combat.bot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
final class StandPicker {
    private static final float BLAST_PROOF = 600.0F;
    private static final double[] RADII = new double[]{1.5, 3.0, 4.0};
    private static final int HOLE_SEARCH = 4;
    private static final int MAX_HOLES = 6;

    private StandPicker() {
    }

    static StandPicker.Spot pick(Level level, Player self, Player enemy, double min, double ideal, double max, double cover) {
        BlockPos here = self.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();
        candidates.add(here);

        for (double radius : RADII) {
            for (int i = 0; i < 8; i++) {
                double angle = (Math.PI / 4) * i;
                candidates.add(BlockPos.containing(self.getX() + Math.cos(angle) * radius, self.getY(), self.getZ() + Math.sin(angle) * radius));
            }
        }

        candidates.addAll(holesNear(level, here));
        StandPicker.Spot best = null;
        double stay = Double.NEGATIVE_INFINITY;

        for (BlockPos raw : candidates) {
            BlockPos feet = standable(level, raw);
            if (feet != null) {
                double score = score(level, self, enemy, feet, min, ideal, max, cover);
                if (feet.equals(here)) {
                    stay = Math.max(stay, score);
                }

                if (best == null || score > best.score()) {
                    best = new StandPicker.Spot(feet, score);
                }
            }
        }

        return best != null && !best.feet().equals(here) && !(best.score() < stay + 0.5) ? best : null;
    }

    static double hole(Level level, BlockPos feet) {
        int closed = 0;

        for (Direction side : Plane.HORIZONTAL) {
            if (level.getBlockState(feet.relative(side)).getBlock().getExplosionResistance() >= 600.0F) {
                closed++;
            }
        }

        return closed == 4 ? 1.0 : (closed == 3 ? 0.4 : 0.0);
    }

    private static double score(Level level, Player self, Player enemy, BlockPos feet, double min, double ideal, double max, double cover) {
        Vec3 at = Vec3.atBottomCenterOf(feet);
        double distance = Math.hypot(enemy.getX() - at.x, enemy.getZ() - at.z);
        if (!(distance < min - 0.5) && !(distance > max + 1.5)) {
            double travel = Math.hypot(self.getX() - at.x, self.getZ() - at.z);
            return -Math.abs(distance - ideal) + 3.0 * cover * hole(level, feet) - 0.15 * travel;
        } else {
            return -10.0 - Math.abs(distance - ideal);
        }
    }

    static BlockPos standable(Level level, BlockPos pos) {
        for (int dy : new int[]{0, -1, 1}) {
            BlockPos feet = pos.above(dy);
            BlockPos floor = feet.below();
            BlockState under = level.getBlockState(floor);
            if (!under.getCollisionShape(level, floor).isEmpty()
                && !harmful(under)
                && !under.is(Blocks.MAGMA_BLOCK)
                && clear(level, feet)
                && clear(level, feet.above())) {
                return feet;
            }
        }

        return null;
    }

    private static boolean clear(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && !harmful(state) && level.getFluidState(pos).isEmpty();
    }

    private static boolean harmful(BlockState state) {
        return state.is(Blocks.LAVA)
            || state.is(BlockTags.FIRE)
            || state.is(Blocks.CACTUS)
            || state.is(Blocks.SWEET_BERRY_BUSH)
            || state.is(Blocks.COBWEB)
            || state.is(Blocks.POWDER_SNOW);
    }

    private static List<BlockPos> holesNear(Level level, BlockPos here) {
        List<BlockPos> holes = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(here.offset(-4, -1, -4), here.offset(4, 1, 4))) {
            if (hole(level, pos) >= 1.0 && standable(level, pos) != null && standable(level, pos).equals(pos)) {
                holes.add(pos.immutable());
            }
        }

        holes.sort(Comparator.comparingDouble(posx -> posx.distSqr(here)));
        return holes.size() > 6 ? holes.subList(0, 6) : holes;
    }

    @Environment(EnvType.CLIENT)
    record Spot(BlockPos feet, double score) {
        Vec3 center() {
            return Vec3.atBottomCenterOf(this.feet);
        }
    }
}
