package dev.bytephase.module.world;

import dev.bytephase.BytePhase;
import dev.bytephase.input.KeyOverride;
import dev.bytephase.mixin.ClientPlayerInteractionManagerAccessor;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.module.combat.AutoMine;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.BlockReachUtil;
import dev.bytephase.util.RotationUtil;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Level tunnel with a heading locked on activation; even widths extend farther right. */
public final class TunnelMiner extends Module {
    private final NumberSetting width = add(new NumberSetting("Width", 1, 1, 5, 1));
    private final NumberSetting height = add(new NumberSetting("Height", 2, 1, 5, 1));
    private final NumberSetting distance = add(new NumberSetting("ForwardDistance", 3, 1, 6, 1));
    private final BooleanSetting autoWalk = add(new BooleanSetting("AutoWalk", false));
    private World activeWorld;
    private PlayerEntity activePlayer;
    private Direction heading;
    private int floorY, correctionPause;
    private BlockPos target;
    private boolean guardRegistered;

    public TunnelMiner() {
        super("TunnelMiner", Category.WORLD, "Mines a level tunnel with visible aim; movement is manual by default");
    }

    @Override
    protected void onEnable() {
        activeWorld = null;
        heading = null;
        correctionPause = 0;
        releaseInput();
        if (!guardRegistered) {
            ClientTickEvents.START_CLIENT_TICK.register(client -> { if (isEnabled()) guardInput(); });
            guardRegistered = true;
        }
    }

    @Override
    protected void onDisable() {
        stop();
        activeWorld = null;
        activePlayer = null;
        heading = null;
    }

    @Override
    public void onServerCorrection() {
        KeyOverride.releaseAll(this, false);
        stop();
        heading = null;
        correctionPause = 10;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        releaseInput();
        if (mc == null || mc.world != activeWorld || mc.player != activePlayer) {
            // Never abort an old dimension's coordinates in the new world.
            target = null;
            activeWorld = mc == null ? null : mc.world;
            activePlayer = mc == null ? null : mc.player;
            heading = null;
        }
        if (!available()) {
            stop();
            return;
        }
        if (correctionPause > 0) {
            correctionPause--;
            stop();
            return;
        }
        if (heading == null) {
            heading = mc.player.getHorizontalFacing();
            floorY = mc.player.getBlockY();
            return;
        }
        if (priorityMiner()) {
            // Another miner may already have acquired the same position this tick.
            // Do not cancel shared vanilla or PacketMine state after yielding.
            target = null;
            return;
        }
        if (Math.abs(mc.player.getY() - floorY) > 0.01 || !loadedCorridor()) {
            stop();
            return;
        }
        PacketMine packet = packetMine();
        var manager = (ClientPlayerInteractionManagerAccessor) mc.interactionManager;
        if (manager.bytephase$isBreaking() && !manager.bytephase$breakingPos().equals(target)
                || packet != null && packet.overlayPosition() != null && !packet.overlayPosition().equals(target)) {
            stop();
            return;
        }
        BlockHitResult hit = target != null && inTunnel(target) ? visible(target) : null;
        if (hit == null) {
            stop();
            hit = findTarget();
        }
        if (hit == null) {
            if (autoWalk.getValue() && canWalk()) {
                mc.player.setYaw(mc.player.getYaw() + MathHelper.wrapDegrees(heading.getPositiveHorizontalDegrees() - mc.player.getYaw()));
                KeyOverride.set(this, mc.options.forwardKey, true);
            }
            return;
        }
        target = hit.getBlockPos().toImmutable();
        Vec3d eye = mc.player.getEyePos();
        Vec3d aim = Vec3d.ofCenter(target);
        mc.player.setYaw(mc.player.getYaw() + MathHelper.wrapDegrees(RotationUtil.yawTo(eye, aim) - mc.player.getYaw()));
        mc.player.setPitch(RotationUtil.pitchTo(eye, aim));
        mc.gameRenderer.updateCrosshairTarget(1.0f);
        HitResult sight = mc.crosshairTarget;
        if (!(sight instanceof BlockHitResult actual) || sight.getType() != HitResult.Type.BLOCK
                || !actual.getBlockPos().equals(target)) {
            stop();
            return;
        }
        // Keep vanilla progress, and never restart PacketMine during its acknowledgement wait.
        boolean owns = packet != null ? packet.ownsBreaking(target)
                : manager.bytephase$isBreaking() && target.equals(manager.bytephase$breakingPos());
        if (!owns) {
            if (!mc.interactionManager.attackBlock(target, actual.getSide())
                    || packet != null && !packet.ownsBreaking(target)) {
                stop();
                return;
            }
            if (mc.interactionManager.updateBlockBreakingProgress(target, actual.getSide()))
                mc.player.swingHand(Hand.MAIN_HAND);
        }
        // Vanilla advances once per tick while held; do not double its progress here.
        KeyOverride.set(this, mc.options.attackKey, true);
    }

