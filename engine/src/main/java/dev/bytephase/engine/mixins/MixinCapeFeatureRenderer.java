package dev.bytephase.engine.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.ModelCommandRenderer.CrumblingOverlayCommand;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.equipment.EquipmentModel.LayerType;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import dev.bytephase.engine.utils.MatrixSubmitUtils;

@Mixin({CapeFeatureRenderer.class})
public abstract class MixinCapeFeatureRenderer {
   @Shadow
   private boolean hasCustomModelForLayer(ItemStack itemStack, LayerType layerType) {
      throw new AssertionError();
   }

   @WrapOperation(
      method = {"render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/PlayerEntityRenderState;FF)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"
      )}
   )
   private void bytephase$submitCape3d(
      OrderedRenderCommandQueue orderedRenderCommandQueue,
      Model<?> model,
      Object obj,
      MatrixStack matrixStack,
      RenderLayer renderLayer,
      int bytephasesubmitCape3dValue,
      int bytephasesubmitCape3dValue2,
      int bytephasesubmitCape3dValue3,
      CrumblingOverlayCommand crumblingOverlayCommand,
      Operation<Void> operation
   ) {
      if (!(
         obj instanceof PlayerEntityRenderState s
            && MatrixSubmitUtils.canFlag(s)
            && MatrixSubmitUtils.canSubmitCustom(
               orderedRenderCommandQueue, model, s, matrixStack, renderLayer, bytephasesubmitCape3dValue, bytephasesubmitCape3dValue2, bytephasesubmitCape3dValue3, this.hasCustomModelForLayer(s.equippedChestStack, LayerType.HUMANOID)
            )
      )) {
         operation.call(new Object[]{orderedRenderCommandQueue, model, obj, matrixStack, renderLayer, bytephasesubmitCape3dValue, bytephasesubmitCape3dValue2, bytephasesubmitCape3dValue3, crumblingOverlayCommand});
      }
   }
}
