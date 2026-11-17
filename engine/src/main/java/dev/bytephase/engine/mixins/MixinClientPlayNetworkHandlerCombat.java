package dev.bytephase.engine.mixins;

import dev.bytephase.engine.entity.ClientThreadUtils;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPlayNetworkHandler.class})
public class MixinClientPlayNetworkHandlerCombat {
   @Inject(
      method = {"onEntityVelocityUpdate(Lnet/minecraft/network/packet/s2c/play/EntityVelocityUpdateS2CPacket;)V"},
      at = {@At("TAIL")}
   )
   private void bytephaseSelfVelocity(EntityVelocityUpdateS2CPacket entityVelocityUpdateS2CPacke, CallbackInfo callbackInfo) {
      ClientThreadUtils.handleInstanceThread(entityVelocityUpdateS2CPacke.getEntityId());
   }

   @Inject(
      method = {"onExplosion(Lnet/minecraft/network/packet/s2c/play/ExplosionS2CPacket;)V"},
      at = {@At("TAIL")}
   )
   private void bytephaseSelfExplosion(ExplosionS2CPacket explosionS2CPacket, CallbackInfo callbackInfo) {
      explosionS2CPacket.playerKnockback().ifPresent(ClientThreadUtils::handleThreadInstance);
   }
}
