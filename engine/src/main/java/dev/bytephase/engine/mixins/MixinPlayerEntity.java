package dev.bytephase.engine.mixins;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.bytephase.engine.categories.combat.Reach;
import dev.bytephase.engine.categories.misc.Fastbreak;
import ai.auth.Module;
import ai.auth.ModuleRegistry;

@Mixin({PlayerEntity.class})
public abstract class MixinPlayerEntity {
   @Inject(
      method = {"getBlockBreakingSpeed(Lnet/minecraft/block/BlockState;)F"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void bytephaseFastbreak(BlockState state, CallbackInfoReturnable<Float> callbackInfoReturnable) {
      if (((Object) this) == Module.mc.player) {
         Fastbreak m = (Fastbreak)ModuleRegistry.getModule(Fastbreak.class);
         if (m != null && m.isEnabled() && m.isPaceToggle()) {
            callbackInfoReturnable.setReturnValue(callbackInfoReturnable.getReturnValueF() * m.getAmount());
         }
      }
   }

   @Inject(
      method = {"getEntityInteractionRange()D"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void bytephaseReach(CallbackInfoReturnable<Double> callbackInfoReturnable) {
      if (((Object) this) == Module.mc.player) {
         Reach m = (Reach)ModuleRegistry.getModule(Reach.class);
         if (m != null && m.isEnabled()) {
            double range = m.getActiveReachDistance();
            if (range > callbackInfoReturnable.getReturnValueD()) {
               callbackInfoReturnable.setReturnValue(range);
            }
         }
      }
   }
}
