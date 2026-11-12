package dev.bytephase.test.mixin;

import dev.bytephase.test.NativeCombatRegressionChecks;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** This observer is present only in the game-test source set. */
@Mixin(ClientConnection.class)
public abstract class NativePacketTraceMixin {
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"))
    private void trace(Packet<?> packet, CallbackInfo ci) {
        NativeCombatRegressionChecks.record(packet);
    }
}
