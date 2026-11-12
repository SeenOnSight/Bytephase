package dev.bytephase.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.PolygonMode;
import dev.bytephase.mixin.RenderLayerAccessor;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cached entity-textured wireframe layers used by Player ESP. */
public final class ModelWireframeLayer {
    // Player skins are dynamic resource identifiers. Keep common skins warm,
    // but do not retain one RenderLayer for every player ever seen.
    private static final int MAX_CACHED_TEXTURES = 512;
    private static final Map<Identifier, RenderLayer> THROUGH_WALLS = new LinkedHashMap<>(64, 0.75f, true);
    private static final Map<Identifier, RenderLayer> DEPTH_TESTED = new LinkedHashMap<>(64, 0.75f, true);
    private static final Map<Identifier, RenderLayer> SURFACES = new LinkedHashMap<>(64, 0.75f, true);
    private static final RenderPipeline SURFACE_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.of("bytephase", "pipeline/entity_model_surface_through"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build();
    private static final RenderPipeline THROUGH_WALLS_PIPELINE = createPipeline(true);
    private static final RenderPipeline DEPTH_TESTED_PIPELINE = createPipeline(false);

    private ModelWireframeLayer() {
    }

    public static RenderLayer get(Identifier texture, boolean throughWalls) {
        Map<Identifier, RenderLayer> cache = throughWalls ? THROUGH_WALLS : DEPTH_TESTED;
        RenderLayer layer = cache.get(texture);
        if (layer != null) {
            return layer;
        }
        layer = RenderLayerAccessor.bytephase$of(
                "bytephase_model_wireframe_" + (throughWalls ? "through_" : "depth_") + texture,
                RenderSetup.builder(throughWalls ? THROUGH_WALLS_PIPELINE : DEPTH_TESTED_PIPELINE)
                        .texture("Sampler0", texture)
                        .useLightmap()
                        .useOverlay()
                        .layeringTransform(LayeringTransform.NO_LAYERING)
                        .outputTarget(OutputTarget.MAIN_TARGET)
                        .outlineMode(RenderSetup.OutlineMode.AFFECTS_OUTLINE)
                        .translucent()
                        .expectedBufferSize(2048)
                        .build());
        cache.put(texture, layer);
        if (cache.size() > MAX_CACHED_TEXTURES) {
            Identifier eldest = cache.keySet().iterator().next();
            cache.remove(eldest);
        }
        return layer;
    }

    /** Textured translucent surface for chams/holograms behind terrain. */
    public static RenderLayer throughWallSurface(Identifier texture) {
        RenderLayer layer = SURFACES.get(texture);
        if (layer != null) return layer;
        layer = RenderLayerAccessor.bytephase$of("bytephase_model_surface_through_" + texture,
                RenderSetup.builder(SURFACE_PIPELINE)
                        .texture("Sampler0", texture)
                        .useLightmap()
                        .useOverlay()
                        .outputTarget(OutputTarget.MAIN_TARGET)
                        .outlineMode(RenderSetup.OutlineMode.AFFECTS_OUTLINE)
                        .translucent()
                        .expectedBufferSize(2048)
                        .build());
        SURFACES.put(texture, layer);
        if (SURFACES.size() > MAX_CACHED_TEXTURES) SURFACES.remove(SURFACES.keySet().iterator().next());
        return layer;
    }

    private static RenderPipeline createPipeline(boolean throughWalls) {
        return RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.of("bytephase", "pipeline/entity_model_wireframe_"
                        + (throughWalls ? "through" : "depth")))
                .withPolygonMode(PolygonMode.WIREFRAME)
                .withCull(false)
                .withDepthTestFunction(throughWalls
                        ? DepthTestFunction.NO_DEPTH_TEST : DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .build();
    }
}
