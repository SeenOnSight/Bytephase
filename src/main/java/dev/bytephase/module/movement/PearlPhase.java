package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** One-shot pearl clip attempt; the server ultimately decides the teleport. */
public final class PearlPhase extends Module {
    private ClientPlayerEntity owner;
    private int previousSlot = -1;
    private int pearlSlot = -1;
    private Vec3d aim;

    public PearlPhase() {
        super("PearlPhase", Category.MOVEMENT, "Attempts a pearl clip through the wall in front of you");
    }

    @Override
    protected void onEnable() {
        owner = null;
        previousSlot = -1;
        pearlSlot = -1;
        aim = null;
    }

    @Override
    protected void onDisable() {
        if (owner != null && previousSlot >= 0 && owner.getInventory().getSelectedSlot() == pearlSlot) {
            owner.getInventory().setSelectedSlot(previousSlot);
        }
        owner = null;
        previousSlot = -1;
        pearlSlot = -1;
        aim = null;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || mc.player.isSpectator() || mc.player.isDead()) {
            setEnabled(false);
            return;
        }
        if (owner == null) {
            Vec3d eye = mc.player.getEyePos();
            Vec3d forward = Vec3d.fromPolar(0, mc.player.getYaw());
            BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, eye.add(forward.multiply(1.5)),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() != HitResult.Type.BLOCK || hit.getSide().getAxis().isVertical()) {
                fail("Stand close to and face a wall.");
                return;
            }
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) {
                    owner = mc.player;
                    previousSlot = owner.getInventory().getSelectedSlot();
                    pearlSlot = i;
                    owner.getInventory().setSelectedSlot(i);
                    // Throw at the wall's lower edge. Whether this clips is server-dependent.
                    aim = new Vec3d(hit.getPos().x, mc.player.getY() + 0.05, hit.getPos().z);
                    return;
                }
            }
            fail("Put an ender pearl in your hotbar.");
            return;
        }
        if (owner != mc.player || owner.getInventory().getSelectedSlot() != pearlSlot
                || !owner.getMainHandStack().isOf(Items.ENDER_PEARL)) {
            setEnabled(false);
            return;
        }
        Vec3d delta = aim.subtract(owner.getEyePos());
        float yaw = (float) (MathHelper.atan2(delta.z, delta.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0f;
        float pitch = (float) (-MathHelper.atan2(delta.y, delta.horizontalLength()) * MathHelper.DEGREES_PER_RADIAN);
        float oldYaw = owner.getYaw();
        float oldPitch = owner.getPitch();
        owner.setYaw(yaw);
        owner.setPitch(pitch);
        try {
            owner.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, owner.isOnGround(), false));
            var result = mc.interactionManager.interactItem(owner, Hand.MAIN_HAND);
            if (result.isAccepted()) owner.swingHand(Hand.MAIN_HAND);
        } finally {
            owner.setYaw(oldYaw);
            owner.setPitch(oldPitch);
            owner.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    oldYaw, oldPitch, owner.isOnGround(), false));
            setEnabled(false);
        }
    }

    private void fail(String message) {
        var mc = BytePhase.mc;
        if (mc != null && mc.player != null) mc.player.sendMessage(Text.literal("[BytePhase] " + message), false);
        setEnabled(false);
    }
}
