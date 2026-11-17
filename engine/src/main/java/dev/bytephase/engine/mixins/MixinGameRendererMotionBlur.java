package dev.bytephase.engine.mixins;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.categories.visuals.MotionBlur;

@Mixin({GameRenderer.class})
public abstract class MixinGameRendererMotionBlur {
   @Inject(
      method = {"renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V"},
      at = {@At("TAIL")}
   )
   private void bytephase$motionBlurWorldDrawn(RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
      MotionBlur.updateFlag();
   }

   @Inject(
      method = {"render(Lnet/minecraft/client/render/RenderTickCounter;Z)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/fog/FogRenderer;rotate()V"
      )}
   )
   private void bytephase$motionBlurBeforeHud(RenderTickCounter renderTickCounter, boolean bytephasemotionBlurFlag, CallbackInfo callbackInfo) {
      MotionBlur.updateFlagInstance();
   }
}
