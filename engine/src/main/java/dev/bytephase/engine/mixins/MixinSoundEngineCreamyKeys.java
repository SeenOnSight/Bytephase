package dev.bytephase.engine.mixins;

import net.minecraft.client.sound.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.categories.misc.CreamyKeys;

@Mixin({SoundEngine.class})
public class MixinSoundEngineCreamyKeys {
   @Inject(
      method = {"close()V"},
      at = {@At("HEAD")}
   )
   private void bytephase$creamyKeysRelease(CallbackInfo callbackInfo) {
      CreamyKeys.updateClickPlayer();
   }
}
