package dev.bytephase.engine.mixins;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.categories.combat.SilentAim;
import ai.auth.Module;

@Mixin({LivingEntityRenderer.class})
public class MixinLivingEntityRenderer {
   @Inject(
      method = {"updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V"},
      at = {@At("TAIL")}
   )
   private void bytephase_silentAimPose(LivingEntity renderedEntity, LivingEntityRenderState livingEntityRenderState, float tickDelta, CallbackInfo callbackInfo) {
      if (renderedEntity == Module.mc.player) {
         if (SilentAim.isRevealPoseActive()) {
            livingEntityRenderState.bodyYaw = SilentAim.getLockedYaw();
            livingEntityRenderState.relativeHeadYaw = 0.0F;
            livingEntityRenderState.pitch = SilentAim.getLockedPitch();
         }
      }
   }
}
