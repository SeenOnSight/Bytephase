package dev.bytephase.engine.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public final class GhostBuilderUtils {
   public static final RenderPipeline GHOST_BYTEPHASE = getGhostBytephase("ghost", DepthTestFunction.LEQUAL_DEPTH_TEST, true, true);
   public static final RenderPipeline GHOST_OCCLUDED_PIPELINE = getGhostBytephase("ghost_occluded", DepthTestFunction.GREATER_DEPTH_TEST, true, false);
   public static final RenderPipeline BYTEPHASE_SCHEM = getBytephaseSchem("overlay_quads", DepthTestFunction.LEQUAL_DEPTH_TEST, true);
   public static final RenderPipeline OVERLAY_QUADS_OCCLUDED_PIPELINE = getBytephaseSchem("overlay_quads_occluded", DepthTestFunction.GREATER_DEPTH_TEST, false);
   public static final RenderPipeline RENDER_PIPELINE = getDepthBias("outline", DepthTestFunction.LEQUAL_DEPTH_TEST, true);
   public static final RenderPipeline DEPTH_BIAS = getDepthBias("outline_occluded", DepthTestFunction.GREATER_DEPTH_TEST, false);

   public static void update() {
      BuilderSchemUtils.update();
   }

   private static Identifier getIdentifier(String identifierText) {
      return Identifier.of("bytephase", "pipeline/schem_" + identifierText);
   }

   private static RenderPipeline getDepthBias(String depthBiasText, DepthTestFunction depthTestFunction, boolean depthBiasFlag) {
      Builder withDepthWrite = RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
         .withLocation(getIdentifier(depthBiasText))
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .withDepthTestFunction(depthTestFunction)
         .withDepthWrite(false);
      if (depthBiasFlag) {
         withDepthWrite = withDepthWrite.withDepthBias(-1.2F, -0.2F);
      }

      return getRegister(withDepthWrite.build());
   }

   private static RenderPipeline getBytephaseSchem(String bytephaseSchemText, DepthTestFunction depthTestFunction, boolean bytephaseSchemFlag) {
      Builder withDepthWrite = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
         .withLocation(getIdentifier(bytephaseSchemText))
         .withVertexShader(getIdentifierForString("bytephase_schem_overlay"))
         .withFragmentShader(getIdentifierForString("bytephase_schem_overlay"))
         .withUniform("BytephaseGhostData", UniformType.UNIFORM_BUFFER)
         .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .withDepthTestFunction(depthTestFunction)
         .withDepthWrite(false);
      if (bytephaseSchemFlag) {
         withDepthWrite = withDepthWrite.withDepthBias(-0.4F, -0.8F);
      }

      return getRegister(withDepthWrite.build());
   }

   private static RenderPipeline getRegister(RenderPipeline renderPipeline) {
      return RenderPipelines.register(renderPipeline);
   }

   private static RenderPipeline getGhostBytephase(String ghostBytephaseText, DepthTestFunction depthTestFunction, boolean ghostBytephaseFlag, boolean ghostBytephaseFlag2) {
      Builder withDepthWrite = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
         .withLocation(getIdentifier(ghostBytephaseText))
         .withVertexShader(getIdentifierForString("bytephase_schem_ghost"))
         .withFragmentShader(getIdentifierForString("bytephase_schem_ghost"))
         .withSampler("Sampler0")
         .withUniform("BytephaseGhostData", UniformType.UNIFORM_BUFFER)
         .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, DrawMode.QUADS)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(ghostBytephaseFlag)
         .withDepthTestFunction(depthTestFunction)
         .withDepthWrite(false);
      if (ghostBytephaseFlag2) {
         withDepthWrite = withDepthWrite.withDepthBias(-0.3F, -0.6F);
      }

      return getRegister(withDepthWrite.build());
   }

   private GhostBuilderUtils() {
   }

   private static Identifier getIdentifierForString(String identifierText) {
      return Identifier.of("bytephase", "core/" + identifierText);
   }
}
