package dev.bytephase.engine.mixins;

import dev.bytephase.engine.render.ClampMatrixUtils;
import dev.bytephase.engine.render.FramebufferSimpleUtils;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.fog.FogRenderer.FogType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.bytephase.engine.categories.misc.FOVSlider;
import dev.bytephase.engine.categories.misc.Zoom;
import dev.bytephase.engine.categories.movement.Freecam;
import dev.bytephase.engine.utils.FpsHistory;
import dev.bytephase.engine.categories.visuals.NameTags;
import ai.auth.Module;
import ai.auth.ModuleRegistry;
import dev.bytephase.engine.utils.FovOptionsUtils;

@Mixin({GameRenderer.class})
public abstract class MixinGameRenderer {
   @Shadow
   @Final
   private Camera camera;
   @Shadow
   @Final
   private GuiRenderer guiRenderer;
   @Shadow
   @Final
   private FogRenderer fogRenderer;

   @ModifyExpressionValue(
      method = {"renderHand(FZLorg/joml/Matrix4f;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z"
      )}
   )
   private boolean bytephaseFreecamHands(boolean bytephaseFreecamFlag) {
      if (Freecam.isInstanceFlag()) {
         return Freecam.isBaseInstanceBooleanBase();
      } else {
         return ClampMatrixUtils.isFrameCamera() ? false : bytephaseFreecamFlag;
      }
   }

   @Inject(
      method = {"render(Lnet/minecraft/client/render/RenderTickCounter;Z)V"},
      at = {@At("HEAD")}
   )
   private void bytephase$gui3dFrame(RenderTickCounter renderTickCounter, boolean bytephasegui3dFrameFlag, CallbackInfo callbackInfo) {
      FpsHistory.updateTimeAmount();
      ClampMatrixUtils.updateFrameFlag();
   }

   @Inject(
      method = {"render(Lnet/minecraft/client/render/RenderTickCounter;Z)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/screen/Screen;renderWithTooltip(Lnet/minecraft/client/gui/DrawContext;IIF)V"
      )}
   )
   private void bytephase$gui3dBegin(RenderTickCounter renderTickCounter, boolean bytephasegui3dBeginFlag, CallbackInfo callbackInfo) {
      if (ClampMatrixUtils.isFlagFrame()) {
         try {
            this.guiRenderer.render(this.fogRenderer.getFogBuffer(FogType.NONE));
         } catch (Throwable e) {
            ClampMatrixUtils.handleFlag(e);
         }
      }

      FramebufferSimpleUtils.updateFramebufferSimple();
   }

   @Inject(
      method = {"render(Lnet/minecraft/client/render/RenderTickCounter;Z)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
         shift = Shift.AFTER
      )}
   )
   private void bytephase$gui3dEnd(RenderTickCounter renderTickCounter, boolean bytephasegui3dEndFlag, CallbackInfo callbackInfo) {
      FramebufferSimpleUtils.updateCompositeFramebuffer();
   }

   @Inject(
      method = {"render(Lnet/minecraft/client/render/RenderTickCounter;Z)V"},
      at = {@At("RETURN")}
   )
   private void bytephase$gui3dLeave(RenderTickCounter renderTickCounter, boolean bytephasegui3dLeaveFlag, CallbackInfo callbackInfo) {
      FramebufferSimpleUtils.updateCompositeBegin();
   }

   @Redirect(
      method = {"renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/RawProjectionMatrix;set(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
      )
   )
   private GpuBufferSlice bytephase_captureProjection(RawProjectionMatrix rawProjectionMatrix, Matrix4f matrix) {
      NameTags.MATRIX4F.set(matrix);
      return rawProjectionMatrix.set(matrix);
   }

   @Inject(
      method = {"renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V",
         ordinal = 1
      )}
   )
   private void onWorldRender(RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
      Camera cam = Module.mc.gameRenderer.getCamera();
      NameTags.MATRIX4_FMATRIX4F.identity();
      NameTags.MATRIX4F3.set(new Matrix4f().rotation(cam.getRotation().conjugate(new Quaternionf())));
   }

   @ModifyReturnValue(
      method = {"getFov(Lnet/minecraft/client/render/Camera;FZ)F"},
      at = {@At("RETURN")}
   )
   private float bytephase_customFovAndZoom(float bytephaseCustomValue, @Local(argsOnly = true) boolean bytephaseCustomFlag) {
      float result = bytephaseCustomValue;
      FOVSlider customFov = (FOVSlider)ModuleRegistry.getModule(FOVSlider.class);
      if (customFov != null && customFov.isEnabled()) {
         result = customFov.getFovTime(bytephaseCustomValue);
      }

      Zoom zoom = (Zoom)ModuleRegistry.getModule(Zoom.class);
      if (zoom != null) {
         double factor = zoom.getToggleHold();
         if (factor > 1.0001) {
            result = (float)(result / factor);
         }
      }

      if (bytephaseCustomFlag && ClampMatrixUtils.isFrameCamera()) {
         result = ClampMatrixUtils.getPanelsFrame(result);
      }

      if (bytephaseCustomFlag) {
         FovOptionsUtils.handleAmount(result);
      }

      return result;
   }

   @Inject(
      method = {"shouldRenderBlockOutline()Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onShouldRenderBlockOutline(CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
      if (Freecam.isInstanceFlag() || ClampMatrixUtils.isFrameCamera()) {
         callbackInfoReturnable.setReturnValue(false);
      }
   }
}
