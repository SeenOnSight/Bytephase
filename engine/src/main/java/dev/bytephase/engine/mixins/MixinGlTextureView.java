package dev.bytephase.engine.mixins;

import dev.bytephase.engine.render.BytephaseRoundedUtils;
import net.minecraft.client.texture.GlTextureView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({GlTextureView.class})
public class MixinGlTextureView {
   @Inject(
      at = {@At("HEAD")},
      method = {"close()V"}
   )
   private void bytephase$flushBeforeClose(CallbackInfo callbackInfo) {
      BytephaseRoundedUtils.updateRenderThread();
   }
}
