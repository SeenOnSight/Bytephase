package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.BlockPlacementUtil;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * One-shot protection: closes one open cell next to the player and turns
 * itself off once the block is confirmed.
 *
 * <p>The player's own cell cannot be filled — vanilla rejects a block that
 * overlaps a living entity — so this fills the closest cell of the foot ring
 * instead, which is what actually cuts a crystal's line of effect. A placement
 * that the server does not confirm is retried up to {@code Attempts} times
 * rather than being assumed to have worked; the module disables once a block
 * appears or the attempts run out. {@link FeetTrap} is the continuous,
 * ring-wide version.</p>
 */
public final class SelfFill extends Module {
    private static final Direction[] SIDES = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private final ModeSetting block = add(new ModeSetting("Block",
            new String[]{"Obsidian", "AnyBlock"}, "Obsidian"));
    private final NumberSetting attempts = add(new NumberSetting("Attempts", 10.0, 1.0, 40.0, 1.0));
    private final NumberSetting delay = add(new NumberSetting("Delay", 1.0, 0.0, 10.0, 1.0));
    private final BooleanSetting swapBack = add(new BooleanSetting("SwapBack", true));

    private World activeWorld;
    private BlockPos awaiting;
    private int waitTicks;
    private int tries;
    private int cooldown;

    public SelfFill() {
        super("SelfFill", Category.COMBAT, "Places one protective block next to you, then disables");
    }

    @Override
    protected void onEnable() {
        activeWorld = BytePhase.mc == null ? null : BytePhase.mc.world;
        awaiting = null;
        waitTicks = 0;
        tries = 0;
        cooldown = 0;
    }

    @Override
    protected void onDisable() {
        activeWorld = null;
        awaiting = null;
        waitTicks = 0;
        tries = 0;
        cooldown = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || mc.player.isSpectator()) {
            return;
        }
        if (mc.world != activeWorld) {
            activeWorld = mc.world;
            awaiting = null;
            return;
        }
        if (awaiting != null) {
            if (!mc.world.getBlockState(awaiting).isReplaceable()) {
                setEnabled(false);
                return;
            }
            if (waitTicks > 0) {
                waitTicks--;
                return;
            }
            awaiting = null;
        }
        if (tries >= attempts.asInt()) {
            setEnabled(false);
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        int slot = block.getValue().equals("AnyBlock")
                ? BlockPlacementUtil.findAnyHotbarBlock(mc)
                : BlockPlacementUtil.findHotbarBlock(mc, Blocks.OBSIDIAN);
        if (slot < 0) {
            return;
        }
        BlockPos feet = mc.player.getBlockPos();
        for (Direction side : SIDES) {
            BlockPos pos = feet.offset(side);
            if (!mc.world.getBlockState(pos).isReplaceable()) {
                continue;
            }
            if (BlockPlacementUtil.place(mc, pos, slot, swapBack.getValue(), true)) {
                awaiting = pos.toImmutable();
                waitTicks = 3;
                tries++;
                cooldown = delay.asInt();
                return;
            }
        }
    }
}
