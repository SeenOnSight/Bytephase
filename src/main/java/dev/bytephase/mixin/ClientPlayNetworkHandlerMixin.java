package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.combat.Velocity;
import dev.bytephase.module.player.NoRotate;
import dev.bytephase.module.render.LogoutSpots;
import dev.bytephase.module.render.TimeChanger;
import net.minecraft.client.world.ClientWorld;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import java.util.Optional;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Shadow private ClientWorld world;

    @Inject(method = "onBlockBreakingProgress", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
            shift = At.Shift.AFTER))
    private void bytephase$miningProgress(net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket packet,
                                          CallbackInfo ci) {
        var overlay = BytePhase.modules == null ? null : BytePhase.modules.get(dev.bytephase.module.render.MiningProgress.class);
        if (overlay != null && overlay.isEnabled()) {
            overlay.updateRemote(world, packet.getEntityId(), packet.getPos(), packet.getProgress());
        }
    }

    @ModifyArg(method = "onWorldTimeUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/world/ClientWorld;setTime(JJZ)V"), index = 1)
    private long bytephase$overrideTimeOfDay(long serverTime) {
        TimeChanger changer = BytePhase.modules == null ? null : BytePhase.modules.get(TimeChanger.class);
        return changer != null && changer.isEnabled() ? changer.overrideTime(world, serverTime) : serverTime;
    }

    @Unique
    private boolean bytephase$noRotate;
    @Unique
    private float bytephase$yaw;
    @Unique
    private float bytephase$pitch;

    @Inject(method = "onPlayerPositionLook", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
            shift = At.Shift.AFTER))
    private void bytephase$serverCorrection(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        if (BytePhase.modules != null) {
            BytePhase.modules.serverCorrection();
        }
    }

    @Inject(method = "onPlayerPositionLook", at = @At("HEAD"))
    private void bytephase$resetNoRotateState(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        // The packet can first enter on Netty, where vanilla schedules a main
        // thread retry by throwing. Clear any stale state without touching the
        // player; the capture below runs only after that handoff.
        this.bytephase$noRotate = false;
    }

    @Inject(method = "onEntityVelocityUpdate", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void bytephase$velocity(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        Velocity velocity = BytePhase.modules == null ? null : BytePhase.modules.velocity();
        if (velocity == null || BytePhase.mc == null || BytePhase.mc.player == null) {
            return;
        }
        if (packet.getEntityId() != BytePhase.mc.player.getId()) {
            return;
        }
        double horizontal = velocity.getHorizontal();
        double vertical = velocity.getVertical();
        // Fully cancelling a velocity packet must not erase existing movement.
        if (horizontal != 0.0 || vertical != 0.0) {
            Vec3d v = packet.getVelocity().multiply(horizontal, vertical, horizontal);
            BytePhase.mc.player.setVelocityClient(v);
        }
        ci.cancel();
    }

    @Inject(method = "onEntitySpawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
            shift = At.Shift.AFTER))
    private void bytephase$crystalSpawn(net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket packet, CallbackInfo ci) {
        if (packet.getEntityType() != net.minecraft.entity.EntityType.END_CRYSTAL) return;
        var crystal = BytePhase.modules == null ? null : BytePhase.modules.get(dev.bytephase.module.combat.AutoCrystal.class);
        if (crystal != null && crystal.isEnabled()) {
            crystal.onCrystalSpawn(new Vec3d(packet.getX(), packet.getY(), packet.getZ()));
        }
    }

    @Inject(method = "onExplosion", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
            shift = At.Shift.AFTER))
    private void bytephase$crystalExplosion(ExplosionS2CPacket packet, CallbackInfo ci) {
        var crystal = BytePhase.modules == null ? null : BytePhase.modules.get(dev.bytephase.module.combat.AutoCrystal.class);
        if (crystal != null && crystal.isEnabled()) crystal.onCrystalExplosion(packet.center());
    }

    @Redirect(method = "onExplosion", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/packet/s2c/play/ExplosionS2CPacket;playerKnockback()Ljava/util/Optional;"))
    private Optional<Vec3d> bytephase$explosionVelocity(ExplosionS2CPacket packet) {
        Velocity velocity = BytePhase.modules == null ? null : BytePhase.modules.velocity();
        if (velocity == null) return packet.playerKnockback();
        return packet.playerKnockback().map(v -> v.multiply(
                velocity.getHorizontal(), velocity.getVertical(), velocity.getHorizontal()));
    }

    @Inject(method = "onPlayerPositionLook", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;setPosition(Lnet/minecraft/entity/EntityPosition;Ljava/util/Set;Lnet/minecraft/entity/Entity;Z)Z",
            shift = At.Shift.BEFORE))
    private void bytephase$noRotateBeforeApply(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        NoRotate noRotate = BytePhase.modules == null ? null : BytePhase.modules.noRotate();
        this.bytephase$noRotate = noRotate != null && BytePhase.mc != null && BytePhase.mc.player != null;
        if (this.bytephase$noRotate) {
            this.bytephase$yaw = BytePhase.mc.player.getYaw();
            this.bytephase$pitch = BytePhase.mc.player.getPitch();
        }
    }

    // Vanilla sends this full acknowledgement immediately after applying a
    // position/look packet. Replacing these two arguments keeps the server's
    // authoritative rotation in sync with the rotation we retain locally.
    @ModifyArg(method = "onPlayerPositionLook", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/packet/c2s/play/PlayerMoveC2SPacket$Full;<init>(DDDFFZZ)V"),
            index = 3)
    private float bytephase$noRotateAckYaw(float yaw) {
        return this.bytephase$noRotate ? this.bytephase$yaw : yaw;
    }

    @ModifyArg(method = "onPlayerPositionLook", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/packet/c2s/play/PlayerMoveC2SPacket$Full;<init>(DDDFFZZ)V"),
            index = 4)
    private float bytephase$noRotateAckPitch(float pitch) {
        return this.bytephase$noRotate ? this.bytephase$pitch : pitch;
    }

    @Inject(method = "onPlayerPositionLook", at = @At("RETURN"))
    private void bytephase$noRotateReturn(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        if (!this.bytephase$noRotate) {
            return;
        }
        this.bytephase$noRotate = false;
        var player = BytePhase.mc.player;
        if (player == null) {
            return;
        }
        player.setYaw(this.bytephase$yaw);
        player.setPitch(this.bytephase$pitch);
        player.setHeadYaw(this.bytephase$yaw);
        player.setBodyYaw(this.bytephase$yaw);
    }

    @Inject(method = "onPlayerRemove", at = @At("HEAD"))
    private void bytephase$playerRemove(PlayerRemoveS2CPacket packet, CallbackInfo ci) {
        LogoutSpots spots = BytePhase.modules == null ? null : BytePhase.modules.logoutSpots();
        if (spots != null && BytePhase.mc != null) {
            List<UUID> removed = List.copyOf(packet.profileIds());
            BytePhase.mc.execute(() -> {
                if (spots.isEnabled()) {
                    spots.onPlayerRemove(removed);
                }
            });
        }
    }
}
