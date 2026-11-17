package dev.bytephase.engine.mixins;

import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.utils.MatrixSubmitUtils;

@Mixin({PlayerEntityRenderer.class})
public class MixinPlayerEntityRendererCape {
   @Inject(
      method = {"updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V"},
      at = {@At("TAIL")}
   )
   private void bytephase$captureCape3d(PlayerLikeEntity playerLikeEntity, PlayerEntityRenderState playerEntityRenderState, float bytephasecaptureCape3dValue, CallbackInfo callbackInfo) {
      MatrixSubmitUtils.handleCaptureCape(playerLikeEntity, playerEntityRenderState, bytephasecaptureCape3dValue);
   }
}
