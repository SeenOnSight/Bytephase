package dev.bytephase.render.font;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.bytephase.mixin.RenderLayerAccessor;
import net.minecraft.client.font.TextRenderLayerSet;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;

public final class DistanceFieldLayers {
    private static final RenderPipeline GUI = pipeline(RenderPipelines.GUI_TEXT_INTENSITY, "gui", true);
    private static final RenderPipeline WORLD = pipeline(RenderPipelines.RENDERTYPE_TEXT_INTENSITY, "world", true);
    private static final RenderPipeline THROUGH = pipeline(RenderPipelines.RENDERTYPE_TEXT_INTENSITY_SEETHROUGH, "through", false);

    private DistanceFieldLayers() { }

    public static TextRenderLayerSet create(Identifier atlas) {
        RenderLayer normal = layer(atlas, WORLD, "normal", false);
        RenderLayer through = layer(atlas, THROUGH, "through", true);
        RenderLayer offset = layer(atlas, WORLD, "offset", true);
        return new TextRenderLayerSet(normal, through, offset, GUI);
    }

    private static RenderLayer layer(Identifier atlas, RenderPipeline pipeline, String name, boolean translucent) {
        var setup = RenderSetup.builder(pipeline).texture("Sampler0", atlas,
            () -> RenderSystem.getSamplerCache().getRepeated(FilterMode.LINEAR)).useLightmap().expectedBufferSize(256);
        if (translucent) setup.translucent();
        return RenderLayerAccessor.bytephase$of("bytephase_distance_field_" + atlas + "_" + name, setup.build());
    }

    /** Retain Minecraft's text vertex format, uniforms, blending and depth rules. */
    private static RenderPipeline pipeline(RenderPipeline source, String name, boolean fog) {
        var builder = RenderPipeline.builder()
            .withLocation(Identifier.of("bytephase", "pipeline/text_distance_field_" + name))
            .withVertexShader(source.getVertexShader())
            .withFragmentShader(Identifier.of("bytephase", "core/text_distance_field"))
            .withVertexFormat(source.getVertexFormat(), source.getVertexFormatMode())
            .withDepthTestFunction(source.getDepthTestFunction())
            .withDepthWrite(source.isWriteDepth()).withCull(source.isCull())
            .withColorWrite(source.isWriteColor(), source.isWriteAlpha())
            .withPolygonMode(source.getPolygonMode()).withColorLogic(source.getColorLogic())
            .withDepthBias(source.getDepthBiasScaleFactor(), source.getDepthBiasConstant());
        source.getBlendFunction().ifPresent(builder::withBlend);
        source.getSamplers().forEach(builder::withSampler);
        for (var uniform : source.getUniforms()) {
            if (uniform.textureFormat() == null) builder.withUniform(uniform.name(), uniform.type());
            else builder.withUniform(uniform.name(), uniform.type(), uniform.textureFormat());
        }
        source.getShaderDefines().flags().forEach(builder::withShaderDefine);
        source.getShaderDefines().values().forEach((key, value) ->
            builder.withShaderDefine(key, Float.parseFloat(value)));
        if (!fog) builder.withShaderDefine("SDF_NO_FOG");
        return builder.build();
    }
}
