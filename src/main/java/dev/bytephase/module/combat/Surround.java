package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.BlockPlacementUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Places one layer of obsidian around the player's feet. */
public final class Surround extends Module {
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private final NumberSetting delay = add(new NumberSetting("Delay", 0.0, 0.0, 10.0, 1.0));
    private final NumberSetting blocksPerTick = add(new NumberSetting("BlocksPerTick", 1.0, 1.0, 4.0, 1.0));
    private final BooleanSetting swapBack = add(new BooleanSetting("SwapBack", true));
    private final BooleanSetting keyActivate = add(new BooleanSetting("KeyActivate", false));

    private int cooldown;
    private BlockPos activationFeet;

    public Surround() {
        super("Surround", Category.COMBAT, "Places a one-block-high obsidian ring around your feet");
    }

    @Override
    protected void onEnable() {
        cooldown = 0;
        activationFeet = null;
    }

    @Override
    protected void onDisable() {
        activationFeet = null;
    }

    /** Returns true when the configured module key was handled as an activation. */
    public boolean activateFromKey() {
        if (!keyActivate.getValue()) return false;
        var mc = BytePhase.mc;
        if (isEnabled() && mc != null && mc.player != null && mc.world != null
                && !mc.player.isSpectator() && mc.player.isOnGround()) {
            activationFeet = mc.player.getBlockPos().toImmutable();
            cooldown = 0;
        }
        return true;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (!keyActivate.getValue()) activationFeet = null;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || mc.player.isSpectator() || !mc.player.isOnGround()) {
            activationFeet = null;
            return;
        }
        if (keyActivate.getValue() && (activationFeet == null
                || !activationFeet.equals(mc.player.getBlockPos()))) {
            activationFeet = null;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        int slot = BlockPlacementUtil.findHotbarBlock(mc, Blocks.OBSIDIAN);
        if (slot < 0) {
            activationFeet = null;
            return;
        }

        BlockPos feet = mc.player.getBlockPos();
        List<BlockPos> missing = new ArrayList<>(4);
        for (Direction side : SIDES) {
            BlockPos pos = feet.offset(side);
            if (mc.world.getBlockState(pos).isReplaceable()) missing.add(pos);
        }
        int placed = 0;
        for (BlockPos pos : missing) {
            if (placed >= blocksPerTick.asInt()) break;
            if (BlockPlacementUtil.place(mc, pos, slot, swapBack.getValue(), true)) placed++;
        }
        if (placed > 0) cooldown = delay.asInt();
        else activationFeet = null;
    }
}
