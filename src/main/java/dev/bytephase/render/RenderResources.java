package dev.bytephase.render;

import dev.bytephase.BytePhase;
import dev.bytephase.render.esp.BytephaseEspRenderer;
import dev.bytephase.render.shader.FullscreenQuad;
import dev.bytephase.render.shader.GlSamplers;
import dev.bytephase.render.shader.GlTextures;
import dev.bytephase.render.sky.BytephaseSkyRenderer;

/** Releases Bytephase-owned native and OpenGL rendering resources at shutdown. */
public final class RenderResources {
    private RenderResources() {
    }

    public static void dispose() {
        disposeOne("ClickGUI backdrop", dev.bytephase.render.ui.LiquidBackdrop::dispose);
        disposeOne("ClickGUI background image", dev.bytephase.render.ui.UiBackgroundImage::dispose);
        disposeOne("ESP renderer", BytephaseEspRenderer::dispose);
        disposeOne("target effect renderer", BytephaseTargetRenderer::dispose);
        disposeOne("crosshair renderer", ShaderCrosshairRenderer::dispose);
        disposeOne("sky renderer", BytephaseSkyRenderer::dispose);
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
