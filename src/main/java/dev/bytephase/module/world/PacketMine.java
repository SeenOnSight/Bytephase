package dev.bytephase.module.world;

import dev.bytephase.BytePhase;
import dev.bytephase.mixin.ClientPlayerInteractionManagerAccessor;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.util.BlockReachUtil;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** Mines the selected block with sequenced start/stop packets and server-time progress. */
public final class PacketMine extends Module {
    private final BooleanSetting skipBedrock = add(new BooleanSetting("SkipBedrock", true));
    private final BooleanSetting requireAttack = add(new BooleanSetting("RequireAttack", false));

    private BlockPos mining;
    private BlockPos awaitingUpdate;
    private Direction side = Direction.UP;
    private float progress;
    private int waitTicks;
    private World activeWorld;

    public PacketMine() {
        super("PacketMine", Category.WORLD, "Mines blocks with start and stop packets while retaining tool control");
    }

    @Override
    protected void onEnable() {
        reset();
        activeWorld = BytePhase.mc == null ? null : BytePhase.mc.world;
    }

    @Override
    protected void onDisable() {
        abort();
        reset();
        activeWorld = null;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            reset();
            activeWorld = null;
            return;
        }
        if (mc.world != activeWorld) {
            // Do not interpret an old dimension's mining position or pending
            // block update against the new world's block at the same coords.
            reset();
            activeWorld = mc.world;
        }
        if (mc.getNetworkHandler() == null || mc.currentScreen != null || mc.player.isSpectator()
                || requireAttack.getValue() && !mc.options.attackKey.isPressed()) {
            abort();
            return;
        }
        if (awaitingUpdate != null) {
            if (mc.world.getBlockState(awaitingUpdate).isAir()) {
                awaitingUpdate = null;
                waitTicks = 0;
            } else if (--waitTicks > 0) {
                return;
            } else {
                awaitingUpdate = null;
            }
        }
        if (mining == null) return;
        BlockPos pos = mining;
        var state = mc.world.getBlockState(pos);
        if (state.isAir() || skipBedrock.getValue() && state.isOf(Blocks.BEDROCK)
                || !canMine(pos)) {
            abort();
            return;
        }

        progress += state.calcBlockBreakingDelta(mc.player, mc.world, pos);
        if (progress >= 1.0f) {
            send(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, mining, side);
            awaitingUpdate = mining;
            waitTicks = 4;
            mining = null;
            progress = 0.0f;
        }

    }

    /** Called by the interaction-manager mixin when the player begins mining. */
    public boolean begin(BlockPos pos, Direction direction) {
        if (!isEnabled()) return false;
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return false;
        if (mc.world != activeWorld) {
            reset();
            activeWorld = mc.world;
        }
        var state = mc.world.getBlockState(pos);
        if (awaitingUpdate != null) {
            if (mc.world.getBlockState(awaitingUpdate).isAir()) {
                awaitingUpdate = null;
            } else if (awaitingUpdate.equals(pos) && waitTicks > 0) {
                return true;
            } else {
                awaitingUpdate = null;
            }
        }
        if (state.isAir() || skipBedrock.getValue() && state.isOf(Blocks.BEDROCK)
                || !canMine(pos)) {
            abort();
            return true;
        }
        if (pos.equals(mining)) return true;
        abort();
        mining = pos.toImmutable();
        side = direction;
        progress = 0.0f;
        send(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, mining, side);
        return true;
    }

    /** Match the restrictions vanilla checks before it sends a start packet. */
    private boolean canMine(BlockPos pos) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.player.isBlockBreakingRestricted(mc.world, pos, mc.interactionManager.getCurrentGameMode())
                || !mc.world.getWorldBorder().contains(pos)) {
            return false;
        }
        var state = mc.world.getBlockState(pos);
        return state.getHardness(mc.world, pos) >= 0
                && BlockReachUtil.isWithinReach(mc, pos, mc.player.getBlockInteractionRange());
    }

    private void send(PlayerActionC2SPacket.Action action, BlockPos pos, Direction direction) {
        var mc = BytePhase.mc;
        if (mc == null || mc.world == null || mc.interactionManager == null
                || mc.getNetworkHandler() == null) return;
        ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).bytephase$sendSequencedPacket(
                mc.world, sequence -> new PlayerActionC2SPacket(action, pos, direction, sequence));
    }

    private void abort() {
        if (mining != null) {
            send(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, mining, side);
        }
        mining = null;
        progress = 0.0f;
        awaitingUpdate = null;
        waitTicks = 0;
    }

    /** Lets a target-driven miner cancel only the block it still owns. */
    public void cancel(BlockPos expected) {
        if (expected != null && expected.equals(mining)) {
            abort();
        }
    }

    /** Current local block and progress for mining overlays, including the server-ack wait. */
    public BlockPos overlayPosition() {
        return isEnabled() ? (mining != null ? mining : awaitingUpdate) : null;
    }

    public float overlayProgress() {
        return mining != null ? Math.max(0.0f, Math.min(1.0f, progress)) : 1.0f;
    }

    /** Whether PacketMine is currently accumulating break progress for this block. */
    public boolean isMining(BlockPos pos) {
        return pos != null && pos.equals(mining);
    }

    /** Whether PacketMine owns vanilla's progress callback for this block. */
    public boolean ownsBreaking(BlockPos pos) {
        return isEnabled() && pos != null
                && (pos.equals(mining) || pos.equals(awaitingUpdate) && waitTicks > 0);
    }

    private void reset() {
        mining = null;
        awaitingUpdate = null;
        progress = 0.0f;
        waitTicks = 0;
    }
}
