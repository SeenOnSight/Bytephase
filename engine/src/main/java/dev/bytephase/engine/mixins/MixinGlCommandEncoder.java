package dev.bytephase.engine.mixins;

import dev.bytephase.engine.render.BytephaseRoundedUtils;
import net.minecraft.client.gl.GlCommandEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({GlCommandEncoder.class})
public abstract class MixinGlCommandEncoder {
   @Shadow
   private boolean renderPassOpen;

   @Inject(
      at = {@At("HEAD")},
      method = {"createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;)Lcom/mojang/blaze3d/systems/RenderPass;", "createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;", "createFence()Lcom/mojang/blaze3d/buffers/GpuFence;", "timerQueryBegin()Lcom/mojang/blaze3d/systems/GpuQuery;"}
   )
   private void bytephase$flushBeforePass(CallbackInfoReturnable<?> callbackInfoReturnable) {
      BytephaseRoundedUtils.handleThreadRender(this.renderPassOpen);
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"clearColorTexture(Lcom/mojang/blaze3d/textures/GpuTexture;I)V", "clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;ILcom/mojang/blaze3d/textures/GpuTexture;D)V", "clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;ILcom/mojang/blaze3d/textures/GpuTexture;DIIII)V", "clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V", "writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lnet/minecraft/client/texture/NativeImage;)V", "writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lnet/minecraft/client/texture/NativeImage;IIIIIIII)V", "writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Ljava/nio/ByteBuffer;Lnet/minecraft/client/texture/NativeImage$Format;IIIIII)V", "copyTextureToBuffer(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/buffers/GpuBuffer;JLjava/lang/Runnable;I)V", "copyTextureToBuffer(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/buffers/GpuBuffer;JLjava/lang/Runnable;IIIII)V", "copyTextureToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/textures/GpuTexture;IIIIIII)V", "presentTexture(Lcom/mojang/blaze3d/textures/GpuTextureView;)V", "timerQueryEnd(Lcom/mojang/blaze3d/systems/GpuQuery;)V"}
   )
   private void bytephase$flushBeforeCommand(CallbackInfo callbackInfo) {
      BytephaseRoundedUtils.handleThreadRender(this.renderPassOpen);
   }
}
