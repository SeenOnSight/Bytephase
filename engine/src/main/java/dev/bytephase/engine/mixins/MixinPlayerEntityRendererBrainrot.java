package dev.bytephase.engine.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.bytephase.engine.utils.RenderMatrixUtilsWarm;

@Mixin({PlayerEntityRenderer.class})
public class MixinPlayerEntityRendererBrainrot {
   @Inject(
      method = {"updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V"},
      at = {@At("TAIL")}
   )
   private void bytephase$captureBrainrot(PlayerLikeEntity playerLikeEntity, PlayerEntityRenderState playerEntityRenderState, float bytephasecaptureBrainrotValue, CallbackInfo callbackInfo) {
      RenderMatrixUtilsWarm.handleProgressPose(playerLikeEntity, playerEntityRenderState);
   }

   @Inject(
      method = {"shouldRenderFeatures(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephase$brainrotHidesFeatures(PlayerEntityRenderState playerEntityRenderState, CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
      if (RenderMatrixUtilsWarm.canInvisible(playerEntityRenderState)) {
         callbackInfoReturnable.setReturnValue(false);
      }
   }

   @WrapOperation(
      method = {"renderArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;Lnet/minecraft/client/model/ModelPart;Z)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModelPart(Lnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IILnet/minecraft/client/texture/Sprite;)V"
      )}
   )
   private void bytephase$brainrotArm(
      OrderedRenderCommandQueue orderedRenderCommandQueue, ModelPart modelPart, MatrixStack matrixStack, RenderLayer renderLayer, int bytephasebrainrotArmValue, int bytephasebrainrotArmValue2, Sprite sprite, Operation<Void> operation
   ) {
      PlayerEntityModel model = ((net.minecraft.client.render.entity.model.PlayerEntityModel) (Object) (((PlayerEntityRenderer)((net.minecraft.client.render.entity.PlayerEntityRenderer) (Object) (this))).getModel()));
      if (!RenderMatrixUtilsWarm.canPersonCustom(orderedRenderCommandQueue, modelPart, matrixStack, bytephasebrainrotArmValue, modelPart == model.rightArm)) {
         operation.call(new Object[]{orderedRenderCommandQueue, modelPart, matrixStack, renderLayer, bytephasebrainrotArmValue, bytephasebrainrotArmValue2, sprite});
      }
   }
}
