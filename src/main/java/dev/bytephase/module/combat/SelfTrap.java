package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.BlockPlacementUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Encloses the player in obsidian: a ring at foot level, a ring at head level
 * and a roof above.
 *
 * <p>Unlike {@link Surround}, which only builds the foot-level ring, this keeps
 * the head covered so the player cannot be hit over the top of a one-high
 * surround. Placements are ordered from the ground up because a lower block is
 * a usable support face for the one above it.</p>
 */
public final class SelfTrap extends Module {
    private static final Direction[] SIDES = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private final NumberSetting blocksPerTick = add(new NumberSetting("BlocksPerTick", 1.0, 1.0, 5.0, 1.0));
    private final NumberSetting delay = add(new NumberSetting("Delay", 0.0, 0.0, 10.0, 1.0));

    private int cooldown;

    public SelfTrap() {
        super("SelfTrap", Category.COMBAT, "Traps your whole body in obsidian");
    }

    @Override
    protected void onEnable() {
        cooldown = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || mc.player.isSpectator()) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        int slot = BlockPlacementUtil.findHotbarBlock(mc, Blocks.OBSIDIAN);
        if (slot < 0) {
            return;
        }

        BlockPos feet = mc.player.getBlockPos();
        List<BlockPos> missing = new ArrayList<>(9);
        for (int layer = 0; layer <= 1; layer++) {
            for (Direction side : SIDES) {
                BlockPos pos = feet.offset(side).up(layer);
                if (mc.world.getBlockState(pos).isReplaceable()) {
                    missing.add(pos);
                }
            }
        }
        BlockPos roof = feet.up(2);
        if (mc.world.getBlockState(roof).isReplaceable()) {
            missing.add(roof);
        }

        int placed = 0;
        int limit = blocksPerTick.asInt();
        for (BlockPos pos : missing) {
            if (placed >= limit) {
                break;
            }
            if (BlockPlacementUtil.place(mc, pos, slot, true, true)) {
                placed++;
            }
        }
        if (placed > 0) {
            cooldown = delay.asInt();
        }
    }
}
