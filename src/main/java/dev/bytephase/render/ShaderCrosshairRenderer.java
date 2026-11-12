package dev.bytephase.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.bytephase.BytePhase;
import dev.bytephase.module.render.Crosshair;
import dev.bytephase.render.shader.FullscreenQuad;
import dev.bytephase.render.shader.GlShader;
import dev.bytephase.render.shader.GlState;
import dev.bytephase.render.shader.GlTarget;
import dev.bytephase.render.shader.GlTextures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.opengl.GL11;

/**
 * Draws the supplied Clarity crosshair shader into a small framebuffer and
 * hands that texture to the normal GUI renderer.
 */
public final class ShaderCrosshairRenderer {
    private static final Identifier TEXTURE_ID = Identifier.of("bytephase", "crosshair_overlay");
    private static final GlTarget TARGET = new GlTarget();
    private static final long START = System.nanoTime();

    private static GlShader shader;
    private static boolean failed;
    private static int registeredTexture;
    private static int registeredSize;
    private static AbstractTexture registeredTextureObject;
    private static float spread;
    private static float targetTint;
    private static float spinAngle;
    private static long lastNanos;

    private ShaderCrosshairRenderer() {
    }

    public static void dispose() {
        if (shader != null) {
            shader.delete();
            shader = null;
        }
        TARGET.close();
        registeredTexture = 0;
        registeredSize = 0;
        registeredTextureObject = null;
    }

    public static boolean render(net.minecraft.client.gui.DrawContext context, Crosshair module) {
        if (failed) {
            return false;
        }
        MinecraftClient mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.options == null
                || mc.options.hudHidden || mc.player.isSpectator()) {
            return true;
        }
        if (!mc.options.getPerspective().isFirstPerson()) {
            return true;
        }
        if (!ensureShader()) {
            return false;
        }

        long now = System.nanoTime();
        float delta = lastNanos == 0L ? 0.016f
                : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;

