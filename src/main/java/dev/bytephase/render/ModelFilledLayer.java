package dev.bytephase.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import dev.bytephase.mixin.RenderLayerAccessor;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;

/** Solid-colour translucent entity models, independent of their skin textures. */
public final class ModelFilledLayer {
    private static final Identifier WHITE = Identifier.of("bytephase", "textures/entity/model_fill.png");
    private static final RenderLayer DEPTH = create(false);
    private static final RenderLayer THROUGH = create(true);
    private static final java.util.Map<Identifier, RenderLayer> ITEM_DEPTH = new java.util.HashMap<>();
    private static final java.util.Map<Identifier, RenderLayer> ITEM_THROUGH = new java.util.HashMap<>();

    private ModelFilledLayer() { }

    public static RenderLayer get(boolean throughWalls) {
        return throughWalls ? THROUGH : DEPTH;
    }

    public static RenderLayer item(Identifier atlas, boolean throughWalls) {
        return (throughWalls ? ITEM_THROUGH : ITEM_DEPTH).computeIfAbsent(atlas, key -> {
            RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation(Identifier.of("bytephase", "pipeline/item_filled_" + (throughWalls ? "through" : "depth")))
                    .withFragmentShader(Identifier.of("bytephase", "core/item_filled"))
                    .withSampler("Sampler1").withShaderDefine("PER_FACE_LIGHTING")
                    .withBlend(BlendFunction.TRANSLUCENT).withCull(false)
                    .withDepthTestFunction(throughWalls ? DepthTestFunction.NO_DEPTH_TEST : DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false).build();
            return RenderLayerAccessor.bytephase$of("bytephase_item_filled_" + atlas,
                    RenderSetup.builder(pipeline).texture("Sampler0", key).useLightmap().useOverlay()
                            .outputTarget(OutputTarget.MAIN_TARGET).translucent().expectedBufferSize(2048).build());
        });
    }

    private static RenderLayer create(boolean throughWalls) {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.of("bytephase", "pipeline/model_filled_" + (throughWalls ? "through" : "depth")))
                .withSampler("Sampler1")
                .withShaderDefine("PER_FACE_LIGHTING")
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(true)
                .withDepthTestFunction(throughWalls ? DepthTestFunction.NO_DEPTH_TEST : DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .build();
        return RenderLayerAccessor.bytephase$of("bytephase_model_filled_" + (throughWalls ? "through" : "depth"),
                RenderSetup.builder(pipeline).texture("Sampler0", WHITE).useLightmap().useOverlay()
                        .outputTarget(OutputTarget.MAIN_TARGET).translucent().expectedBufferSize(2048).build());
    }
}
