package dev.bytephase.pvp.mixin;

import dev.bytephase.pvp.event.impl.PacketAppliedEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {
    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    private void pvp$afterEntitySpawn(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        PacketAppliedEvent.dispatch(packet);
    }
    

    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void pvp$afterEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        PacketAppliedEvent.dispatch(packet);
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void pvp$afterLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        PacketAppliedEvent.dispatch(packet);
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
    private void pvp$afterChunkData(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        PacketAppliedEvent.dispatch(packet);
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void pvp$afterBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        PacketAppliedEvent.dispatch(packet);
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void pvp$afterSectionUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        PacketAppliedEvent.dispatch(packet);
    }

    @Inject(method = "handleLightUpdatePacket", at = @At("TAIL"))
    private void pvp$afterLightUpdate(ClientboundLightUpdatePacket packet, CallbackInfo ci) {
        PacketAppliedEvent.dispatch(packet);
    }

    @Inject(method = "handleSetEntityMotion", at = @At("TAIL"))
    private void pvp$afterEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        PacketAppliedEvent.dispatch(packet);
    }
}
