package dev.bytephase.engine.mixins;

import dev.bytephase.engine.render.ClampMatrixUtils;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.categories.visuals.CustomFlame;
import dev.bytephase.engine.categories.visuals.NoFlame;
import ai.auth.ModuleRegistry;
import dev.bytephase.engine.utils.MinimalSharpUtils;

@Mixin({InGameOverlayRenderer.class})
public class MixinInGameOverlayRenderer {
   @Inject(
      method = {"renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/texture/Sprite;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void onRenderFireOverlay(MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, Sprite sprite, CallbackInfo callbackInfo) {
      NoFlame noFlame = (NoFlame)ModuleRegistry.getModule(NoFlame.class);
      CustomFlame customFlame = (CustomFlame)ModuleRegistry.getModule(CustomFlame.class);
      boolean firstPerson = true;
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.options != null && client.options.getPerspective() != null) {
         firstPerson = client.options.getPerspective().isFirstPerson();
      }

      boolean cancel = MinimalSharpUtils.canFlag(
         customFlame != null && customFlame.isEnabled(), customFlame != null && customFlame.isBase(), firstPerson, noFlame != null && noFlame.isEnabled()
      );
      if (cancel) {
         callbackInfo.cancel();
      }
   }

   @ModifyExpressionValue(
      method = {"renderOverlays(ZFLnet/minecraft/client/render/command/OrderedRenderCommandQueue;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z"
      )}
   )
   private boolean bytephase$gui3dOverlays(boolean bytephasegui3dOverlaysFlag) {
      return bytephasegui3dOverlaysFlag && !ClampMatrixUtils.isFrameCamera();
   }
}
