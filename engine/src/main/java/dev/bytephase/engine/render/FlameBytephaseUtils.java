package dev.bytephase.engine.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.ShapeIndexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.BuiltBuffer.DrawParameters;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import ai.auth.Module;
import dev.bytephase.engine.utils.MinimalSharpUtils;

public final class FlameBytephaseUtils {
   private static GpuBuffer gpuBuffer;
   public static final RenderPipeline build = RenderPipeline.builder()
      .withLocation(Identifier.of("bytephase", "pipeline/flame"))
      .withVertexShader(Identifier.of("bytephase", "core/bytephase_flame"))
      .withFragmentShader(Identifier.of("bytephase", "core/bytephase_flame"))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withUniform("BytephaseFlame", UniformType.UNIFORM_BUFFER)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withCull(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .build();
   private static GpuBuffer deviceCreate;
   private static final Matrix4f MATRIX4F = new Matrix4f();
   private static boolean flameBytephaseActive;
   private static final ByteBuffer order = ByteBuffer.allocateDirect(96).order(ByteOrder.nativeOrder());

   public static void updateRegisterBuild() {
      if (MinimalSharpUtils.canFlagForFlag(flameBytephaseActive)) {
         flameBytephaseActive = true;
         RenderPipelines.register(build);
      }
   }

   private static GpuBuffer getDeviceCreate(GpuBuffer deviceCreate, String deviceCreateText, int deviceCreateValue, int remaining) {
      if (deviceCreate != null && deviceCreate.size() >= remaining) {
         return deviceCreate;
      } else {
         if (deviceCreate != null) {
            deviceCreate.close();
         }

         return RenderSystem.getDevice().createBuffer(() -> deviceCreateText, deviceCreateValue, remaining);
      }
   }

   private FlameBytephaseUtils() {
   }

   public static boolean canFlag(
      float flameBytephaseValue,
      float flameBytephaseFactor,
      float panelSamplerValue,
      float flameBytephaseBound,
      float flameBytephaseValue3,
      float flameBytephaseValue4,
      float flameBytephaseValue5,
      float flameBytephaseValue6,
      float flameBytephaseValue7,
      float flameBytephaseValue8,
      float flameBytephaseValue9,
      float flameBytephaseValue10,
      float flameBytephaseValue11,
      float flameBytephaseValue12,
      float flameBytephaseValue13,
      float flameBytephaseValue14,
      float flameBytephaseValue15,
      float flameBytephaseValue16,
      float flameBytephaseValue17,
      float flameBytephaseValue18,
      float flameBytephaseValue19,
      float flameBytephaseValue20
   ) {
      return MinimalSharpUtils.isBoolean(
         () -> {
            updateRegisterBuild();
            float flameBytephaseValue21 = flameBytephaseFactor * (1.0F - flameBytephaseValue20);
            float flameBytephaseValue22 = panelSamplerValue / Math.max(1.0F, flameBytephaseBound);
            ByteBuffer order2 = order;
            order2.putFloat(0, flameBytephaseValue);
            order2.putFloat(4, flameBytephaseFactor);
            order2.putFloat(8, panelSamplerValue);
            order2.putFloat(12, flameBytephaseBound);
            order2.putFloat(16, flameBytephaseValue3);
            order2.putFloat(20, flameBytephaseValue4);
            order2.putFloat(24, flameBytephaseValue5);
            order2.putFloat(28, flameBytephaseValue6);
            order2.putFloat(32, flameBytephaseValue7);
            order2.putFloat(36, flameBytephaseValue8);
            order2.putFloat(40, flameBytephaseValue9);
            order2.putFloat(44, 1.0F);
            order2.putFloat(48, flameBytephaseValue10);
            order2.putFloat(52, flameBytephaseValue11);
            order2.putFloat(56, flameBytephaseValue12);
            order2.putFloat(60, flameBytephaseValue13);
            order2.putFloat(64, flameBytephaseValue14);
            order2.putFloat(68, flameBytephaseValue15);
            order2.putFloat(72, flameBytephaseValue16);
            order2.putFloat(76, flameBytephaseValue17);
            order2.putFloat(80, flameBytephaseValue18);
            order2.putFloat(84, flameBytephaseValue19);
            order2.putFloat(88, flameBytephaseValue22);
            order2.putFloat(92, flameBytephaseValue20);
            BufferBuilder begin = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION);
            begin.vertex(MATRIX4F, 0.0F, flameBytephaseValue21, 0.0F);
            begin.vertex(MATRIX4F, 0.0F, flameBytephaseFactor, 0.0F);
            begin.vertex(MATRIX4F, flameBytephaseValue, flameBytephaseFactor, 0.0F);
            begin.vertex(MATRIX4F, flameBytephaseValue, flameBytephaseValue21, 0.0F);
            GpuDevice device = RenderSystem.getDevice();

            try (BuiltBuffer end = begin.end()) {
               DrawParameters drawParameters = end.getDrawParameters();
               ByteBuffer buffer = end.getBuffer();
               int remaining = buffer.remaining();
               deviceCreate = getDeviceCreate(deviceCreate, "bytephase_flame_vtx", 40, remaining);
               gpuBuffer = getDeviceCreate(gpuBuffer, "bytephase_flame_ubo", 136, 96);
               ShapeIndexBuffer sequentialBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
               GpuBuffer indexBuffer = sequentialBuffer.getIndexBuffer(drawParameters.indexCount());
               Framebuffer framebuffer = Module.mc.getFramebuffer();
               CommandEncoder commandEncoder = device.createCommandEncoder();
               commandEncoder.writeToBuffer(deviceCreate.slice(0L, remaining), buffer);
               commandEncoder.writeToBuffer(gpuBuffer.slice(0L, 96L), order2);

               try (RenderPass renderPass = commandEncoder.createRenderPass(
                     () -> "bytephase_flame", framebuffer.getColorAttachmentView(), OptionalInt.empty(), framebuffer.getDepthAttachmentView(), OptionalDouble.empty()
                  )) {
                  renderPass.setPipeline(build);
                  MatrixWindowUtils.handleDequeScissor(renderPass);
                  renderPass.setUniform("BytephaseFlame", gpuBuffer);
                  renderPass.setVertexBuffer(0, deviceCreate);
                  renderPass.setIndexBuffer(indexBuffer, sequentialBuffer.getIndexType());
                  renderPass.drawIndexed(0, 0, drawParameters.indexCount(), 1);
               }
            }

            return true;
         }
      );
   }
}
