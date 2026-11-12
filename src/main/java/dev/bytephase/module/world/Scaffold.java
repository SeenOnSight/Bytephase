package dev.bytephase.module.world;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Bridge/tower scaffold.
 *
 * <p>Every tick the module looks at the cell directly below the player. If it
 * is replaceable it searches the six neighbours for a solid face to click and
 * places the selected block into that cell. Because it runs while walking (not
 * only while falling) it builds a bridge as soon as the player steps over an
 * edge. Tower mode jumps first so the block can be placed underneath.</p>
 */
public final class Scaffold extends Module {
    private static final Direction[] SUPPORT_ORDER = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST, Direction.UP
    };

    private final ModeSetting mode = add(new ModeSetting("Mode",
            new String[]{"Normal", "Tower"}, "Normal"));
    private final NumberSetting delay = add(new NumberSetting("Delay", 1, 0, 10, 1));
    private final BooleanSetting swing = add(new BooleanSetting("Swing", true));
    private final BooleanSetting swapBack = add(new BooleanSetting("SwapBack", true));

    private int cooldown;
    private int originalSlot = -1;
    private int scaffoldSlot = -1;

    public Scaffold() {
        super("Scaffold", Category.WORLD, "Places blocks under you while walking");
    }

    @Override
    protected void onEnable() {
        cooldown = 0;
        originalSlot = -1;
        scaffoldSlot = -1;
    }

    @Override
    protected void onDisable() {
        cooldown = 0;
        restoreSlot();
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }
        if (mc.currentScreen != null || mc.player.isSpectator()) {
            return;
        }
        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (scaffoldSlot >= 0 && selectedSlot != scaffoldSlot) {
            // Respect a manual hotbar change and use it as the new slot to
            // restore if Scaffold needs to switch again.
            originalSlot = -1;
            scaffoldSlot = -1;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (mode.getValue().equals("Tower") && mc.options.jumpKey.isPressed() && mc.player.isOnGround()) {
            Vec3d velocity = mc.player.getVelocity();
            mc.player.setVelocityClient(new Vec3d(velocity.x, 0.42, velocity.z));
            cooldown = 2;
            return;
        }

        BlockPos target = scaffoldTarget();
        if (target == null) {
            return;
        }
        int slot = findBlockSlot();
        if (slot < 0) {
            return;
        }

        BlockHitResult hit = findSupportFace(target);
        if (hit == null) {
            return;
        }
        double reach = mc.player.getBlockInteractionRange();
        if (mc.player.getEyePos().squaredDistanceTo(hit.getPos()) > reach * reach) {
            return;
        }

        if (mc.player.getInventory().getSelectedSlot() != slot) {
            if (swapBack.getValue() && originalSlot < 0) {
                originalSlot = mc.player.getInventory().getSelectedSlot();
            }
            mc.player.getInventory().setSelectedSlot(slot);
            scaffoldSlot = slot;
        }
        try {
            var result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (swing.getValue() && result.isAccepted()) {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        } finally {
            // SwapBack means restore the user's prior hotbar selection after
            // this placement, rather than only when Scaffold is disabled.
            restoreSlot();
        }
        cooldown = delay.asInt();
    }

    /** The replaceable cell directly below the player's feet, or null. */
    private BlockPos scaffoldTarget() {
        var mc = BytePhase.mc;
        BlockPos target = mc.player.getBlockPos().down();
        return mc.world.getBlockState(target).isReplaceable() ? target : null;
    }

    /**
     * Finds a solid neighbour of {@code target} and returns a click on the face
     * pointing at the target. The hit point sits on the support's surface
     * rather than inside the air cell, matching what the server expects.
     */
    private BlockHitResult findSupportFace(BlockPos target) {
        var mc = BytePhase.mc;
        for (Direction supportDirection : SUPPORT_ORDER) {
            BlockPos support = target.offset(supportDirection);
            BlockState state = mc.world.getBlockState(support);
            if (state.isAir() || state.isReplaceable()
                    || state.getCollisionShape(mc.world, support).isEmpty()) {
                continue;
            }
            // Right-clicking a chest/door/etc. opens it instead of placing.
            if (!mc.player.isSneaking() && dev.bytephase.util.BlockPlacementUtil.opensOnUse(state)) {
                continue;
            }
            Direction clickedFace = supportDirection.getOpposite();
            Vec3d center = Vec3d.ofCenter(support);
            Vec3d normal = Vec3d.of(clickedFace.getVector());
            Vec3d hitVec = center.add(normal.multiply(0.5));
            return new BlockHitResult(hitVec, clickedFace, support, false);
        }
        return null;
    }

    private int findBlockSlot() {
        var mc = BytePhase.mc;
        int current = mc.player.getInventory().getSelectedSlot();
        if (isBlock(mc.player.getInventory().getStack(current))) {
            return current;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (isBlock(mc.player.getInventory().getStack(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private void restoreSlot() {
        var mc = BytePhase.mc;
        if (swapBack.getValue() && mc != null && mc.player != null && originalSlot >= 0
                && mc.player.getInventory().getSelectedSlot() == scaffoldSlot) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
        }
        originalSlot = -1;
        scaffoldSlot = -1;
    }

    private static boolean isBlock(ItemStack stack) {
        return dev.bytephase.util.BlockPlacementUtil.isFiller(stack);
    }
}
