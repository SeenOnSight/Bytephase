package dev.bytephase.engine.mixins;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.client.sound.SoundSystem.PlayResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.bytephase.engine.categories.visuals.NoFarmLag;

@Mixin({SoundSystem.class})
public class MixinSoundSystemFarmLag {
   @Inject(
      method = {"play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephase$noFarmLagMuteSpam(SoundInstance soundInstance, CallbackInfoReturnable<PlayResult> callbackInfoReturnable) {
      NoFarmLag module = NoFarmLag.getNoFarmLag();
      if (module != null && module.canNoteBlock(soundInstance)) {
         callbackInfoReturnable.setReturnValue(PlayResult.NOT_STARTED);
      }
   }
}
