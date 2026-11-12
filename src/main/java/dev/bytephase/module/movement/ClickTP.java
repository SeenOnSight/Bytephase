package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

/**
 * Teleports to the top of the block under the crosshair when the use key is
 * pressed. The client moves immediately and sends the matching position packet;
 * the server can still reject the move.
 */
public final class ClickTP extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", 100.0, 4.0, 256.0, 1.0));

    private boolean useWasPressed;

    public ClickTP() {
        super("ClickTP", Category.MOVEMENT, "Teleports to the block you are looking at");
    }

    @Override
    protected void onEnable() {
        var mc = BytePhase.mc;
        useWasPressed = mc != null && mc.options.useKey.isPressed();
    }

    @Override
    protected void onDisable() {
        useWasPressed = false;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (!isEnabled() || mc == null || mc.player == null) {
            return;
        }
        boolean pressed = mc.options.useKey.isPressed();
        boolean clicked = pressed && !useWasPressed;
        useWasPressed = pressed;
        if (!clicked || mc.currentScreen != null) {
            return;
        }
        HitResult hit = mc.player.raycast(range.getValue(), 1.0f, false);
        if (hit instanceof BlockHitResult blockHit) {
            sendTeleport(mc.player, teleportTarget(blockHit));
        }
    }

    /** The top centre of the clicked block, where the player's feet go. */
    public static Vec3d teleportTarget(BlockHitResult hit) {
        return Vec3d.ofBottomCenter(hit.getBlockPos().up());
    }

    public static void sendTeleport(ClientPlayerEntity player, Vec3d target) {
        player.setPosition(target);
        player.setVelocityClient(Vec3d.ZERO);
        player.fallDistance = 0.0;
        if (player.networkHandler != null) {
            player.networkHandler.sendPacket(
                    new PlayerMoveC2SPacket.PositionAndOnGround(target, false, false));
        }
    }
}
