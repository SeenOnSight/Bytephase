package dev.bytephase.engine.mixins;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.bytephase.engine.categories.misc.WeatherChanger;
import dev.bytephase.engine.categories.visuals.CustomWorld;

@Mixin({World.class})
public class MixinWorldGradient {
   @Inject(
      method = {"getRainGradient(F)F"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephase$rainGradient(float bytephaserainGradientValue, CallbackInfoReturnable<Float> callbackInfoReturnable) {
      WeatherChanger wc = WeatherChanger.getWeatherChanger();
      if (wc != null) {
         float wg = wc.getClearEnum();
         if (wg != -1.0F) {
            callbackInfoReturnable.setReturnValue(wg);
            return;
         }
      }

      CustomWorld cw = CustomWorld.getCustomWorld();
      if (cw != null) {
         float g = cw.getRainySunny();
         if (g != -1.0F) {
            callbackInfoReturnable.setReturnValue(g);
         }
      }
   }

   @Inject(
      method = {"getThunderGradient(F)F"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephase$thunderGradient(float bytephasethunderGradientValue, CallbackInfoReturnable<Float> callbackInfoReturnable) {
      WeatherChanger wc = WeatherChanger.getWeatherChanger();
      if (wc != null) {
         float wg = wc.getThunderEnum();
         if (wg != -1.0F) {
            callbackInfoReturnable.setReturnValue(wg);
            return;
         }
      }

      CustomWorld cw = CustomWorld.getCustomWorld();
      if (cw != null) {
         float g = cw.getRainySunnyWeather();
         if (g != -1.0F) {
            callbackInfoReturnable.setReturnValue(g);
         }
      }
   }
}
