package dev.bytephase.engine.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.ModelCommandRenderer.CrumblingOverlayCommand;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.bytephase.engine.utils.RenderMatrixUtilsWarm;

@Mixin({LivingEntityRenderer.class})
public class MixinLivingEntityRendererBrainrot {
   @WrapOperation(
      method = {"render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"
      )}
   )
   private void bytephase$brainrotBody(
      OrderedRenderCommandQueue orderedRenderCommandQueue,
      Model<?> model,
      Object obj,
      MatrixStack matrixStack,
      RenderLayer renderLayer,
      int bytephasebrainrotBodyValue,
      int bytephasebrainrotBodyValue2,
      int bytephasebrainrotBodyValue3,
      Sprite sprite,
      int vectorOutlineValue,
      CrumblingOverlayCommand crumblingOverlayCommand,
      Operation<Void> operation
   ) {
      if (!(obj instanceof PlayerEntityRenderState s && RenderMatrixUtilsWarm.canCustomSubmit(orderedRenderCommandQueue, s, matrixStack, renderLayer, bytephasebrainrotBodyValue, bytephasebrainrotBodyValue2, bytephasebrainrotBodyValue3, vectorOutlineValue))) {
         operation.call(new Object[]{orderedRenderCommandQueue, model, obj, matrixStack, renderLayer, bytephasebrainrotBodyValue, bytephasebrainrotBodyValue2, bytephasebrainrotBodyValue3, sprite, vectorOutlineValue, crumblingOverlayCommand});
      }
   }
}
