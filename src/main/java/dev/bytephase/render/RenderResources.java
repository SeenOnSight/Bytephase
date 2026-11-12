package dev.bytephase.render;

import dev.bytephase.BytePhase;
import dev.bytephase.render.esp.ClarityEspRenderer;
import dev.bytephase.render.shader.FullscreenQuad;
import dev.bytephase.render.shader.GlSamplers;
import dev.bytephase.render.shader.GlTextures;
import dev.bytephase.render.sky.ClaritySkyRenderer;

/** Releases Bytephase-owned native and OpenGL rendering resources at shutdown. */
public final class RenderResources {
    private RenderResources() {
    }

    public static void dispose() {
        disposeOne("ESP renderer", ClarityEspRenderer::dispose);
        disposeOne("target effect renderer", ClarityTargetRenderer::dispose);
        disposeOne("crosshair renderer", ShaderCrosshairRenderer::dispose);
        disposeOne("sky renderer", ClaritySkyRenderer::dispose);
        disposeOne("fullscreen geometry", FullscreenQuad::dispose);
        disposeOne("samplers", GlSamplers::dispose);
        disposeOne("framebuffer bridges", GlTextures::dispose);
    }

    private static void disposeOne(String name, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (Throwable error) {
            BytePhase.LOGGER.warn("Could not dispose Bytephase {} resources", name, error);
        }
    }
}
