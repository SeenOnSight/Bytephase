package dev.bytephase.engine.render;

import dev.bytephase.engine.mixins.AccessorGlCommandEncoder;
import dev.bytephase.engine.render.DynMappableUtils.Vertices;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPass.RenderObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.ShapeIndexBuffer;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.BuiltBuffer.DrawParameters;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import ai.auth.Module;

public final class BytephaseRoundedUtils {
   private static boolean bytephaseRoundedActive;
   private static GpuSampler sampler;
   public static final RenderPipeline buildValid = RenderPipeline.builder()
      .withLocation(Identifier.of("bytephase", "pipeline/ambient"))
      .withVertexShader(Identifier.of("bytephase", "core/bytephase_ambient"))
      .withFragmentShader(Identifier.of("bytephase", "core/bytephase_ambient"))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withUniform("BytephaseAmbient", UniformType.UNIFORM_BUFFER)
      .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
      .withCull(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .build();
   public static final RenderPipeline buildCrosshairFramebuffer = RenderPipeline.builder()
      .withLocation(Identifier.of("bytephase", "pipeline/crosshair"))
      .withVertexShader(Identifier.of("bytephase", "core/bytephase_crosshair"))
      .withFragmentShader(Identifier.of("bytephase", "core/bytephase_crosshair"))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withUniform("BytephaseCrosshair", UniformType.UNIFORM_BUFFER)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withCull(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .build();
   private static float bytephaseRoundedBoundRectRounded;
   private static GpuTextureView attachmentView;
   private static float boundScale;
   public static final RenderPipeline buildRectRounded = RenderPipeline.builder()
      .withLocation(Identifier.of("bytephase", "pipeline/rounded_rect"))
      .withVertexShader(Identifier.of("bytephase", "core/rounded_rect"))
      .withFragmentShader(Identifier.of("bytephase", "core/rounded_rect"))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withUniform("BytephaseRoundedRect", UniformType.UNIFORM_BUFFER)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withCull(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .build();
   private static int indexCount;
   private static float bytephaseRoundedBound2RectRounded;
   public static final RenderPipeline rectRoundedRenderPipeline = RenderPipeline.builder()
      .withLocation(Identifier.of("bytephase", "pipeline/rounded_rect_glow"))
      .withVertexShader(Identifier.of("bytephase", "core/rounded_rect_glow"))
      .withFragmentShader(Identifier.of("bytephase", "core/rounded_rect_glow"))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withUniform("BytephaseRoundedRectGlow", UniformType.UNIFORM_BUFFER)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withCull(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .build();
   public static final RenderPipeline build = RenderPipeline.builder()
      .withLocation(Identifier.of("bytephase", "pipeline/textured"))
      .withVertexShader(Identifier.of("bytephase", "core/bytephase_textured"))
      .withFragmentShader(Identifier.of("bytephase", "core/bytephase_textured"))
      .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
      .withUniform("BytephaseTextured", UniformType.UNIFORM_BUFFER)
      .withSampler("Sampler0")
      .withBlend(BlendFunction.TRANSLUCENT)
      .withCull(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .build();
   public static final RenderPipeline buildTextureColor = RenderPipeline.builder()
      .withLocation(Identifier.of("bytephase", "pipeline/textured_rounded"))
      .withVertexShader(Identifier.of("bytephase", "core/bytephase_textured_rounded"))
      .withFragmentShader(Identifier.of("bytephase", "core/bytephase_textured_rounded"))
      .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
      .withUniform("BytephaseRoundedTex", UniformType.UNIFORM_BUFFER)
      .withSampler("Sampler0")
      .withBlend(BlendFunction.TRANSLUCENT)
      .withCull(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .build();
   private static int pendingScissorCount;
   private static GpuTextureView attachmentView4SamplerView;
   private static GpuSampler samplerSamplerLinear;
   public static final RenderPipeline buildMsdfBytephase = RenderPipeline.builder()
      .withLocation(Identifier.of("bytephase", "pipeline/msdf_text"))
      .withVertexShader(Identifier.of("bytephase", "core/bytephase_msdf_text"))
      .withFragmentShader(Identifier.of("bytephase", "core/bytephase_msdf_text"))
      .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
      .withUniform("BytephaseMsdf", UniformType.UNIFORM_BUFFER)
      .withSampler("Sampler0")
      .withBlend(BlendFunction.TRANSLUCENT)
      .withCull(false)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .build();
   private static int bufferCloseCount;
   private static float boundOffset;
   public static float scaledWidth = 0.0F;
   private static float rectRoundedAmount;
   public static float scaledHeight = 0.0F;
   public static float panelOverlayAmount = 1.0F;
   private static boolean samplerViewActive;
   private static final BytephaseRoundedUtils.Pending[] msdfGpuTextureHelpers = new BytephaseRoundedUtils.Pending[512];
   private static final BytephaseRoundedUtils.ScissorProbe SCISSOR_PROBE = new BytephaseRoundedUtils.ScissorProbe();
   private static final Supplier<String> SUPPLIER = () -> "bytephase_batch";

   public static boolean canFlag(Matrix4f matrix) {
      float bytephaseRoundedValue = 1.0E-5F;
      return Math.abs(matrix.m01()) <= 1.0E-5F
         && Math.abs(matrix.m10()) <= 1.0E-5F
         && Math.abs(matrix.m03()) <= 1.0E-5F
         && Math.abs(matrix.m13()) <= 1.0E-5F
         && Math.abs(matrix.m33() - 1.0F) <= 1.0E-5F
         && matrix.m00() > 1.0E-5F
         && matrix.m11() > 1.0E-5F;
   }

   public static int getPanelOverlay(int panelOverlayValue) {
      int panelOverlayFactor = panelOverlayValue >>> 24 & 0xFF;
      int panelOverlayBound = Math.round(panelOverlayFactor * panelOverlayAmount);
      return Math.max(0, Math.min(255, panelOverlayBound)) << 24 | panelOverlayValue & 16777215;
   }

   public static void handleTextureColor(
      Matrix4f matrix4f, float textureColorValue, float randomValue, float textureColorValue3, float textureColorValue4, float textureColorValue5, float textureColorValue6, float textureColorValue7, float textureColorValue8, GpuTextureView gpuTextureView, float bytephaseLicenseLimit, float textureColorLimit
   ) {
      if (gpuTextureView != null) {
         ByteBuffer framebufferWindow = getFramebufferWindow(matrix4f, textureColorValue, randomValue, textureColorValue3, textureColorValue4, bytephaseLicenseLimit);
         float textureColorValue10 = panelOverlayAmount * (textureColorLimit < 0.0F ? 0.0F : (textureColorLimit > 1.0F ? 1.0F : textureColorLimit));
         BufferBuilder begin = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
         begin.vertex(matrix4f, textureColorValue, textureColorValue4, 0.0F).texture(textureColorValue5, textureColorValue8).color(1.0F, 1.0F, 1.0F, textureColorValue10);
         begin.vertex(matrix4f, textureColorValue3, textureColorValue4, 0.0F).texture(textureColorValue7, textureColorValue8).color(1.0F, 1.0F, 1.0F, textureColorValue10);
         begin.vertex(matrix4f, textureColorValue3, randomValue, 0.0F).texture(textureColorValue7, textureColorValue6).color(1.0F, 1.0F, 1.0F, textureColorValue10);
         begin.vertex(matrix4f, textureColorValue, randomValue, 0.0F).texture(textureColorValue5, textureColorValue6).color(1.0F, 1.0F, 1.0F, textureColorValue10);
         handleBufferClose(begin.end(), buildTextureColor, "bytephase_frost", "BytephaseRoundedTex", framebufferWindow, gpuTextureView, getSamplerLinear());
      }
   }

   public static void updateRenderThread() {
      if (pendingScissorCount != 0 && !samplerViewActive && RenderSystem.isOnRenderThread()) {
         if (!(RenderSystem.getDevice().createCommandEncoder() instanceof AccessorGlCommandEncoder accessorGlCommandEncoder && accessorGlCommandEncoder.bytephaseRenderPassOpen())) {
            updateSamplerView();
         }
      }
   }

   public static void handleCrosshairFramebuffer(
      Matrix4f matrix4f,
      float crosshairFramebufferValue,
      float matrixStackValue,
      float peekPositionLimit,
      float crosshairFramebufferValue4,
      int crosshairFramebufferValue5,
      int halfBboxValue,
      float crosshairFramebufferValue7,
      float crosshairFramebufferValue8,
      float crosshairFramebufferValue9,
      float crosshairFramebufferValue10,
      float crosshairFramebufferValue11,
      float crosshairFramebufferValue12,
      float crosshairFramebufferValue13,
      float crosshairFramebufferValue14,
      float crosshairFramebufferValue15,
      float crosshairFramebufferValue16,
      float crosshairFramebufferValue17,
      float crosshairFramebufferValue18,
      float panelSamplerValue,
      float crosshairFramebufferValue20,
      float crosshairFramebufferValue21,
      float crosshairFramebufferValue22,
      float crosshairFramebufferValue23,
      float crosshairFramebufferValue24
   ) {
      ByteBuffer order = DynMappableUtils.getOrder(96);
      order.putFloat(0, getWidthWindow());
      order.putFloat(4, getHeightWindow());
      order.putFloat(8, Module.mc.getWindow().getFramebufferWidth());
      order.putFloat(12, Module.mc.getWindow().getFramebufferHeight());
      order.putFloat(16, crosshairFramebufferValue7);
      order.putFloat(20, crosshairFramebufferValue8);
      order.putFloat(24, crosshairFramebufferValue9);
      order.putFloat(28, crosshairFramebufferValue10);
      order.putFloat(32, crosshairFramebufferValue17);
      order.putFloat(36, crosshairFramebufferValue18);
      order.putFloat(40, panelSamplerValue);
      order.putFloat(44, crosshairFramebufferValue20);
      order.putFloat(48, crosshairFramebufferValue21);
      order.putFloat(52, crosshairFramebufferValue22);
      order.putFloat(56, crosshairFramebufferValue23);
      order.putFloat(60, crosshairFramebufferValue24);
      order.putFloat(64, crosshairFramebufferValue11);
      order.putFloat(68, crosshairFramebufferValue12);
      order.putFloat(72, crosshairFramebufferValue15);
      order.putFloat(76, crosshairFramebufferValue13);
      order.putFloat(80, crosshairFramebufferValue14);
      order.putFloat(84, crosshairFramebufferValue16);
      order.putInt(88, crosshairFramebufferValue5);
      order.putInt(92, halfBboxValue);
      handleVertexShader(buildCrosshairFramebuffer, "BytephaseCrosshair", order, matrix4f, crosshairFramebufferValue, matrixStackValue, peekPositionLimit, crosshairFramebufferValue4);
   }

   public static void handle(
      Matrix4f matrix4f,
      float bytephaseRoundedValue,
      float matrixStackValue,
      float peekPositionLimit,
      float bytephaseRoundedValue4,
      float rectRoundedFactor,
      float bytephaseRoundedValue6,
      float bytephaseRoundedValue7,
      float bytephaseRoundedValue8,
      float bytephaseRoundedValue9,
      float bytephaseRoundedValue10,
      float panelSamplerValue,
      float bytephaseRoundedValue12,
      float bytephaseRoundedValue13
   ) {
      handleRectRounded(matrix4f, bytephaseRoundedValue, matrixStackValue, peekPositionLimit, bytephaseRoundedValue4, rectRoundedFactor, bytephaseRoundedValue6, bytephaseRoundedValue7, bytephaseRoundedValue8, bytephaseRoundedValue9, 0.0F, bytephaseRoundedValue10, panelSamplerValue, bytephaseRoundedValue12, bytephaseRoundedValue13);
   }

   public static void handleRectRounded(
      Matrix4f matrix,
      float rectRoundedValue,
      float matrixStackValue,
      float peekPositionLimit,
      float rectRoundedValue4,
      float rectRoundedFactor,
      float rectRoundedFactor2,
      float rectRoundedValue5,
      float rectRoundedValue6,
      float rectRoundedValue7,
      float rectRoundedValue8,
      float rectRoundedValue9,
      float panelSamplerValue,
      float rectRoundedValue11,
      float rectRoundedValue12
   ) {
      if (canAmountMin(matrix)) {
         rectRoundedFactor = rectRoundedFactor * bytephaseRoundedBoundRectRounded + rectRoundedAmount;
         rectRoundedFactor2 = rectRoundedFactor2 * bytephaseRoundedBound2RectRounded + boundOffset;
         rectRoundedValue5 *= bytephaseRoundedBoundRectRounded;
         rectRoundedValue6 *= bytephaseRoundedBound2RectRounded;
         rectRoundedValue7 *= boundScale;
         rectRoundedValue8 *= boundScale;
      }

      ByteBuffer order = DynMappableUtils.getOrder(64);
      order.putFloat(0, getWidthWindow());
      order.putFloat(4, getHeightWindow());
      order.putFloat(8, Module.mc.getWindow().getFramebufferWidth());
      order.putFloat(12, Module.mc.getWindow().getFramebufferHeight());
      order.putFloat(16, rectRoundedFactor);
      order.putFloat(20, rectRoundedFactor2);
      order.putFloat(24, rectRoundedValue5);
      order.putFloat(28, rectRoundedValue6);
      order.putFloat(32, rectRoundedValue7);
      order.putFloat(36, rectRoundedValue8);
      order.putFloat(48, rectRoundedValue9);
      order.putFloat(52, panelSamplerValue);
      order.putFloat(56, rectRoundedValue11);
      order.putFloat(60, rectRoundedValue12);
      handleVertexShader(buildRectRounded, "BytephaseRoundedRect", order, matrix, rectRoundedValue, matrixStackValue, peekPositionLimit, rectRoundedValue4);
   }

   public static float getPanelOverlay(float panelOverlayLimit) {
      float panelOverlayAmount2 = panelOverlayAmount;
      float panelOverlayFactor = panelOverlayLimit < 0.0F ? 0.0F : (panelOverlayLimit > 1.0F ? 1.0F : panelOverlayLimit);
      panelOverlayAmount = panelOverlayAmount2 * panelOverlayFactor;
      return panelOverlayAmount2;
   }

   public static float getFramebufferHeight() {
      return getHeightWindow() / Module.mc.getWindow().getFramebufferHeight();
   }

   public static void updateRegisterBuild() {
      if (!bytephaseRoundedActive) {
         bytephaseRoundedActive = true;
         RenderPipelines.register(buildRectRounded);
         RenderPipelines.register(rectRoundedRenderPipeline);
         RenderPipelines.register(build);
         RenderPipelines.register(buildTextureColor);
         RenderPipelines.register(buildMsdfBytephase);
         RenderPipelines.register(buildCrosshairFramebuffer);
      }
   }

   private static void handleVertexShader(RenderPipeline renderPipeline, String vertexShaderText, ByteBuffer byteBuffer, Matrix4f matrix4f, float vertexShaderValue, float matrixStackValue, float peekPositionLimit, float vertexShaderValue4) {
      BufferBuilder begin = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION);
      begin.vertex(matrix4f, vertexShaderValue, matrixStackValue, 0.0F);
      begin.vertex(matrix4f, vertexShaderValue, vertexShaderValue4, 0.0F);
      begin.vertex(matrix4f, peekPositionLimit, vertexShaderValue4, 0.0F);
      begin.vertex(matrix4f, peekPositionLimit, matrixStackValue, 0.0F);
      handleBufferClose(begin.end(), renderPipeline, "bytephase_shader", vertexShaderText, byteBuffer, null, null);
   }

   private static void handleMsdfBytephase(BuiltBuffer builtBuffer, GpuTextureView gpuTextureView) {
      ByteBuffer order = DynMappableUtils.getOrder(16);
      order.putFloat(0, getWidthWindow());
      order.putFloat(4, getHeightWindow());
      handleBufferClose(builtBuffer, buildMsdfBytephase, "bytephase_msdf_text", "BytephaseMsdf", order, gpuTextureView, getSamplerLinear());
   }

   public static void handleTextureView(BuiltBuffer builtBuffer, Identifier id) {
      AbstractTexture texture = Module.mc.getTextureManager().getTexture(id);
      handleMsdfBytephase(builtBuffer, texture.getGlTextureView());
   }

   public static void handleTextureTextured(BuiltBuffer builtBuffer, Identifier id) {
      AbstractTexture texture = Module.mc.getTextureManager().getTexture(id);
      handleTexturedBytephase(builtBuffer, texture.getGlTextureView(), build, "bytephase_textured");
   }

   public static void handlePanelOverlay(float panelOverlay) {
      panelOverlayAmount = panelOverlay;
   }

   private static ByteBuffer getFramebufferWindow(Matrix4f matrix, float framebufferWindowValue, float randomValue, float framebufferWindowValue3, float framebufferWindowValue4, float bytephaseLicenseLimit) {
      float framebufferHeight = getFramebufferHeight();
      float framebufferWindowFactor = framebufferWindowValue / framebufferHeight;
      float panelSamplerValue = randomValue / framebufferHeight;
      float framebufferWindowValue6 = (framebufferWindowValue3 - framebufferWindowValue) / framebufferHeight;
      float framebufferWindowValue7 = (framebufferWindowValue4 - randomValue) / framebufferHeight;
      float framebufferWindowValue8 = bytephaseLicenseLimit / framebufferHeight;
      if (canAmountMin(matrix)) {
         framebufferWindowFactor = framebufferWindowFactor * bytephaseRoundedBoundRectRounded + rectRoundedAmount;
         panelSamplerValue = panelSamplerValue * bytephaseRoundedBound2RectRounded + boundOffset;
         framebufferWindowValue6 *= bytephaseRoundedBoundRectRounded;
         framebufferWindowValue7 *= bytephaseRoundedBound2RectRounded;
         framebufferWindowValue8 *= boundScale;
      }

      ByteBuffer order = DynMappableUtils.getOrder(48);
      order.putFloat(0, getWidthWindow());
      order.putFloat(4, getHeightWindow());
      order.putFloat(8, Module.mc.getWindow().getFramebufferWidth());
      order.putFloat(12, Module.mc.getWindow().getFramebufferHeight());
      order.putFloat(16, framebufferWindowFactor);
      order.putFloat(20, panelSamplerValue);
      order.putFloat(24, framebufferWindowValue6);
      order.putFloat(28, framebufferWindowValue7);
      order.putFloat(32, framebufferWindowValue8);
      return order;
   }

   public static boolean isValid() {
      return RenderSystem.getDevice().precompilePipeline(buildValid).isValid();
   }

   private static boolean canSamplerAttachment(
      RenderPipeline renderPipeline, String samplerText, GpuBufferSlice gpuBufferSlice, Vertices vertices, int indexCount2, GpuTextureView textureView, GpuSampler gpuSampler
   ) {
      if (textureView == null && !renderPipeline.getSamplers().isEmpty()) {
         return false;
      } else {
         Framebuffer framebuffer = Module.mc.getFramebuffer();
         GpuTextureView colorAttachmentView = framebuffer.getColorAttachmentView();
         GpuTextureView depthAttachmentView = framebuffer.getDepthAttachmentView();
         if (colorAttachmentView != null && !colorAttachmentView.isClosed() && (depthAttachmentView == null || !depthAttachmentView.isClosed())) {
            if (pendingScissorCount > 0 && (pendingScissorCount == 512 || colorAttachmentView != attachmentView || depthAttachmentView != attachmentView4SamplerView)) {
               updateSamplerView();
            }

            attachmentView = colorAttachmentView;
            attachmentView4SamplerView = depthAttachmentView;
            BytephaseRoundedUtils.Pending pending = msdfGpuTextureHelpers[pendingScissorCount];
            if (pending == null) {
               pending = msdfGpuTextureHelpers[pendingScissorCount] = new BytephaseRoundedUtils.Pending();
            }

            pending.renderPipeline = renderPipeline;
            pending.samplerText = samplerText;
            pending.gpuBufferSlice = gpuBufferSlice;
            pending.gpuBuffer = vertices.buffer();
            pending.baseVertex = vertices.baseVertex();
            pending.indexCount = indexCount2;
            pending.textureView = textureView;
            pending.gpuSampler = gpuSampler;
            SCISSOR_PROBE.scissorActive = false;
            MatrixWindowUtils.handleDequeScissor(SCISSOR_PROBE);
            pending.samplerAttachmentActive = SCISSOR_PROBE.scissorActive;
            pending.scissorX = SCISSOR_PROBE.scissorValue;
            pending.samplerValue = SCISSOR_PROBE.scissorProbeValue;
            pending.samplerAttachmentValue = SCISSOR_PROBE.scissorWidth;
            pending.samplerViewValue = SCISSOR_PROBE.samplerAttachmentValue;
            RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(indexCount2);
            if (indexCount2 > BytephaseRoundedUtils.indexCount) {
               BytephaseRoundedUtils.indexCount = indexCount2;
            }

            pendingScissorCount++;
            DynMappableUtils.samplerBytephaseCount++;
            return true;
         } else {
            return false;
         }
      }
   }

   public static void handleRectRounded(
      Matrix4f matrix,
      float rectRoundedValue,
      float matrixStackValue,
      float peekPositionLimit,
      float rectRoundedValue4,
      float rectRoundedFactor,
      float panelSamplerValue,
      float rectRoundedValue5,
      float rectRoundedValue6,
      float rectRoundedValue7,
      float rectRoundedValue8,
      float rectRoundedValue9,
      float rectRoundedValue10,
      float rectRoundedValue11,
      float rectRoundedValue12,
      float rectRoundedValue13,
      boolean rectRoundedFlag,
      float rectRoundedFactor3,
      float rectRoundedFactor4,
      float rectRoundedValue14,
      float rectRoundedValue15,
      float rectRoundedValue16,
      float rectRoundedValue17
   ) {
      if (canAmountMin(matrix)) {
         rectRoundedFactor = rectRoundedFactor * bytephaseRoundedBoundRectRounded + rectRoundedAmount;
         panelSamplerValue = panelSamplerValue * bytephaseRoundedBound2RectRounded + boundOffset;
         rectRoundedValue5 *= bytephaseRoundedBoundRectRounded;
         rectRoundedValue6 *= bytephaseRoundedBound2RectRounded;
         rectRoundedValue7 *= boundScale;
         rectRoundedValue12 *= boundScale;
         rectRoundedFactor3 = rectRoundedFactor3 * bytephaseRoundedBoundRectRounded + rectRoundedAmount;
         rectRoundedFactor4 = rectRoundedFactor4 * bytephaseRoundedBound2RectRounded + boundOffset;
         rectRoundedValue14 *= bytephaseRoundedBoundRectRounded;
         rectRoundedValue15 *= bytephaseRoundedBound2RectRounded;
         rectRoundedValue16 *= boundScale;
         rectRoundedValue17 *= boundScale;
      }

      ByteBuffer order = DynMappableUtils.getOrder(112);
      order.putFloat(0, getWidthWindow());
      order.putFloat(4, getHeightWindow());
      order.putFloat(8, Module.mc.getWindow().getFramebufferWidth());
      order.putFloat(12, Module.mc.getWindow().getFramebufferHeight());
      order.putFloat(16, rectRoundedFactor);
      order.putFloat(20, panelSamplerValue);
      order.putFloat(24, rectRoundedValue5);
      order.putFloat(28, rectRoundedValue6);
      order.putFloat(32, rectRoundedValue7);
      order.putFloat(36, rectRoundedValue12);
      order.putFloat(48, rectRoundedValue8);
      order.putFloat(52, rectRoundedValue9);
      order.putFloat(56, rectRoundedValue10);
      order.putFloat(60, rectRoundedValue11);
      order.putFloat(64, rectRoundedValue13);
      order.putInt(68, rectRoundedFlag ? 1 : 0);
      order.putFloat(80, rectRoundedFactor3);
      order.putFloat(84, rectRoundedFactor4);
      order.putFloat(88, rectRoundedValue14);
      order.putFloat(92, rectRoundedValue15);
      order.putFloat(96, rectRoundedValue16);
      order.putFloat(100, rectRoundedValue17);
      handleVertexShader(rectRoundedRenderPipeline, "BytephaseRoundedRectGlow", order, matrix, rectRoundedValue, matrixStackValue, peekPositionLimit, rectRoundedValue4);
   }

   public static void update() {
      bufferCloseCount++;
   }

   public static void handleTextureColor(
      Matrix4f matrix4f, float textureColorValue, float roundOffset, float textureColorValue3, float textureColorValue4, float peekPositionValue, float textureColorValue6, float textureColorValue7, float textureColorValue8, Identifier id, float textureColorValue9, float textureColorValue10, float textureColorValue11, float textureColorValue12
   ) {
      AbstractTexture texture = Module.mc.getTextureManager().getTexture(id);
      GpuTextureView glTextureView = texture.getGlTextureView();
      ByteBuffer order = DynMappableUtils.getOrder(16);
      order.putFloat(0, getWidthWindow());
      order.putFloat(4, getHeightWindow());
      BufferBuilder begin = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
      begin.vertex(matrix4f, textureColorValue, textureColorValue4, 0.0F).texture(peekPositionValue, textureColorValue8).color(textureColorValue9, textureColorValue10, textureColorValue11, textureColorValue12);
      begin.vertex(matrix4f, textureColorValue3, textureColorValue4, 0.0F).texture(textureColorValue7, textureColorValue8).color(textureColorValue9, textureColorValue10, textureColorValue11, textureColorValue12);
      begin.vertex(matrix4f, textureColorValue3, roundOffset, 0.0F).texture(textureColorValue7, textureColorValue6).color(textureColorValue9, textureColorValue10, textureColorValue11, textureColorValue12);
      begin.vertex(matrix4f, textureColorValue, roundOffset, 0.0F).texture(peekPositionValue, textureColorValue6).color(textureColorValue9, textureColorValue10, textureColorValue11, textureColorValue12);
      handleTexturedBytephase(begin.end(), glTextureView, build, "bytephase_textured");
   }

   private static float getWidthWindow() {
      return scaledWidth > 0.0F
         ? scaledWidth
         : (float)Module.mc.getWindow().getFramebufferWidth() / Module.mc.getWindow().getScaleFactor();
   }

   private BytephaseRoundedUtils() {
   }

   public static void handleTextureRounded(
      Matrix4f matrix4f, float textureRoundedValue, float randomValue, float textureRoundedValue3, float textureRoundedValue4, float textureRoundedValue5, float textureRoundedValue6, float textureRoundedValue7, float textureRoundedValue8, Identifier id, float bytephaseLicenseLimit
   ) {
      AbstractTexture texture = Module.mc.getTextureManager().getTexture(id);
      GpuTextureView glTextureView = texture.getGlTextureView();
      ByteBuffer framebufferWindow = getFramebufferWindow(matrix4f, textureRoundedValue, randomValue, textureRoundedValue3, textureRoundedValue4, bytephaseLicenseLimit);
      float panelOverlayAmount2 = panelOverlayAmount;
      BufferBuilder begin = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
      begin.vertex(matrix4f, textureRoundedValue, textureRoundedValue4, 0.0F).texture(textureRoundedValue5, textureRoundedValue8).color(1.0F, 1.0F, 1.0F, panelOverlayAmount2);
      begin.vertex(matrix4f, textureRoundedValue3, textureRoundedValue4, 0.0F).texture(textureRoundedValue7, textureRoundedValue8).color(1.0F, 1.0F, 1.0F, panelOverlayAmount2);
      begin.vertex(matrix4f, textureRoundedValue3, randomValue, 0.0F).texture(textureRoundedValue7, textureRoundedValue6).color(1.0F, 1.0F, 1.0F, panelOverlayAmount2);
      begin.vertex(matrix4f, textureRoundedValue, randomValue, 0.0F).texture(textureRoundedValue5, textureRoundedValue6).color(1.0F, 1.0F, 1.0F, panelOverlayAmount2);
      handleBufferClose(begin.end(), buildTextureColor, "bytephase_textured_rounded", "BytephaseRoundedTex", framebufferWindow, glTextureView, getSamplerNearest());
   }

   private static float getHeightWindow() {
      return scaledHeight > 0.0F
         ? scaledHeight
         : (float)Module.mc.getWindow().getFramebufferHeight() / Module.mc.getWindow().getScaleFactor();
   }

   public static void updateBufferCloseCount() {
      if (bufferCloseCount > 0 && --bufferCloseCount == 0) {
         updateSamplerView();
      }
   }

   public static void handle(float bytephaseRoundedValue, Runnable runnable) {
      float panelOverlay = getPanelOverlay(bytephaseRoundedValue);

      try {
         runnable.run();
      } finally {
         handlePanelOverlay(panelOverlay);
      }
   }

   private static GpuSampler getSamplerNearest() {
      if (sampler == null) {
         sampler = RenderSystem.getDevice()
            .createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty());
      }

      return sampler;
   }

   public static void handle(
      Matrix4f matrix4f,
      float bytephaseRoundedValue,
      float matrixStackValue,
      float peekPositionLimit,
      float bytephaseRoundedValue4,
      float rectRoundedFactor,
      float bytephaseRoundedValue6,
      float bytephaseRoundedValue7,
      float bytephaseRoundedValue8,
      float panelSamplerValue,
      float bytephaseRoundedValue10,
      float bytephaseRoundedValue11,
      float bytephaseRoundedValue12,
      float bytephaseRoundedValue13,
      float bytephaseRoundedValue14,
      float bytephaseRoundedValue15,
      boolean bytephaseRoundedFlag
   ) {
      handleRectRounded(matrix4f, bytephaseRoundedValue, matrixStackValue, peekPositionLimit, bytephaseRoundedValue4, rectRoundedFactor, bytephaseRoundedValue6, bytephaseRoundedValue7, bytephaseRoundedValue8, panelSamplerValue, bytephaseRoundedValue10, bytephaseRoundedValue11, bytephaseRoundedValue12, bytephaseRoundedValue13, bytephaseRoundedValue14, bytephaseRoundedValue15, bytephaseRoundedFlag, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
   }

   public static float getPanelOverlay() {
      return panelOverlayAmount;
   }

   public static void handleThreadRender(boolean threadRenderFlag) {
      if (pendingScissorCount != 0 && !samplerViewActive && !threadRenderFlag && RenderSystem.isOnRenderThread()) {
         updateSamplerView();
      }
   }

   private static void handleBufferClose(
      BuiltBuffer builtBuffer, RenderPipeline renderPipeline, String bufferCloseText, String samplerText, ByteBuffer byteBuffer, GpuTextureView gpuTextureView, GpuSampler gpuSampler
   ) {
      BuiltBuffer builtBuffer2 = builtBuffer;

      label70: {
         try {
            DrawParameters drawParameters = builtBuffer2.getDrawParameters();
            Vertices mappableRing = DynMappableUtils.getMappableRing(builtBuffer2.getBuffer(), renderPipeline.getVertexFormat().getVertexSize());
            GpuBufferSlice slice = DynMappableUtils.getSlice(byteBuffer);
            if (bufferCloseCount > 0 && canSamplerAttachment(renderPipeline, samplerText, slice, mappableRing, drawParameters.indexCount(), gpuTextureView, gpuSampler)) {
               break label70;
            }

            ShapeIndexBuffer sequentialBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
            GpuBuffer indexBuffer = sequentialBuffer.getIndexBuffer(drawParameters.indexCount());
            Framebuffer framebuffer = Module.mc.getFramebuffer();
            CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();

            try (RenderPass renderPass = commandEncoder.createRenderPass(
                  () -> bufferCloseText, framebuffer.getColorAttachmentView(), OptionalInt.empty(), framebuffer.getDepthAttachmentView(), OptionalDouble.empty()
               )) {
               DynMappableUtils.samplerBytephaseCount++;
               renderPass.setPipeline(renderPipeline);
               MatrixWindowUtils.handleDequeScissor(renderPass);
               renderPass.setUniform(samplerText, slice);
               if (gpuTextureView != null) {
                  renderPass.bindTexture("Sampler0", gpuTextureView, gpuSampler);
               }

               renderPass.setVertexBuffer(0, mappableRing.buffer());
               renderPass.setIndexBuffer(indexBuffer, sequentialBuffer.getIndexType());
               renderPass.drawIndexed(mappableRing.baseVertex(), 0, drawParameters.indexCount(), 1);
            }
         } catch (Throwable e) {
            if (builtBuffer != null) {
               try {
                  builtBuffer2.close();
               } catch (Throwable e2) {
                  e.addSuppressed(e2);
               }
            }

            throw e;
         }

         if (builtBuffer != null) {
            builtBuffer.close();
         }

         return;
      }

      if (builtBuffer != null) {
         builtBuffer.close();
      }
   }

   public static void handleAmbientFramebuffer(
      Matrix4f matrix, float ambientFramebufferFactor, float matrixStackValue, float introsoundFactor, float hashHuntBound, float wrapDegreesBound, float ambientFramebufferFactor6, float ambientFramebufferLimit, float[] ambientFramebufferValues, float[] ambientFramebufferValues2, int ambientFramebufferBound
   ) {
      if (!(introsoundFactor <= 0.0F) && !(hashHuntBound <= 0.0F) && !(ambientFramebufferLimit <= 0.0F)) {
         float ambientFramebufferFactor7 = 1.0F / getFramebufferHeight();
         boolean amountMin = canAmountMin(matrix);
         float ambientFramebufferFactor8 = amountMin ? bytephaseRoundedBoundRectRounded : 1.0F;
         float ambientFramebufferFactor9 = amountMin ? bytephaseRoundedBound2RectRounded : 1.0F;
         float ambientFramebufferFactor10 = amountMin ? boundScale : 1.0F;
         float ambientFramebufferValue = amountMin ? rectRoundedAmount : 0.0F;
         float ambientFramebufferValue2 = amountMin ? boundOffset : 0.0F;
         int max2 = Math.max(0, Math.min(6, ambientFramebufferBound));
         ByteBuffer order = DynMappableUtils.getOrder(240);
         order.putFloat(0, getWidthWindow());
         order.putFloat(4, getHeightWindow());
         order.putFloat(8, Module.mc.getWindow().getFramebufferWidth());
         order.putFloat(12, Module.mc.getWindow().getFramebufferHeight());
         order.putFloat(16, ambientFramebufferFactor * ambientFramebufferFactor7 * ambientFramebufferFactor8 + ambientFramebufferValue);
         order.putFloat(20, matrixStackValue * ambientFramebufferFactor7 * ambientFramebufferFactor9 + ambientFramebufferValue2);
         order.putFloat(24, introsoundFactor * ambientFramebufferFactor7 * ambientFramebufferFactor8);
         order.putFloat(28, hashHuntBound * ambientFramebufferFactor7 * ambientFramebufferFactor9);
         order.putFloat(32, wrapDegreesBound * ambientFramebufferFactor7 * ambientFramebufferFactor10);
         order.putFloat(36, ambientFramebufferFactor6 * ambientFramebufferFactor7 * ambientFramebufferFactor10);
         order.putFloat(40, ambientFramebufferLimit > 1.0F ? 1.0F : ambientFramebufferLimit);
         order.putFloat(44, 1.0F);

         for (int i = 0; i < 6; i++) {
            int ambientFramebufferValue3 = 48 + i * 16;
            int ambientFramebufferValue4 = 144 + i * 16;
            boolean ambientFramebufferFlag = i < max2;
            order.putFloat(ambientFramebufferValue3, ambientFramebufferFlag ? ambientFramebufferValues[i * 4] * ambientFramebufferFactor7 * ambientFramebufferFactor8 + ambientFramebufferValue : 0.0F);
            order.putFloat(ambientFramebufferValue3 + 4, ambientFramebufferFlag ? ambientFramebufferValues[i * 4 + 1] * ambientFramebufferFactor7 * ambientFramebufferFactor9 + ambientFramebufferValue2 : 0.0F);
            order.putFloat(ambientFramebufferValue3 + 8, ambientFramebufferFlag ? Math.max(1.0F, ambientFramebufferValues[i * 4 + 2] * ambientFramebufferFactor7 * ambientFramebufferFactor10) : 1.0F);
            order.putFloat(ambientFramebufferValue3 + 12, ambientFramebufferFlag ? ambientFramebufferValues[i * 4 + 3] : 0.0F);
            order.putFloat(ambientFramebufferValue4, ambientFramebufferFlag ? ambientFramebufferValues2[i * 3] : 0.0F);
            order.putFloat(ambientFramebufferValue4 + 4, ambientFramebufferFlag ? ambientFramebufferValues2[i * 3 + 1] : 0.0F);
            order.putFloat(ambientFramebufferValue4 + 8, ambientFramebufferFlag ? ambientFramebufferValues2[i * 3 + 2] : 0.0F);
            order.putFloat(ambientFramebufferValue4 + 12, 1.0F);
         }

         handleVertexShader(buildValid, "BytephaseAmbient", order, matrix, ambientFramebufferFactor, matrixStackValue, ambientFramebufferFactor + introsoundFactor, matrixStackValue + hashHuntBound);
      }
   }

   private static GpuSampler getSamplerLinear() {
      if (samplerSamplerLinear == null) {
         samplerSamplerLinear = RenderSystem.getDevice()
            .createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
      }

      return samplerSamplerLinear;
   }

   private static boolean canAmountMin(Matrix4f matrix) {
      float bytephaseRoundedBound = matrix.m00();
      float m11 = matrix.m11();
      float bytephaseRoundedFactor = matrix.m30();
      float m31 = matrix.m31();
      if (bytephaseRoundedBound == 1.0F && m11 == 1.0F && bytephaseRoundedFactor == 0.0F && m31 == 0.0F) {
         return false;
      } else if (!canFlag(matrix)) {
         return false;
      } else {
         float bytephaseRoundedFactor3 = 1.0F / getFramebufferHeight();
         rectRoundedAmount = bytephaseRoundedFactor * bytephaseRoundedFactor3;
         boundOffset = m31 * bytephaseRoundedFactor3;
         bytephaseRoundedBoundRectRounded = bytephaseRoundedBound;
         bytephaseRoundedBound2RectRounded = m11;
         boundScale = Math.min(bytephaseRoundedBound, m11);
         return true;
      }
   }

   public static void handleBufferTexture(GpuBuffer gpuBuffer, int bufferTextureLimit, Identifier id) {
      if (gpuBuffer != null && bufferTextureLimit > 0) {
         updateSamplerView();
         AbstractTexture texture = Module.mc.getTextureManager().getTexture(id);
         GpuTextureView glTextureView = texture.getGlTextureView();
         GpuDevice device = RenderSystem.getDevice();
         ByteBuffer order = DynMappableUtils.getOrder(16);
         order.putFloat(0, getWidthWindow());
         order.putFloat(4, getHeightWindow());
         GpuBufferSlice slice = DynMappableUtils.getSlice(order);
         ShapeIndexBuffer sequentialBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
         GpuBuffer indexBuffer = sequentialBuffer.getIndexBuffer(bufferTextureLimit);
         Framebuffer framebuffer = Module.mc.getFramebuffer();
         CommandEncoder commandEncoder = device.createCommandEncoder();

         try (RenderPass renderPass = commandEncoder.createRenderPass(
               () -> "bytephase_textured_cached", framebuffer.getColorAttachmentView(), OptionalInt.empty(), framebuffer.getDepthAttachmentView(), OptionalDouble.empty()
            )) {
            DynMappableUtils.samplerBytephaseCount++;
            renderPass.setPipeline(build);
            MatrixWindowUtils.handleDequeScissor(renderPass);
            renderPass.setUniform("BytephaseTextured", slice);
            renderPass.bindTexture("Sampler0", glTextureView, getSamplerLinear());
            renderPass.setVertexBuffer(0, gpuBuffer);
            renderPass.setIndexBuffer(indexBuffer, sequentialBuffer.getIndexType());
            renderPass.drawIndexed(0, 0, bufferTextureLimit, 1);
         }
      }
   }

   public static void updateSamplerView() {
      if (pendingScissorCount != 0 && !samplerViewActive) {
         samplerViewActive = true;
         boolean samplerViewFlag = false ;

         try {
            samplerViewFlag = true;
            ShapeIndexBuffer sequentialBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
            GpuBuffer indexBuffer = sequentialBuffer.getIndexBuffer(indexCount);
            IndexType indexType = sequentialBuffer.getIndexType();
            CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();

            try (RenderPass renderPass = commandEncoder.createRenderPass(SUPPLIER, attachmentView, OptionalInt.empty(), attachmentView4SamplerView, OptionalDouble.empty())) {
               RenderPipeline renderPipeline = null;

               for (int i = 0; i < pendingScissorCount; i++) {
                  BytephaseRoundedUtils.Pending pending = msdfGpuTextureHelpers[i];
                  if (pending.renderPipeline != renderPipeline) {
                     renderPass.setPipeline(pending.renderPipeline);
                     renderPipeline = pending.renderPipeline;
                  }

                  if (pending.samplerAttachmentActive) {
                     renderPass.enableScissor(pending.scissorX, pending.samplerValue, pending.samplerAttachmentValue, pending.samplerViewValue);
                  } else {
                     renderPass.disableScissor();
                  }

                  renderPass.setUniform(pending.samplerText, pending.gpuBufferSlice);
                  if (pending.textureView != null) {
                     renderPass.bindTexture("Sampler0", pending.textureView, pending.gpuSampler);
                  }

                  renderPass.setVertexBuffer(0, pending.gpuBuffer);
                  renderPass.setIndexBuffer(indexBuffer, indexType);
                  renderPass.drawIndexed(pending.baseVertex, 0, pending.indexCount, 1);
               }
            }
         } finally {
            if (samplerViewFlag) {
               for (int j = 0; j < pendingScissorCount; j++) {
                  msdfGpuTextureHelpers[j].updateGpuSampler();
               }

               pendingScissorCount = 0;
               indexCount = 0;
               attachmentView = null;
               attachmentView4SamplerView = null;
               samplerViewActive = false;
            }
         }

         for (int k = 0; k < pendingScissorCount; k++) {
            msdfGpuTextureHelpers[k].updateGpuSampler();
         }

         pendingScissorCount = 0;
         indexCount = 0;
         attachmentView = null;
         attachmentView4SamplerView = null;
         samplerViewActive = false;
      }
   }

   private static void handleTexturedBytephase(BuiltBuffer builtBuffer, GpuTextureView gpuTextureView, RenderPipeline renderPipeline, String texturedBytephaseText) {
      ByteBuffer order = DynMappableUtils.getOrder(16);
      order.putFloat(0, getWidthWindow());
      order.putFloat(4, getHeightWindow());
      handleBufferClose(builtBuffer, renderPipeline, texturedBytephaseText, "BytephaseTextured", order, gpuTextureView, getSamplerLinear());
   }

   private static final class Pending {
      GpuSampler gpuSampler;
      String samplerText;
      int samplerValue;
      int scissorX;
      int samplerAttachmentValue;
      int baseVertex;
      RenderPipeline renderPipeline;
      GpuTextureView textureView;
      GpuBuffer gpuBuffer;
      boolean samplerAttachmentActive;
      int indexCount;
      int samplerViewValue;
      GpuBufferSlice gpuBufferSlice;

      void updateGpuSampler() {
         this.renderPipeline = null;
         this.samplerText = null;
         this.gpuBufferSlice = null;
         this.gpuBuffer = null;
         this.textureView = null;
         this.gpuSampler = null;
      }
   }

   private static final class ScissorProbe implements RenderPass {
      int scissorWidth;
      int scissorValue;
      int samplerAttachmentValue;
      boolean scissorActive;
      int scissorProbeValue;

      @Override
      public void pushDebugGroup(Supplier<String> supplier) {
      }

      @Override
      public void drawIndexed(int indexedValue, int indexedValue2, int indexedValue3, int indexedValue4) {
      }

      @Override
      public void close() {
      }

      @Override
      public void setVertexBuffer(int vertexBufferValue, GpuBuffer gpuBuffer) {
      }

      @Override
      public void draw(int scissorProbeValue, int scissorProbeValue2) {
      }

      @Override
      public void setIndexBuffer(GpuBuffer gpuBuffer, IndexType indexType) {
      }

      @Override
      public void enableScissor(int value, int value2, int value3, int value4) {
         this.scissorActive = true;
         this.scissorValue = value;
         this.scissorProbeValue = value2;
         this.scissorWidth = value3;
         this.samplerAttachmentValue = value4;
      }

      @Override
      public void disableScissor() {
         this.scissorActive = false;
      }

      @Override
      public void setUniform(String uniformText, GpuBuffer gpuBuffer) {
      }

      @Override
      public void popDebugGroup() {
      }

      @Override
      public void bindTexture(String bindTextureText, GpuTextureView gpuTextureView, GpuSampler gpuSampler) {
      }

      @Override
      public void setUniform(String uniformText, GpuBufferSlice gpuBufferSlice) {
      }

      @Override
      public void setPipeline(RenderPipeline renderPipeline) {
      }

      @Override
      public <T> void drawMultipleIndexed(
         Collection<RenderObject<T>> collection, GpuBuffer gpuBuffer, IndexType indexType, Collection<String> collection2, T t
      ) {
      }
   }
}
