package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;

/**
 * Jumps the tick knockback is applied so less of it is taken.
 *
 * <p>Ported from Anubis' {@code JumpResetModule}. Knockback arrives as an
 * {@link EntityVelocityUpdateS2CPacket} and is only visible to modules that
 * hook the inbound packet stream, so the jump has to be queued here and applied
 * on the next tick.</p>
 */
public final class JumpReset extends Module {
    // Packet callbacks run on Netty. Keep Minecraft player/world access on the
    // client thread and transfer only immutable packet data across threads.
    private record PendingKnockback(int entityId) { }
    private final AtomicReference<PendingKnockback> pending = new AtomicReference<>();
    private volatile int localEntityId = -1;

    public JumpReset() {
        super("JumpReset", Category.COMBAT, "Jumps when knockback hits to reduce it");
    }

    @Override
    protected void onDisable() {
        pending.set(null);
        localEntityId = -1;
    }

    @Override
    protected void onEnable() {
        var mc = BytePhase.mc;
        localEntityId = mc == null || mc.player == null ? -1 : mc.player.getId();
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        if (!(packet instanceof EntityVelocityUpdateS2CPacket velocity)) {
            return false;
        }
        if (velocity.getEntityId() != localEntityId) {
            return false;
        }
        Vec3d knockback = velocity.getVelocity();
        if (Math.hypot(knockback.x, knockback.z) >= 0.1 && knockback.y > 0.0) {
            pending.set(new PendingKnockback(velocity.getEntityId()));
        }
        return false;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        localEntityId = mc == null || mc.player == null ? -1 : mc.player.getId();
        PendingKnockback impact = pending.getAndSet(null);
        if (impact == null) {
            return;
        }
        if (mc == null || mc.player == null || mc.currentScreen != null
                || mc.player.getId() != impact.entityId()) {
            return;
        }
        var player = mc.player;
        if (!player.isOnGround() || !player.isSprinting() || player.isTouchingWater()
                || player.isInLava() || player.isClimbing() || player.hasVehicle()) {
            return;
        }
        if (!mc.options.forwardKey.isPressed()) {
            return;
        }
        Vec3d velocity = player.getVelocity();
        player.setVelocityClient(new Vec3d(velocity.x, 0.42, velocity.z));
    }
}
