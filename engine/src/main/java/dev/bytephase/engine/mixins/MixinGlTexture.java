package dev.bytephase.engine.mixins;

import dev.bytephase.engine.render.BytephaseRoundedUtils;
import net.minecraft.client.texture.GlTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({GlTexture.class})
public class MixinGlTexture {
   @Inject(
      at = {@At("HEAD")},
      method = {"close()V"}
   )
   private void bytephase$flushBeforeClose(CallbackInfo callbackInfo) {
      BytephaseRoundedUtils.updateRenderThread();
   }
}
