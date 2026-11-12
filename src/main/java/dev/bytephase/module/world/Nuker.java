package dev.bytephase.module.world;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.module.combat.AutoMine;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.BlockReachUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class Nuker extends Module {
    private final NumberSetting radius = add(new NumberSetting("Radius", 4, 2, 6, 1));
    private final BooleanSetting ignoreBedrock = add(new BooleanSetting("IgnoreBedrock", true));
    private BlockPos target;
    private Direction side = Direction.UP;
    private World lastWorld;

    public Nuker() {
        super("Nuker", Category.WORLD, "Mines nearby blocks automatically");
    }

    @Override
    public void onDisable() {
        cancelMining();
        lastWorld = null;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null) {
            cancelMining();
            return;
        }
        if (mc.world != lastWorld) {
            // A mining target from the previous dimension must not be reused
            // at the same coordinates in a different world.
            target = null;
            lastWorld = mc.world;
            return;
        }
        AutoMine autoMine = BytePhase.modules == null ? null : BytePhase.modules.get(AutoMine.class);
        if (autoMine != null && autoMine.isEnabled()) {
            // AutoMine has priority over area mining. Do not call
            // cancelBlockBreaking here: AutoMine may already own the shared
            // interaction-manager state earlier in this client tick.
            cancelPacketMineTarget();
            target = null;
            return;
        }
        if (mc.player.isSpectator() || mc.currentScreen != null) {
            cancelMining();
            return;
        }
        // Keep mining the current block while it is still there and in reach.
        // Rescanning ~1500 cells every tick was wasteful, and re-picking a
        // different "closest" block mid-break throws away vanilla's progress.
        BlockPos next = stillValid(target) ? target : findBlock();
        if (next == null) {
            cancelMining();
            return;
        }
        if (target == null || !target.equals(next)) {
            target = next;
            mc.interactionManager.attackBlock(target, side);
        }
        mc.interactionManager.updateBlockBreakingProgress(target, side);
    }

    private void cancelMining() {
        if (target != null) {
            var mc = BytePhase.mc;
            cancelPacketMineTarget();
            if (mc != null && mc.interactionManager != null) {
                mc.interactionManager.cancelBlockBreaking();
            }
            target = null;
        }
    }

    private void cancelPacketMineTarget() {
        if (target == null || BytePhase.modules == null) {
            return;
        }
        PacketMine packetMine = BytePhase.modules.get(PacketMine.class);
        if (packetMine != null && packetMine.isEnabled()) {
            packetMine.cancel(target);
        }
    }

    private boolean stillValid(BlockPos pos) {
        var mc = BytePhase.mc;
        if (pos == null) {
            return false;
        }
        BlockState state = mc.world.getBlockState(pos);
        return !state.isAir() && state.getFluidState().isEmpty()
                && !state.getCollisionShape(mc.world, pos).isEmpty()
                && state.getHardness(mc.world, pos) >= 0
                && !(ignoreBedrock.getValue() && state.getBlock() == Blocks.BEDROCK)
                && BlockReachUtil.isWithinReach(mc, pos, mc.player.getBlockInteractionRange());
    }

    private BlockPos findBlock() {
        var mc = BytePhase.mc;
        BlockPos center = mc.player.getBlockPos();
        int r = radius.asInt();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        double reach = mc.player.getBlockInteractionRange();
        for (BlockPos pos : BlockPos.iterateOutwards(center, r, 4, r)) {
            BlockState state = mc.world.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            if (state.getCollisionShape(mc.world, pos).isEmpty()) {
                continue;
            }
            if (state.getHardness(mc.world, pos) < 0) {
                continue;
            }
            if (ignoreBedrock.getValue() && state.getBlock() == Blocks.BEDROCK) {
                continue;
            }
            if (!BlockReachUtil.isWithinReach(mc, pos, reach)) {
                continue;
            }
            if (best == null || pos.getSquaredDistance(center) < bestDist) {
                bestDist = pos.getSquaredDistance(center);
                best = pos.toImmutable();
            }
        }
        return best;
    }
}
