package dev.bytephase.engine.mixins;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.bytephase.engine.categories.combat.AutoTrap;
import dev.bytephase.engine.categories.misc.NameProtect;
import ai.auth.Module;
import ai.auth.ModuleRegistry;

@Mixin({EntityRenderer.class})
public class MixinEntityRenderer<T extends Entity> {
   @Inject(
      method = {"getBoundingBox(Lnet/minecraft/entity/Entity;)Lnet/minecraft/util/math/Box;"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void onGetBoundingBox(T t, CallbackInfoReturnable<Box> callbackInfoReturnable) {
      if (t != Module.mc.player) {
         if (t instanceof PlayerEntity) {
            AutoTrap autoTrap = (AutoTrap)ModuleRegistry.getModule(AutoTrap.class);
            if (autoTrap != null && autoTrap.isEnabled() && autoTrap.getHitboxExpand() > 0.0) {
               Box box = (Box)callbackInfoReturnable.getReturnValue();
               if (box != null) {
                  callbackInfoReturnable.setReturnValue(box.expand(autoTrap.getHitboxExpand()));
               }
            }
         }
      }
   }

   @Inject(
      method = {"updateRenderState(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V"},
      at = {@At("TAIL")}
   )
   private void bytephase_maskNameLabel(Entity entity, EntityRenderState entityRenderState, float bytephaseMaskValue, CallbackInfo callbackInfo) {
      if (entityRenderState.displayName != null) {
         if (entity == Module.mc.player || NameProtect.isFlag()) {
            entityRenderState.displayName = NameProtect.getDisplayName(entityRenderState.displayName);
         }
      }
   }
}
