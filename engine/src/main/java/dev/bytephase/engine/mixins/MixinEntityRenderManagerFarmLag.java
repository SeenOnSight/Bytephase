package dev.bytephase.engine.mixins;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.bytephase.engine.categories.visuals.NoFarmLag;

@Mixin({EntityRenderManager.class})
public class MixinEntityRenderManagerFarmLag {
   @Inject(
      method = {"shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private <E extends Entity> void bytephase$noFarmLagHide(E e, Frustum frustum, double bytephasenoFarmValue, double bytephasenoFarmValue2, double bytephasenoFarmValue3, CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
      NoFarmLag module = NoFarmLag.getNoFarmLag();
      if (module != null && module.canBase(e)) {
         callbackInfoReturnable.setReturnValue(false);
      }
   }
}
