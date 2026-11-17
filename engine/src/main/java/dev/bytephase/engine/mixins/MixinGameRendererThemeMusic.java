package dev.bytephase.engine.mixins;

import dev.bytephase.engine.config.ThemeMusicPlayer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({GameRenderer.class})
public class MixinGameRendererThemeMusic {
   @Inject(
      method = {"render(Lnet/minecraft/client/render/RenderTickCounter;Z)V"},
      at = {@At("HEAD")}
   )
   private void bytephase$themeMusicFrame(RenderTickCounter renderTickCounter, boolean bytephasethemeMusicFlag, CallbackInfo callbackInfo) {
      ThemeMusicPlayer.tickMusicSafely();
   }
}
