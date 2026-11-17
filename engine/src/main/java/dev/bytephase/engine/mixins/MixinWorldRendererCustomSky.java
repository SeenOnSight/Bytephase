package dev.bytephase.engine.mixins;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.FramePass;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.categories.visuals.CustomSky;

@Mixin({WorldRenderer.class})
public abstract class MixinWorldRendererCustomSky {
   @Shadow
   @Final
   private DefaultFramebufferSet framebufferSet;

   @Inject(
      method = {"renderSky(Lnet/minecraft/client/render/FrameGraphBuilder;Lnet/minecraft/client/render/Camera;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephase$customSky(FrameGraphBuilder frameGraphBuilder, Camera camera, GpuBufferSlice gpuBufferSlice, CallbackInfo callbackInfo) {
      CustomSky sf = CustomSky.getCustomSky();
      if (sf != null) {
         float vis = sf.getAmount();
         if (!(vis < 1.0F)) {
            this.bytephase$addSkyPass(frameGraphBuilder, gpuBufferSlice, sf, 1.0F);
            callbackInfo.cancel();
         }
      }
   }

   @Inject(
      method = {"renderSky(Lnet/minecraft/client/render/FrameGraphBuilder;Lnet/minecraft/client/render/Camera;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"},
      at = {@At("RETURN")}
   )
   private void bytephase$customSkyFade(FrameGraphBuilder frameGraphBuilder, Camera camera, GpuBufferSlice gpuBufferSlice, CallbackInfo callbackInfo) {
      CustomSky sf = CustomSky.getCustomSky();
      if (sf != null) {
         float vis = sf.getEasedFade();
         if (!(vis <= 0.0F) && !(vis >= 1.0F)) {
            this.bytephase$addSkyPass(frameGraphBuilder, gpuBufferSlice, sf, vis);
         }
      }
   }

   @Unique
   private void bytephase$addSkyPass(FrameGraphBuilder frameGraphBuilder, GpuBufferSlice shaderFog, CustomSky customSky, float bytephaseaddSkyValue) {
      FramePass framePass = frameGraphBuilder.createPass("bytephase_custom_sky");
      this.framebufferSet.mainFramebuffer = framePass.transfer(this.framebufferSet.mainFramebuffer);
      framePass.setRenderer(() -> {
         RenderSystem.setShaderFog(shaderFog);
         customSky.handleFramebufferWindow(bytephaseaddSkyValue);
      });
   }

   @Inject(
      method = {"renderClouds(Lnet/minecraft/client/render/FrameGraphBuilder;Lnet/minecraft/client/option/CloudRenderMode;Lnet/minecraft/util/math/Vec3d;JFIF)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephase$customSkyClouds(
      FrameGraphBuilder frameGraphBuilder, CloudRenderMode cloudRenderMode, Vec3d vec, long bytephasecustomSkyValue, float bytephasecustomSkyValue2, int bytephasecustomSkyValue3, float bytephasecustomSkyValue4, CallbackInfo callbackInfo
   ) {
      CustomSky sf = CustomSky.getCustomSky();
      if (sf != null && sf.isMax()) {
         callbackInfo.cancel();
      }
   }
}
