package dev.bytephase.engine.mixins;

import net.minecraft.client.render.SkyRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.categories.visuals.CustomWorld;

@Mixin({SkyRendering.class})
public class MixinSkyRenderingCustomWorld {
   @Inject(
      method = {"renderStars(FLnet/minecraft/client/util/math/MatrixStack;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephase$hideStars(CallbackInfo callbackInfo) {
      CustomWorld cw = CustomWorld.getCustomWorld();
      if (cw != null && cw.isSkyCustom()) {
         callbackInfo.cancel();
      }
   }

   @Inject(
      method = {"renderCelestialBodies(Lnet/minecraft/client/util/math/MatrixStack;FFFLnet/minecraft/world/MoonPhase;FF)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephase$hideCelestial(CallbackInfo callbackInfo) {
      CustomWorld cw = CustomWorld.getCustomWorld();
      if (cw != null && cw.isSkyCustom()) {
         callbackInfo.cancel();
      }
   }
}
