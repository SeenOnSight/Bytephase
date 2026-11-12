package dev.bytephase.util;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Shared, bounded block placement helpers for combat placement modules. */
public final class BlockPlacementUtil {
    private BlockPlacementUtil() { }

    public static int findHotbarBlock(MinecraftClient mc, net.minecraft.block.Block block) {
        if (mc == null || mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem item && item.getBlock() == block) {
                return i;
            }
        }
        return -1;
    }

    /** Whether right-clicking this block does something other than accept a placement. */
    public static boolean opensOnUse(BlockState state) {
        var block = state.getBlock();
        return state.hasBlockEntity()
                || block instanceof net.minecraft.block.DoorBlock
                || block instanceof net.minecraft.block.TrapdoorBlock
                || block instanceof net.minecraft.block.FenceGateBlock
                || block instanceof net.minecraft.block.ButtonBlock
                || block instanceof net.minecraft.block.LeverBlock
                || block instanceof net.minecraft.block.BedBlock
                || block instanceof net.minecraft.block.CraftingTableBlock
                || block instanceof net.minecraft.block.AnvilBlock
                || block instanceof net.minecraft.block.NoteBlock;
    }

    /**
     * Whether the stack is a block that is sensible to use as filler: a full
     * cube with no block entity that does not fall or explode. Without this,
     * "any block" placement would happily put down chests, shulker boxes, TNT,
     * sand, slabs, torches and so on.
     */
    public static boolean isFiller(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) {
            return false;
        }
        var block = item.getBlock();
        var state = block.getDefaultState();
        return !state.hasBlockEntity()
                && !(block instanceof net.minecraft.block.FallingBlock)
                && !(block instanceof net.minecraft.block.TntBlock)
                && state.isFullCube(net.minecraft.world.EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
    }

    /** First hotbar slot holding a usable filler block, or -1. */
    public static int findAnyHotbarBlock(MinecraftClient mc) {
        if (mc == null || mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (isFiller(mc.player.getInventory().getStack(i))) {
                return i;
            }
        }
        return -1;
    }

    /** Places in a replaceable cell by clicking a reachable solid neighbour. */
    public static boolean place(MinecraftClient mc, BlockPos target, int slot,
                                boolean swapBack, boolean swing) {
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || slot < 0 || slot >= 9
                || !mc.world.getBlockState(target).isReplaceable()
                || !mc.world.isSpaceEmpty(mc.player, new net.minecraft.util.math.Box(target))) {
            return false;
        }

        double reach = mc.player.getBlockInteractionRange();
        double reachSq = reach * reach;
        BlockHitResult hit = null;
        double bestDistanceSq = Double.POSITIVE_INFINITY;
        Vec3d eye = mc.player.getEyePos();
        for (Direction direction : Direction.values()) {
            BlockPos support = target.offset(direction);
            BlockState state = mc.world.getBlockState(support);
            if (state.getCollisionShape(mc.world, support).isEmpty()) continue;
            // Clicking a chest, door, trapdoor, button, crafting table and the
            // like activates it instead of placing against it, unless the
            // player is sneaking. Prefer any other support face.
            if (!mc.player.isSneaking() && opensOnUse(state)) continue;

            Direction face = direction.getOpposite();
            Vec3d hitPos = Vec3d.ofCenter(support).add(Vec3d.of(face.getVector()).multiply(0.5));
            double distanceSq = eye.squaredDistanceTo(hitPos);
            if (distanceSq > reachSq || distanceSq >= bestDistanceSq) continue;
            // The support face lies exactly on the boundary between the support
            // block and the empty target block. A ray that stops on that plane
            // only visits the target block, so the support is never tested and
            // the raycast always misses. Push the endpoint just inside the
            // support so the traversal includes it.
            Vec3d rayEnd = hitPos.add(Vec3d.of(face.getVector()).multiply(-0.01));
            BlockHitResult sight = mc.world.raycast(new RaycastContext(eye, rayEnd,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (sight.getType() != HitResult.Type.BLOCK
                    || !sight.getBlockPos().equals(support) || sight.getSide() != face) continue;
            hit = sight;
            bestDistanceSq = distanceSq;
        }
        if (hit == null) return false;

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        if (oldSlot != slot) mc.player.getInventory().setSelectedSlot(slot);
        try {
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (swing && result.isAccepted()) {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
            return result.isAccepted();
        } finally {
            if (swapBack && oldSlot != slot) {
                mc.player.getInventory().setSelectedSlot(oldSlot);
            }
        }
    }
}
