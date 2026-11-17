package dev.bytephase.engine.mixins;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.utils.WaterBytephaseUtils;

@Mixin({WorldRenderer.class})
public abstract class MixinWorldRendererWater {
   @Inject(
      method = {"scheduleChunkRender(IIIZ)V"},
      at = {@At("HEAD")},
      require = 0
   )
   private void bytephase$waterSectionDirty(int bytephasewaterSectionValue, int bytephasewaterSectionValue2, int bytephasewaterSectionValue3, boolean bytephasewaterSectionFlag, CallbackInfo callbackInfo) {
      WaterBytephaseUtils.handleChunkHash(bytephasewaterSectionValue, bytephasewaterSectionValue2, bytephasewaterSectionValue3);
   }
}
