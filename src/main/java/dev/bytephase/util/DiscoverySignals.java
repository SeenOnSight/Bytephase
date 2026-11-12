package dev.bytephase.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/** Loaded block-state heuristics. A signal is evidence of growth or placement, not proof of a base. */
public final class DiscoverySignals {
    public record Options(boolean kelp, boolean caveVines, boolean vines, boolean bamboo,
                          boolean beeNests, boolean cocoa, boolean rotatedDeepslate) {}

    private DiscoverySignals() {}

    public static int growthWeight(BlockState state) {
        if (state.isOf(Blocks.AMETHYST_CLUSTER)) return 4;
        if (state.isOf(Blocks.LARGE_AMETHYST_BUD)) return 3;
        if (state.isOf(Blocks.MEDIUM_AMETHYST_BUD)) return 2;
        return state.isOf(Blocks.SMALL_AMETHYST_BUD) ? 1 : 0;
    }

    public static boolean enabled(BlockState state, Options options) {
        return options.kelp && state.isOf(Blocks.KELP)
                || options.caveVines && state.isOf(Blocks.CAVE_VINES)
                || options.vines && state.isOf(Blocks.VINE)
                || options.bamboo && state.isOf(Blocks.BAMBOO)
                || options.beeNests && state.isOf(Blocks.BEE_NEST)
                || options.cocoa && state.isOf(Blocks.COCOA)
                || options.rotatedDeepslate && state.isOf(Blocks.DEEPSLATE);
    }

    public static int activity(BlockState state, BlockState above, BlockState below, Options options) {
        if (options.kelp && state.isOf(Blocks.KELP)) {
            return state.get(Properties.AGE_25) < 25 && above != null && above.isOf(Blocks.WATER) ? 1 : 0;
        }
        if (options.caveVines && state.isOf(Blocks.CAVE_VINES)) {
            return state.get(Properties.AGE_25) < 25 && below != null && below.isAir() ? 1 : 0;
        }
        if (options.vines && state.isOf(Blocks.VINE)) {
            return below != null && !below.isOf(Blocks.VINE) ? 1 : 0;
        }
        if (options.bamboo && state.isOf(Blocks.BAMBOO)) {
            return state.get(Properties.STAGE) == 1 && above != null && !above.isOf(Blocks.BAMBOO) ? 1 : 0;
        }
        if (options.beeNests && state.isOf(Blocks.BEE_NEST)) {
            return state.get(Properties.HONEY_LEVEL) == 5 ? 1 : 0;
        }
        if (options.cocoa && state.isOf(Blocks.COCOA)) {
            return state.get(Properties.AGE_2) == 2 ? 1 : 0;
        }
        if (options.rotatedDeepslate && state.isOf(Blocks.DEEPSLATE)) {
            return state.get(Properties.AXIS) != Direction.Axis.Y ? 1 : 0;
        }
        return 0;
    }
}