    private boolean available() {
        var mc = BytePhase.mc;
        return mc != null && mc.world != null && mc.world == activeWorld && mc.player != null
                && mc.player == activePlayer && mc.getCameraEntity() == mc.player && mc.interactionManager != null
                && mc.getNetworkHandler() != null && mc.currentScreen == null && mc.isWindowFocused()
                && !mc.player.isSpectator() && mc.player.isAlive() && !mc.player.isUsingItem();
    }

    private void guardInput() {
        var mc = BytePhase.mc;
        if (!available() || heading == null || correctionPause > 0 || priorityMiner()
                || Math.abs(mc.player.getY() - floorY) > 0.01 || !loadedCorridor()) {
            stop();
            return;
        }
        if (target == null) {
            if (!autoWalk.getValue() || !canWalk()
                    || Math.abs(MathHelper.wrapDegrees(mc.player.getYaw() - heading.getPositiveHorizontalDegrees())) > 2)
                releaseInput();
            return;
        }
        mc.gameRenderer.updateCrosshairTarget(1.0f);
        if (!inTunnel(target) || visible(target) == null
                || !(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !hit.getBlockPos().equals(target))
            stop();
    }

    private double reach() {
        var player = BytePhase.mc.player;
        // Reach overrides the getter; the vanilla attribute remains the upper bound.
        return Math.min(player.getBlockInteractionRange(), player.getAttributeValue(EntityAttributes.BLOCK_INTERACTION_RANGE));
    }

    private int depth() {
        return Math.min(distance.asInt(), (int) Math.ceil(reach()));
    }

    private BlockPos cell(int forward, int lateral, int up) {
        return BytePhase.mc.player.getBlockPos().withY(floorY).offset(heading, forward)
                .offset(heading.rotateYClockwise(), lateral).up(up);
    }

    private boolean inTunnel(BlockPos pos) {
        BlockPos base = cell(0, 0, 0);
        int dx = pos.getX() - base.getX(), dz = pos.getZ() - base.getZ();
        Direction right = heading.rotateYClockwise();
        int forward = dx * heading.getOffsetX() + dz * heading.getOffsetZ();
        int lateral = dx * right.getOffsetX() + dz * right.getOffsetZ();
        return forward >= 0 && forward <= depth() && lateral >= -(width.asInt() - 1) / 2
                && lateral <= width.asInt() / 2 && pos.getY() >= floorY && pos.getY() < floorY + height.asInt();
    }

    private boolean loaded(BlockPos pos) {
        var world = BytePhase.mc.world;
        return !world.isOutOfHeightLimit(pos) && world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private boolean loadedCorridor() {
        for (int d = 0; d <= depth(); d++)
            for (int x = -(width.asInt() - 1) / 2; x <= width.asInt() / 2; x++)
                for (int y = 0; y < height.asInt(); y++)
                    if (!loaded(cell(d, x, y))) return false;
        return true;
    }

    private BlockHitResult findTarget() {
        for (int d = 0; d <= depth(); d++)
            for (int y = 0; y < height.asInt(); y++)
                for (int x = -(width.asInt() - 1) / 2; x <= width.asInt() / 2; x++) {
                    BlockHitResult hit = visible(cell(d, x, y));
                    if (hit != null) return hit;
                }
        return null;
    }

    private BlockHitResult visible(BlockPos pos) {
        var mc = BytePhase.mc;
        if (!loaded(pos) || !mc.world.getWorldBorder().contains(pos)) return null;
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || mc.world.getBlockEntity(pos) != null
                || !state.getFluidState().isEmpty() || state.getHardness(mc.world, pos) < 0
                || state.getOutlineShape(mc.world, pos).isEmpty()
                || mc.player.isBlockBreakingRestricted(mc.world, pos, mc.interactionManager.getCurrentGameMode())
                || !mc.player.getMainHandStack().canMine(state, mc.world, pos, mc.player)
                || !BlockReachUtil.isWithinReach(mc, pos, reach())) return null;
        // Do not open a fluid pocket or remove support beneath falling blocks.
        for (Direction side : Direction.values()) {
            BlockPos neighbor = pos.offset(side);
            if (!loaded(neighbor) || !mc.world.getFluidState(neighbor).isEmpty()) return null;
        }
        if (mc.world.getBlockState(pos.up()).getBlock() instanceof FallingBlock) return null;
        BlockHitResult hit = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), Vec3d.ofCenter(pos),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.ANY, mc.player));
        double reach = reach();
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)
                && mc.player.getEyePos().squaredDistanceTo(hit.getPos()) <= reach * reach ? hit : null;
    }

    private boolean canWalk() {
        var mc = BytePhase.mc;
        if (height.asInt() < 2 || !mc.player.isOnGround() || mc.player.isSneaking() || mc.player.isSprinting()
                || mc.player.getAbilities().flying || mc.player.hasVehicle() || mc.player.isGliding()
                || mc.player.isTouchingWater() || mc.player.isInLava()
                || mc.options.jumpKey.isPressed() || mc.options.leftKey.isPressed()
                || mc.options.rightKey.isPressed() || mc.options.backKey.isPressed()) return false;
        if (BytePhase.modules != null)
            for (Module module : BytePhase.modules.enabled())
                if (module.getCategory() == Category.MOVEMENT) return false;
        for (int x = -(width.asInt() - 1) / 2; x <= width.asInt() / 2; x++)
            for (int y = 0; y < height.asInt(); y++)
                if (!loaded(cell(1, x, y)) || !mc.world.getBlockState(cell(1, x, y)).isAir()) return false;
        Vec3d movement = new Vec3d(heading.getOffsetX(), 0, heading.getOffsetZ());
        Vec3d velocity = mc.player.getVelocity();
        if (velocity.horizontalLength() > 0.3 || velocity.dotProduct(movement) < -0.01
                || velocity.subtract(movement.multiply(velocity.dotProduct(movement))).horizontalLength() > 0.05) return false;
        // Sweep one block ahead, including the full player footprint and every supporting floor cell.
        Box sweep = mc.player.getBoundingBox().union(mc.player.getBoundingBox().offset(movement)).contract(0.001);
        for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(sweep.minX, floorY - 1, sweep.minZ),
                BlockPos.ofFloored(sweep.maxX, sweep.maxY, sweep.maxZ))) {
            if (!loaded(pos) || !mc.world.getWorldBorder().contains(pos)) return false;
            BlockState state = mc.world.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || state.hasBlockEntity() || hazardous(state)) return false;
            if (pos.getY() == floorY - 1 && !state.isSideSolidFullSquare(mc.world, pos, Direction.UP)) return false;
            if (pos.getY() >= floorY && !state.isAir()) return false;
        }
        return mc.world.isSpaceEmpty(mc.player, sweep);
    }

    private boolean hazardous(BlockState state) {
        return state.isOf(Blocks.MAGMA_BLOCK) || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.FIRE)
                || state.isOf(Blocks.SOUL_FIRE) || state.isOf(Blocks.CAMPFIRE) || state.isOf(Blocks.SOUL_CAMPFIRE)
                || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.WITHER_ROSE) || state.isOf(Blocks.POWDER_SNOW);
    }

    private boolean priorityMiner() {
        if (BytePhase.modules == null) return false;
        AutoMine auto = BytePhase.modules.get(AutoMine.class);
        Nuker nuker = BytePhase.modules.get(Nuker.class);
        return auto != null && auto.isEnabled() || nuker != null && nuker.isEnabled();
    }

    private PacketMine packetMine() {
        PacketMine packet = BytePhase.modules == null ? null : BytePhase.modules.get(PacketMine.class);
        return packet != null && packet.isEnabled() ? packet : null;
    }

    private void releaseInput() {
        KeyOverride.releaseAll(this, BytePhase.mc != null && BytePhase.mc.currentScreen == null
                && BytePhase.mc.isWindowFocused());
    }

    private void stop() {
        releaseInput();
        var mc = BytePhase.mc;
        if (target != null && mc != null && mc.world == activeWorld && mc.player == activePlayer && !priorityMiner()) {
            PacketMine packet = packetMine();
            if (packet != null) packet.cancel(target);
            if (mc.interactionManager != null) {
                var manager = (ClientPlayerInteractionManagerAccessor) mc.interactionManager;
                if (manager.bytephase$isBreaking() && target.equals(manager.bytephase$breakingPos()))
                    mc.interactionManager.cancelBlockBreaking();
            }
        }
        target = null;
    }
}