        float resolutionScale = Math.max(0.75f, mc.getWindow().getFramebufferHeight() / 1080.0f);
        float wantedSpread = 0.0f;
        if (module.dynamicSpread()) {
            double horizontalSpeed = Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z) * 20.0;
            wantedSpread = (float) Math.min(4.0, horizontalSpeed * 0.45)
                    + (mc.player.isOnGround() ? 0.0f : 2.5f);
        }
        spread += (wantedSpread - spread) * Math.min(1.0f, delta * 12.0f);

        boolean onEntity = mc.crosshairTarget instanceof EntityHitResult;
        float wantedTarget = module.targetTint() && onEntity ? 1.0f : 0.0f;
        targetTint += (wantedTarget - targetTint) * Math.min(1.0f, delta * 14.0f);
        spinAngle = (spinAngle + module.spinSpeed() * 90.0f * delta) % 360.0f;

        float cooldown = module.showCooldown()
                ? mc.player.getAttackCooldownProgress(0.0f) : 1.0f;
        float halfExtent = (module.length() + module.gap() + 22.0f
                + module.glow() * 8.0f + 4.0f) * resolutionScale;
        int guiSize = (int) Math.ceil((2.0f * halfExtent) / mc.getWindow().getScaleFactor());
        int textureSize = (int) Math.round((double) guiSize * mc.getWindow().getScaleFactor());
        if (!TARGET.ensure(textureSize, textureSize, false)) {
            return false;
        }
        registerTexture(textureSize);

        int scaledWidth = mc.getWindow().getScaledWidth();
        int scaledHeight = mc.getWindow().getScaledHeight();
        int guiX = (scaledWidth - guiSize) / 2;
        int guiY = (scaledHeight - guiSize) / 2;
        float offsetX = (float) (mc.getWindow().getFramebufferWidth() / 2.0
                - (guiX * (double) mc.getWindow().getScaleFactor() + textureSize / 2.0));
        float offsetY = (float) -(mc.getWindow().getFramebufferHeight() / 2.0
                - (guiY * (double) mc.getWindow().getScaleFactor() + textureSize / 2.0));

        try (GlState ignored = GlState.capture()) {
            TARGET.bind();
            TARGET.clear();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColorMask(true, true, true, true);

            shader.bind();
            shader.set2f("uRes", textureSize, textureSize);
            shader.set2f("uOffset", offsetX, offsetY);
            shader.set1f("uK", resolutionScale);
            shader.set1i("uStyle", styleIndex(module.style()));
            shader.set1f("uLen", module.length());
            shader.set1f("uGap", module.gap());
            shader.set1f("uThick", module.thickness());
            shader.set1f("uDot", module.dot() ? module.dotSize() : 0.0f);
            shader.set1f("uRot", module.rotation() + spinAngle);
            shader.set1f("uOutline", module.outline() ? module.outlineWidth() : 0.0f);
            shader.set1f("uGlow", module.glow());
            shader.set1f("uTime", (float) ((now - START) / 1.0e9 % 3600.0));
            shader.set1f("uSpread", spread);
            shader.set1i("uColorMode", colorModeIndex(module.colorMode()));
            shader.set3f("uCol1", module.primary().rgb());
            shader.set3f("uCol2", module.secondary().rgb());
            shader.set3f("uOutCol", 0.02f, 0.02f, 0.03f);
            shader.set3f("uHitCol", module.hitColor().rgb());
            shader.set3f("uTargetCol", module.targetColor().rgb());
            shader.set1f("uTarget", targetTint);
            shader.set1f("uHitAge", module.hitAgeSeconds(now));
            shader.set1f("uKillAge", module.killAgeSeconds(now));
            shader.set1i("uHitmarker", module.hitmarker() ? 1 : 0);
            shader.set1i("uExpand", module.expandOnHit() ? 1 : 0);
            shader.set1i("uFlash", module.flashOnHit() ? 1 : 0);
            shader.set1i("uShock", module.shockwave() ? 1 : 0);
            shader.set1f("uCooldown", cooldown);
            FullscreenQuad.draw();
            shader.unbind();
        } catch (Throwable error) {
            failed = true;
            BytePhase.LOGGER.error("Clarity crosshair shader pass failed", error);
            return false;
        }

        // Supply explicit u/v extents: the FBO texture is bottom-left, so the
        // source-style v range is textureSize..0. Using the full texture also
        // avoids sampling only the fraction the GUI scale would otherwise map.
        context.drawTexture(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, TEXTURE_ID,
                guiX, guiY, 0.0f, textureSize, guiSize, guiSize,
                textureSize, -textureSize, textureSize, textureSize);
        return true;
    }

    private static boolean ensureShader() {
        if (shader != null) {
            return true;
        }
        try {
            shader = GlShader.load("crosshair", "crosshair");
            return true;
        } catch (Throwable error) {
            failed = true;
            BytePhase.LOGGER.error("Could not load supplied crosshair shader", error);
            return false;
        }
    }

    private static void registerTexture(int size) {
        int texture = TARGET.texture();
        MinecraftClient mc = BytePhase.mc;
        if (texture == 0 || mc == null) {
            return;
        }
        if (registeredTexture == texture && registeredSize == size
                && registeredTextureObject != null
                && mc.getTextureManager().getTexture(TEXTURE_ID) == registeredTextureObject) {
            return;
        }
        GlTexture glTexture = new GlTextures.BridgeTexture(texture, size);
        GpuTextureView view = RenderSystem.getDevice().createTextureView(glTexture);
        GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        AbstractTexture bridge = new BridgeAbstractTexture(glTexture, view, sampler);
        mc.getTextureManager().registerTexture(TEXTURE_ID, bridge);
        registeredTexture = texture;
        registeredSize = size;
        registeredTextureObject = bridge;
    }

    private static int styleIndex(String style) {
        return switch (style) {
            case "Dot" -> 1;
            case "Circle" -> 2;
            case "Cross + Dot" -> 3;
            case "Segments" -> 4;
            case "Chevron" -> 5;
            case "Diamond" -> 6;
            case "Brackets" -> 7;
            default -> 0;
        };
    }

    private static int colorModeIndex(String mode) {
        return switch (mode) {
            case "Gradient" -> 1;
            case "Chroma" -> 2;
            default -> 0;
        };
    }

    private static final class BridgeAbstractTexture extends AbstractTexture {
        private BridgeAbstractTexture(GlTexture texture, GpuTextureView view, GpuSampler sampler) {
            this.glTexture = texture;
            this.glTextureView = view;
            this.sampler = sampler;
        }
    }
}
