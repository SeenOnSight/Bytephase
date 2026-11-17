package dev.bytephase.engine.mixins;

import net.minecraft.client.option.InactivityFpsLimiter;
import net.minecraft.client.option.InactivityFpsLimiter.LimitReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.bytephase.engine.utils.CrystalPvpTickController;
import dev.bytephase.engine.utils.SwordPlayer;

@Mixin({InactivityFpsLimiter.class})
public class MixinInactivityFpsLimiter {
   @Inject(
      method = {"getLimitReason()Lnet/minecraft/client/option/InactivityFpsLimiter$LimitReason;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephaseBotKeepsFrames(CallbackInfoReturnable<LimitReason> callbackInfoReturnable) {
      if (CrystalPvpTickController.isGloballyEngaged()) {
         callbackInfoReturnable.setReturnValue(LimitReason.NONE);
      } else if (SwordPlayer.isFightActive()) {
         callbackInfoReturnable.setReturnValue(LimitReason.NONE);
      }
   }
}
