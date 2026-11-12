package dev.bytephase.render.shader;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import java.lang.reflect.Method;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Small raw-GL texture/framebuffer bridge for the supplied shader passes. */
public final class GlTextures {
    private static int mainFbo;
    private static int scratchFbo;

    private GlTextures() {
    }

    public static int id(GpuTexture texture) {
        if (texture == null || texture.isClosed()) {
            return 0;
        }
        if (texture instanceof GlTexture glTexture) {
            return glTexture.getGlId();
        }
        // Some 1.21.11 builds expose a different concrete backend type. Keep
        // the fallback narrow so failures turn into harmless no-op renders.
        try {
            Method method = texture.getClass().getMethod("getGlId");
            return (Integer) method.invoke(texture);
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    public static int mainColorId() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null || mc.getFramebuffer() == null ? 0 : id(mc.getFramebuffer().getColorAttachment());
    }

    public static int mainDepthId() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null || mc.getFramebuffer() == null ? 0 : id(mc.getFramebuffer().getDepthAttachment());
    }

    public static int mainWidth() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null || mc.getFramebuffer() == null ? 0 : mc.getFramebuffer().textureWidth;
    }

    public static int mainHeight() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null || mc.getFramebuffer() == null ? 0 : mc.getFramebuffer().textureHeight;
    }

    /** Binds the vanilla main target through a temporary attachment FBO. */
    public static boolean bindMain() {
        if (!attachMain()) {
            return false;
        }
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GlStateManager._viewport(0, 0, mainWidth(), mainHeight());
        return true;
    }

    /**
     * Copies the current main colour attachment into a scratch texture via a
     * blit. Used only by the Clarity blur ESP style, whose Scene sampler must
     * not read the attachment it is writing to.
     */
    public static boolean blitMainTo(int texture, int width, int height) {
        if (texture == 0 || width <= 0 || height <= 0) {
            return false;
        }
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (!attachMain()) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            return false;
        }
        ensureScratchFbo();
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scratchFbo);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scratchFbo);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, texture, 0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GlStateManager._glBlitFrameBuffer(0, 0, mainWidth(), mainHeight(), 0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        return true;
    }

    private static boolean attachMain() {
        int color = mainColorId();
        int depth = mainDepthId();
        if (color == 0 || !GL11.glIsTexture(color)) {
            return false;
        }
        ensureMainFbo();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, color, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, depth != 0 && GL11.glIsTexture(depth) ? depth : 0, 0);
        return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
                == GL30.GL_FRAMEBUFFER_COMPLETE;
    }

    private static void ensureMainFbo() {
        if (mainFbo == 0) {
            mainFbo = GlStateManager.glGenFramebuffers();
        }
    }

    private static void ensureScratchFbo() {
        if (scratchFbo == 0) {
            scratchFbo = GlStateManager.glGenFramebuffers();
        }
    }

    public static void dispose() {
        if (mainFbo != 0) {
            GlStateManager._glDeleteFramebuffers(mainFbo);
            mainFbo = 0;
        }
        if (scratchFbo != 0) {
            GlStateManager._glDeleteFramebuffers(scratchFbo);
            scratchFbo = 0;
        }
    }

    public static final class BridgeTexture extends GlTexture {
        public BridgeTexture(int glId, int size) {
            super(GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    "bytephase-clarity-bridge", TextureFormat.RGBA8,
                    size, size, 1, 1, glId);
        }

        @Override
        public void close() {
            // The raw framebuffer owns this GL texture.
        }
    }
}
