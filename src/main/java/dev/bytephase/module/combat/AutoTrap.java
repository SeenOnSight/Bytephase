package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.BlockPlacementUtil;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Builds two-high obsidian walls and a roof around the nearest nearby player. */
public final class AutoTrap extends Module {
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private final NumberSetting targetRange = add(new NumberSetting("TargetRange", 4.0, 1.0, 8.0, 0.5));
    private final NumberSetting delay = add(new NumberSetting("Delay", 1.0, 0.0, 10.0, 1.0));
    private final NumberSetting blocksPerTick = add(new NumberSetting("BlocksPerTick", 1.0, 1.0, 5.0, 1.0));

    private int cooldown;

    public AutoTrap() {
        super("AutoTrap", Category.COMBAT, "Traps the nearest player in an obsidian enclosure");
    }

    @Override
    protected void onEnable() {
        cooldown = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || mc.player.isSpectator()) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        int slot = BlockPlacementUtil.findHotbarBlock(mc, Blocks.OBSIDIAN);
        if (slot < 0) return;
        double targetRangeSq = targetRange.getValue() * targetRange.getValue();
        PlayerEntity target = null;
        double nearestDistanceSq = Double.POSITIVE_INFINITY;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) continue;
            double distanceSq = player.squaredDistanceTo(mc.player);
            if (distanceSq <= targetRangeSq && distanceSq < nearestDistanceSq) {
                target = player;
                nearestDistanceSq = distanceSq;
            }
        }
        if (target == null) return;

        BlockPos feet = target.getBlockPos();
        // Build the lower wall first, then the upper wall, then cap the cage.
        int placed = 0;
        int limit = blocksPerTick.asInt();
        for (int layer = 0; layer < 2 && placed < limit; layer++) {
            for (Direction side : SIDES) {
                BlockPos pos = feet.offset(side).up(layer);
                if (mc.world.getBlockState(pos).isReplaceable()
                        && BlockPlacementUtil.place(mc, pos, slot, true, true)) {
                    placed++;
                    if (placed >= limit) break;
                }
            }
        }
        if (placed < limit) {
            BlockPos roof = feet.up(2);
            if (mc.world.getBlockState(roof).isReplaceable()
                    && BlockPlacementUtil.place(mc, roof, slot, true, true)) {
                placed++;
            }
        }
        if (placed > 0) cooldown = delay.asInt();
    }
}
