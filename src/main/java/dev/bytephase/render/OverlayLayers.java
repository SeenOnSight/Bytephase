package dev.bytephase.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.bytephase.BytePhase;
import dev.bytephase.mixin.RenderLayerAccessor;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/**
 * Depth-disabled twins of the vanilla debug box/line layers.
 *
 * <p>ESP geometry has to survive behind terrain or it is useless in a real
 * fight. Vanilla's {@code lines()} and {@code debugFilledBox()} both depth-test,
 * so a wall hides the target. These layers use identical shaders, uniforms and
 * vertex formats but {@link DepthTestFunction#NO_DEPTH_TEST} and no depth
 * writes, so the geometry always lands on screen.</p>
 *
 * <p>The pipelines are not part of {@code RenderPipelines}' registry; the GL
 * backend compiles any pipeline object lazily on first use. If that ever
 * fails the accessors fall back to the vanilla layers so ESP stays visible
 * (just occluded), and the failure is logged once.</p>
 */
public final class OverlayLayers {
    private static boolean initialized;
    private static RenderLayer lines;
    private static RenderLayer filled;
    private static boolean failed;

    private OverlayLayers() {
    }

    /** No-depth line layer, or vanilla {@code lines()} if creation failed. */
    public static RenderLayer lines() {
        ensure();
        return lines != null ? lines : RenderLayers.lines();
    }

    /** No-depth quad layer, or vanilla {@code debugFilledBox()} if creation failed. */
    public static RenderLayer filled() {
        ensure();
        return filled != null ? filled : RenderLayers.debugFilledBox();
    }

    private static void ensure() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            lines = RenderLayerAccessor.bytephase$of("bytephase_overlay_lines",
                    RenderSetup.builder(linePipeline())
                            .layeringTransform(LayeringTransform.NO_LAYERING)
                            .outputTarget(OutputTarget.MAIN_TARGET)
                            .expectedBufferSize(1536)
                            .build());
            filled = RenderLayerAccessor.bytephase$of("bytephase_overlay_filled",
                    RenderSetup.builder(filledPipeline())
                            .layeringTransform(LayeringTransform.NO_LAYERING)
                            .outputTarget(OutputTarget.MAIN_TARGET)
                            .translucent()
                            .expectedBufferSize(1536)
                            .build());
        } catch (Throwable failure) {
            failed = true;
            lines = null;
            filled = null;
            BytePhase.LOGGER.error("No-depth ESP layers unavailable; falling back to depth-tested layers", failure);
        }
    }

    public static boolean active() {
        ensure();
        return !failed;
    }

    private static RenderPipeline linePipeline() {
        return RenderPipeline.builder()
                .withLocation(Identifier.of("bytephase", "pipeline/overlay_lines"))
                .withVertexShader("core/rendertype_lines")
                .withFragmentShader("core/rendertype_lines")
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("Fog", UniformType.UNIFORM_BUFFER)
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.DrawMode.LINES)
                .build();
    }

    private static RenderPipeline filledPipeline() {
        return RenderPipeline.builder()
                .withLocation(Identifier.of("bytephase", "pipeline/overlay_filled"))
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                .build();
    }
}
