package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.module.world.PacketMine;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.BlockReachUtil;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Mines the closest breakable block around a nearby player's feet. */
public final class AutoMine extends Module {
    private final NumberSetting targetRange = add(new NumberSetting("TargetRange", 5.0, 1.0, 8.0, 0.5));
    private final BooleanSetting pauseUsing = add(new BooleanSetting("PauseUsingItem", true));
    private final BooleanSetting obsidianOnly = add(new BooleanSetting("ObsidianOnly", true));

    private BlockPos targetBlock;
    private PlayerEntity targetPlayer;
    private World activeWorld;

    public AutoMine() {
        super("AutoMine", Category.COMBAT, "Mines blocks around nearby players' feet, skipping bedrock");
    }

    @Override
    protected void onEnable() {
        activeWorld = BytePhase.mc == null ? null : BytePhase.mc.world;
    }

    @Override
    protected void onDisable() {
        cancelMining();
        activeWorld = null;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || mc.player.isSpectator()
                || pauseUsing.getValue() && mc.player.isUsingItem()) {
            cancelMining();
            activeWorld = mc == null ? null : mc.world;
            return;
        }
        if (activeWorld != mc.world) {
            // PacketMine ticks after AutoMine. Drop stale ownership here so
            // cancelMining cannot send an abort for old coordinates into the
            // newly joined world before PacketMine resets its own state.
            targetBlock = null;
            targetPlayer = null;
            activeWorld = mc.world;
            return;
        }

        double rangeSq = targetRange.getValue() * targetRange.getValue();
        PlayerEntity nearest = null;
        BlockPos best = null;
        double nearestDistanceSq = Double.POSITIVE_INFINITY;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) continue;
            double distanceSq = player.squaredDistanceTo(mc.player);
            if (distanceSq <= rangeSq && distanceSq < nearestDistanceSq) {
                BlockPos candidate = findBlock(player.getBlockPos());
                if (candidate != null) {
                    nearest = player;
                    best = candidate;
                    nearestDistanceSq = distanceSq;
                }
            }
        }
        if (nearest == null) {
            cancelMining();
            return;
        }

        boolean changedTarget = !best.equals(targetBlock) || nearest != targetPlayer;
        if (changedTarget) {
            cancelMining();
            targetBlock = best.toImmutable();
            targetPlayer = nearest;
        }
        PacketMine packetMine = BytePhase.modules == null ? null : BytePhase.modules.get(PacketMine.class);
        if (packetMine != null && packetMine.isEnabled() && !packetMine.isMining(targetBlock)) {
            // A stale or invalid target can be rejected by PacketMine's
            // vanilla restriction checks. Retry acquisition instead of
            // leaving AutoMine stuck on a block it does not own.
            mc.interactionManager.attackBlock(targetBlock, Direction.UP);
            if (!packetMine.isMining(targetBlock)) {
                return;
            }
        } else if (packetMine == null || !packetMine.isEnabled()) {
            // Without PacketMine, use the normal interaction-manager path.
            // Only restart vanilla progress after selecting a new target.
            if (changedTarget && !mc.interactionManager.attackBlock(targetBlock, Direction.UP)) {
                targetBlock = null;
                targetPlayer = null;
                return;
            }
        }
        mc.interactionManager.updateBlockBreakingProgress(targetBlock, Direction.UP);
    }

    private BlockPos findBlock(BlockPos feet) {
        var mc = BytePhase.mc;
        Vec3d eye = mc.player.getEyePos();
        double reach = mc.player.getBlockInteractionRange();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dy = -1; dy <= 0; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    // Prefer the four blocks directly beside the target's feet;
                    // diagonal and floor blocks do not open a crystal position.
                    if (obsidianOnly.getValue() && (dy != 0 || Math.abs(dx) + Math.abs(dz) != 1)) continue;
                    if (dx == 0 && dz == 0 && dy == 0) continue;
                    BlockPos pos = feet.add(dx, dy, dz);
                    var state = mc.world.getBlockState(pos);
                    if (obsidianOnly.getValue() && !state.isOf(Blocks.OBSIDIAN)) continue;
                    if (state.isAir() || state.isOf(Blocks.BEDROCK) || !state.getFluidState().isEmpty()
                            || state.getHardness(mc.world, pos) < 0
                            || state.getCollisionShape(mc.world, pos).isEmpty()) continue;
                    Vec3d center = Vec3d.ofCenter(pos);
                    if (!BlockReachUtil.isWithinReach(mc, pos, reach)) continue;
                    double score = center.squaredDistanceTo(feet.toCenterPos())
                            + eye.squaredDistanceTo(center) * 0.05;
                    if (score < bestScore) {
                        best = pos.toImmutable();
                        bestScore = score;
                    }
                }
            }
        }
        return best;
    }

    private void cancelMining() {
        var mc = BytePhase.mc;
        PacketMine packetMine = BytePhase.modules == null ? null : BytePhase.modules.get(PacketMine.class);
        boolean sameWorld = mc != null && mc.world != null && mc.world == activeWorld;
        if (sameWorld && packetMine != null && packetMine.isEnabled() && targetBlock != null) {
            packetMine.cancel(targetBlock);
        }
        if (sameWorld && targetBlock != null && mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }
        targetBlock = null;
        targetPlayer = null;
    }
}
