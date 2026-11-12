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
import net.minecraft.world.World;

/**
 * Seals the four cells beside the player's feet.
 *
 * <p>Vanilla refuses a block that overlaps a living entity, so the player's own
 * cell cannot be filled; the ring is built around the feet instead, which is
 * what interrupts a crystal's line of effect. Unlike {@link Surround}, which
 * trickles blocks in at its own rate, this fills every open side in one burst
 * and then goes idle until the player stands on a new block.</p>
 */
public final class FeetTrap extends Module {
    private static final Direction[] SIDES = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private final NumberSetting blocksPerTick = add(new NumberSetting("BlocksPerTick", 4.0, 1.0, 4.0, 1.0));
    private final NumberSetting delay = add(new NumberSetting("Delay", 1.0, 0.0, 10.0, 1.0));

    private BlockPos armed;
    private World activeWorld;
    private int cooldown;

    public FeetTrap() {
        super("FeetTrap", Category.COMBAT, "Seals the blocks around your feet against explosion damage");
    }

    @Override
    protected void onEnable() {
        armed = null;
        cooldown = 0;
        activeWorld = BytePhase.mc == null ? null : BytePhase.mc.world;
    }

    @Override
    protected void onDisable() {
        armed = null;
        activeWorld = null;
        cooldown = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || mc.player.isSpectator() || !mc.player.isOnGround()) {
            armed = null;
            return;
        }
        if (mc.world != activeWorld) {
            armed = null;
            activeWorld = mc.world;
            return;
        }
        BlockPos feet = mc.player.getBlockPos();
        if (armed != null && !armed.equals(feet)) {
            armed = null;
        }
        if (armed != null) {
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

        List<BlockPos> missing = new ArrayList<>(4);
        for (Direction side : SIDES) {
            BlockPos pos = feet.offset(side);
            if (mc.world.getBlockState(pos).isReplaceable()) {
                missing.add(pos);
            }
        }
        if (missing.isEmpty()) {
            armed = feet.toImmutable();
            return;
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
        if (placed >= missing.size()) {
            armed = feet.toImmutable();
        }
    }
}
