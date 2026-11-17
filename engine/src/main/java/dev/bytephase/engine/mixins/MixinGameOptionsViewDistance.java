package dev.bytephase.engine.mixins;

import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.bytephase.engine.categories.visuals.CustomWorld;

@Mixin({GameOptions.class})
public class MixinGameOptionsViewDistance {
   @Inject(
      method = {"getClampedViewDistance()I"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void bytephase$clampViewDistance(CallbackInfoReturnable<Integer> callbackInfoReturnable) {
      CustomWorld cw = CustomWorld.getCustomWorld();
      if (cw != null && cw.isBase()) {
         int original = callbackInfoReturnable.getReturnValueI();
         int chunks = Math.max(2, (int)(cw.getFogEnd() / 16.0F));
         callbackInfoReturnable.setReturnValue(Math.min(chunks, original));
      }
   }
}
