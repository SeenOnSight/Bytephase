package dev.bytephase.engine.mixins;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.categories.visuals.NameTags;
import ai.auth.ModuleRegistry;

@Mixin({PlayerEntityRenderer.class})
public class MixinPlayerEntityRenderer {
   @Inject(
      method = {"renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephase_cancelPlayerLabel(
      PlayerEntityRenderState playerEntityRenderState, MatrixStack matrixStack, OrderedRenderCommandQueue orderedRenderCommandQueue, CameraRenderState cameraRenderState, CallbackInfo callbackInfo
   ) {
      NameTags nameTags = (NameTags)ModuleRegistry.getModule(NameTags.class);
      if (nameTags != null && nameTags.isFlag()) {
         callbackInfo.cancel();
      }
   }
}
