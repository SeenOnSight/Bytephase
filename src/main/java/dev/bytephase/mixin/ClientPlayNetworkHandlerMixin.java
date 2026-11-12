package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.LogoutSpots;
import dev.bytephase.module.render.TimeChanger;
import net.minecraft.client.world.ClientWorld;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Shadow private ClientWorld world;

    @Inject(method = "onChunkData", at = @At("TAIL"))
    private void bytephase$chunkLight(net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket packet,
                                      CallbackInfo ci) {
        bytephase$receivedLight(packet.getChunkX(), packet.getChunkZ(), packet.getLightData(), true);
    }

    @Inject(method = "onLightUpdate", at = @At("TAIL"))
    private void bytephase$lightUpdate(net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket packet,
                                       CallbackInfo ci) {
        bytephase$receivedLight(packet.getChunkX(), packet.getChunkZ(), packet.getData(), false);
    }

    private void bytephase$receivedLight(int x, int z, net.minecraft.network.packet.s2c.play.LightData data,
                                         boolean initial) {
        if (BytePhase.modules == null || BytePhase.mc == null || !BytePhase.mc.isOnThread()) return;
        BytePhase.modules.receivedChunkLight(world, x, z, data, initial);
    }

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

    @Inject(method = "onPlayerPositionLook", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",
            shift = At.Shift.AFTER))
    private void bytephase$serverCorrection(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        if (BytePhase.modules != null) {
            BytePhase.modules.serverCorrection();
        }
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
