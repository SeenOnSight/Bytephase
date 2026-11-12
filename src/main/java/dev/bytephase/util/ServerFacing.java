package dev.bytephase.util;

import dev.bytephase.BytePhase;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Client-thread evidence of accepted outgoing packets, not server acknowledgements. */
public final class ServerFacing {
    private static boolean tracking;
    private static ClientWorld world;
    private static ClientPlayerEntity player;
    private static ClientConnection connection;
    private static boolean hasLook;
    private static boolean ended;
    private static boolean acted;
    private static boolean cleanTick;
    private static int movements;
    private static float yaw;
    private static float pitch;

    private ServerFacing() { }

    public static void startTick(boolean enabled) {
        if (tracking != enabled) reset();
        tracking = enabled;
        sync();
    }

    private static void sync() {
        var mc = BytePhase.mc;
        var currentPlayer = mc == null ? null : mc.player;
        var currentWorld = mc == null ? null : mc.world;
        var currentConnection = currentPlayer == null || currentPlayer.networkHandler == null
                ? null : currentPlayer.networkHandler.getConnection();
        if (!tracking || currentPlayer != player || currentWorld != world || currentConnection != connection) {
            reset();
            if (tracking) {
                world = currentWorld;
                player = currentPlayer;
                connection = currentConnection;
            }
        }
    }

    public static void reset() {
        world = null;
        player = null;
        connection = null;
        hasLook = ended = acted = cleanTick = false;
        movements = 0;
    }

    /** Called only after the module packet interceptors have accepted the packet. */
    public static void observe(ClientConnection sender, Packet<?> packet) {
        if (!tracking || BytePhase.mc == null || !BytePhase.mc.isOnThread()) return;
        sync();
        if (player == null || world == null || sender != connection) return;
        if (packet instanceof PlayerMoveC2SPacket move) {
            movements++;
            ended = false;
            if (move.changesLook()) {
                yaw = move.getYaw(0.0f);
                pitch = move.getPitch(0.0f);
                hasLook = Float.isFinite(yaw) && Float.isFinite(pitch);
            }
        } else if (packet instanceof ClientTickEndC2SPacket) {
            cleanTick = movements <= 1;
            movements = 0;
            ended = true;
            acted = false;
        } else if (packet instanceof PlayerInteractEntityC2SPacket
                || packet instanceof PlayerInteractBlockC2SPacket
                || packet instanceof PlayerInteractItemC2SPacket
                || packet instanceof PlayerActionC2SPacket || packet instanceof ClientCommandC2SPacket) {
            acted = true;
        }
    }

    public static boolean canAct() {
        sync();
        return tracking && world != null && player != null && hasLook && ended && cleanTick && !acted;
    }

    public static boolean hasConflicts() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.interactionManager == null) return true;
        if (mc.player.isUsingItem() || mc.interactionManager.isBreakingBlock()) return true;
        var modules = BytePhase.modules;
        if (modules == null) return false;
        var blink = modules.get(dev.bytephase.module.movement.Blink.class);
        var lag = modules.get(dev.bytephase.module.movement.FakeLag.class);
        var criticals = modules.get(dev.bytephase.module.combat.Criticals.class);
        return blink != null && blink.isEnabled() || lag != null && lag.isEnabled()
                || criticals != null && criticals.isEnabled();
    }

    public static float continuousYaw(float cameraYaw) {
        sync();
        return hasLook ? yaw + MathHelper.wrapDegrees(cameraYaw - yaw) : cameraYaw;
    }

    public static BlockHitResult blockHit(BlockPos expected, double reach) {
        if (!canAct()) return null;
        var hit = world.raycast(new RaycastContext(player.getEyePos(),
                player.getEyePos().add(Vec3d.fromPolar(pitch, yaw).multiply(reach)),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(expected) ? hit : null;
    }

    public static boolean hitsEntity(Box bounds, double reach) {
        if (!canAct()) return false;
        Vec3d eye = player.getEyePos();
        var hit = bounds.raycast(eye, eye.add(Vec3d.fromPolar(pitch, yaw).multiply(reach)));
        if (bounds.contains(eye)) return true;
        if (hit.isEmpty()) return false;
        return world.raycast(new RaycastContext(eye, hit.get(), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player)).getType() == HitResult.Type.MISS;
    }
}
