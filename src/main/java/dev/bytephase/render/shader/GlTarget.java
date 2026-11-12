package dev.bytephase.render.shader;

import com.mojang.blaze3d.opengl.GlStateManager;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/** Raw RGBA framebuffer used by the ESP and sky post-processing passes. */
public final class GlTarget {
    private int texture;
    private int framebuffer;
    private int width;
    private int height;
    private boolean floating;

    public int texture() {
        return texture;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean ensure(int requestedWidth, int requestedHeight, boolean useFloat) {
        requestedWidth = Math.max(1, requestedWidth);
        requestedHeight = Math.max(1, requestedHeight);
        if (texture != 0 && framebuffer != 0 && width == requestedWidth
                && height == requestedHeight && floating == useFloat) {
            return true;
        }

        close();
        int previousActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        texture = GlStateManager._genTexture();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GlStateManager._bindTexture(texture);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        int internalFormat = useFloat ? GL30.GL_RGBA16F : GL11.GL_RGBA8;
        int type = useFloat ? GL11.GL_FLOAT : GL11.GL_UNSIGNED_BYTE;
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat,
                requestedWidth, requestedHeight, 0, GL11.GL_RGBA, type, (ByteBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        GlStateManager._bindTexture(previousTexture);
        GL13.glActiveTexture(previousActive);
        GlStateManager._activeTexture(previousActive);

        framebuffer = GlStateManager.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, texture, 0);
        boolean complete = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
                == GL30.GL_FRAMEBUFFER_COMPLETE;
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        if (!complete) {
            close();
            return false;
        }
        width = requestedWidth;
        height = requestedHeight;
        floating = useFloat;
        return true;
    }

    public void bind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glViewport(0, 0, width, height);
        GlStateManager._viewport(0, 0, width, height);
    }

    /** Attaches an existing same-sized depth texture without taking ownership. */
    public boolean attachDepthTexture(int depthTexture) {
        if (framebuffer == 0 || depthTexture == 0 || !GL11.glIsTexture(depthTexture)) {
            return false;
        }
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, depthTexture, 0);
        boolean complete = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)
                == GL30.GL_FRAMEBUFFER_COMPLETE;
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        return complete;
    }

    public void clear() {
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT);
    }

    public void close() {
        if (framebuffer != 0) {
            GlStateManager._glDeleteFramebuffers(framebuffer);
            framebuffer = 0;
        }
        if (texture != 0) {
            GlStateManager._deleteTexture(texture);
            texture = 0;
        }
        width = 0;
        height = 0;
    }
}
