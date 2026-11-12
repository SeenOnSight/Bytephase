package dev.bytephase.util;

import dev.bytephase.BytePhase;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * A rotation that is sent to the server without turning the camera.
 *
 * <p>The modules that aim for you store their facing here. While a rotation is
 * active the outgoing movement packet carries it instead of the camera angles,
 * so the server (and everyone else) sees you turned toward the target while your
 * view stays exactly where you left it. The local player model is turned to
 * match at render time, so your own avatar turns too.</p>
 */
public final class SilentRotation {
    private static boolean active;
    private static float yaw;
    private static float pitch;

    private SilentRotation() {
    }

    public static void set(float yaw, float pitch) {
        active = true;
        SilentRotation.yaw = yaw;
        SilentRotation.pitch = pitch;
    }

    public static void clear() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    /** Sends the stored rotation right now, for an action in this same tick. */
    public static void flush() {
        ClientPlayerEntity player = BytePhase.mc == null ? null : BytePhase.mc.player;
        if (!active || player == null || player.networkHandler == null) {
            return;
        }
        player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                yaw, pitch, player.isOnGround(), player.horizontalCollision));
    }
}
