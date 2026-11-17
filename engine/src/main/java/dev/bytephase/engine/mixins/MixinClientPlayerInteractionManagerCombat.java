package dev.bytephase.engine.mixins;

import dev.bytephase.engine.entity.ClientThreadUtils;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPlayerInteractionManager.class})
public class MixinClientPlayerInteractionManagerCombat {
   @Inject(
      method = {"attackEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;)V"},
      at = {@At("HEAD")}
   )
   private void bytephaseOnAttackEntity(PlayerEntity attacker, Entity entity, CallbackInfo callbackInfo) {
      ClientThreadUtils.handleFlagEntity(entity);
   }
}
