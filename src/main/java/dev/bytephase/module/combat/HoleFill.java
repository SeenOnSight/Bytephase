package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.BlockPlacementUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Fills crystal-PvP holes near the player so an opponent cannot take them.
 *
 * <p>A hole is a two-block air column with a solid floor and a fully solid ring
 * at both levels. The scan is bounded to a small radius around the player and
 * runs at most once per cooldown; the nearest holes are filled first, one
 * placement attempt per hole so a rejected placement does not starve the
 * rest.</p>
 */
public final class HoleFill extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", 4.0, 2.0, 8.0, 0.5));
    private final NumberSetting blocksPerTick = add(new NumberSetting("BlocksPerTick", 1.0, 1.0, 4.0, 1.0));
    private final NumberSetting delay = add(new NumberSetting("Delay", 1.0, 0.0, 10.0, 1.0));
    private final BooleanSetting swapBack = add(new BooleanSetting("SwapBack", true));

    private int cooldown;
    private World activeWorld;
    private final List<BlockPos> holes = new ArrayList<>();
    private int scanCooldown;

    public HoleFill() {
        super("HoleFill", Category.COMBAT, "Fills nearby crystal holes with blocks");
    }

    @Override
    protected void onEnable() {
        cooldown = 0;
        scanCooldown = 0;
        holes.clear();
        activeWorld = BytePhase.mc == null ? null : BytePhase.mc.world;
    }

    @Override
    protected void onDisable() {
        holes.clear();
        activeWorld = null;
        cooldown = 0;
        scanCooldown = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || mc.player.isSpectator()) {
            holes.clear();
            return;
        }
        if (mc.world != activeWorld) {
            holes.clear();
            activeWorld = mc.world;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        int slot = BlockPlacementUtil.findAnyHotbarBlock(mc);
        if (slot < 0) {
            return;
        }

        if (--scanCooldown <= 0) {
            rescan(mc.player.getBlockPos());
            scanCooldown = 10;
        }

        int placed = 0;
        int limit = blocksPerTick.asInt();
        for (int i = holes.size() - 1; i >= 0 && placed < limit; i--) {
            BlockPos pos = holes.get(i);
            if (!isHole(pos)) {
                // Something already filled the hole (or it changed); drop the
                // stale entry instead of retrying it every pass.
                holes.remove(i);
                continue;
            }
            if (BlockPlacementUtil.place(mc, pos, slot, swapBack.getValue(), true)) {
                placed++;
                holes.remove(i);
            }
        }
        if (placed > 0) {
            cooldown = delay.asInt();
        }
    }

    private void rescan(BlockPos center) {
        holes.clear();
        int r = (int) Math.ceil(range.getValue());
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (pos.getSquaredDistance(center) > range.getValue() * range.getValue()) {
                        continue;
                    }
                    if (isHole(pos)) {
                        holes.add(pos.toImmutable());
                    }
                }
            }
        }
        // Nearest first: the list is consumed from the end.
        holes.sort(Comparator.comparingDouble(pos -> -pos.getSquaredDistance(center)));
    }

    private boolean isHole(BlockPos pos) {
        var mc = BytePhase.mc;
        if (mc == null || mc.world == null) {
            return false;
        }
        if (!mc.world.getBlockState(pos).isReplaceable()
                || !mc.world.getBlockState(pos.up()).isReplaceable()
                || !isSolid(pos.down())) {
            return false;
        }
        return isSolid(pos.north()) && isSolid(pos.south())
                && isSolid(pos.east()) && isSolid(pos.west())
                && isSolid(pos.north().up()) && isSolid(pos.south().up())
                && isSolid(pos.east().up()) && isSolid(pos.west().up());
    }

    private boolean isSolid(BlockPos pos) {
        var mc = BytePhase.mc;
        if (mc == null || mc.world == null) {
            return false;
        }
        BlockState state = mc.world.getBlockState(pos);
        return !state.getCollisionShape(mc.world, pos).isEmpty();
    }
}
