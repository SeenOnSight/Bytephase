package dev.bytephase.engine.mixins;

import net.minecraft.client.world.ClientWorld.Properties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.bytephase.engine.categories.visuals.CustomWorld;

@Mixin({Properties.class})
public class MixinClientWorldProperties {
   @Inject(
      method = {"getTimeOfDay()J"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void bytephase$timeOfDay(CallbackInfoReturnable<Long> callbackInfoReturnable) {
      CustomWorld cw = CustomWorld.getCustomWorld();
      if (cw != null) {
         callbackInfoReturnable.setReturnValue(cw.getValueJ(callbackInfoReturnable.getReturnValueJ()));
      }
   }
}
