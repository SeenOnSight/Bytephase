package dev.bytephase.engine.mixins;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.memory.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.utils.RenderAtmoUtils;

@Mixin({WorldRenderer.class})
public abstract class MixinWorldRendererAtmo {
   @Inject(
      method = {"render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"},
      at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/render/FrameGraphBuilder;run(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/FrameGraphBuilder$Profiler;)V", shift = At.Shift.AFTER)},
      require = 0
   )
   private void bytephase$atmoAfterFrameGraph(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline,
         Camera camera, Matrix4f positionMatrix, Matrix4f basicProjection, Matrix4f projection,
         GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
      RenderAtmoUtils.handleRenderLate(fogBuffer);
   }
}
