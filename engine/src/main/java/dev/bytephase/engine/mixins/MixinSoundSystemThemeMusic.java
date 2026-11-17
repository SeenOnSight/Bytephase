package dev.bytephase.engine.mixins;

import dev.bytephase.engine.config.ThemeMusicPlayer;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SoundSystem.class})
public class MixinSoundSystemThemeMusic {
   @Inject(
      method = {"resumeAll()V"},
      at = {@At("TAIL")}
   )
   private void bytephase$keepThemeMusicParked(CallbackInfo callbackInfo) {
      ThemeMusicPlayer.enforcePausedState();
   }

   @Inject(
      method = {"getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void bytephase$duckMusicUnderTheme(float bytephaseduckMusicValue, SoundCategory soundCategory, CallbackInfoReturnable<Float> callbackInfoReturnable) {
      if (soundCategory == SoundCategory.MUSIC) {
         float duck = ThemeMusicPlayer.getMusicDuckFactor();
         if (duck < 1.0F) {
            callbackInfoReturnable.setReturnValue(callbackInfoReturnable.getReturnValueF() * duck);
         }
      }
   }
}
