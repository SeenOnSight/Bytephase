package dev.bytephase.render.shader;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/**
 * Captures the raw GL state touched by the Clarity passes and restores it on
 * close. Keep this deliberately small: every field here is something the
 * shader renderers actually change.
 */
public final class GlState implements AutoCloseable {
    // capture() is called from active shader renderers every frame. Reuse the
    // tiny query buffers instead of allocating native direct buffers each time.
    private static final IntBuffer INT_QUERY = BufferUtils.createIntBuffer(4);
    private static final ByteBuffer BYTE_QUERY = BufferUtils.createByteBuffer(4);
    private static final FloatBuffer FLOAT_QUERY = BufferUtils.createFloatBuffer(4);

    private final int drawFramebuffer;
    private final int readFramebuffer;
    private final int drawBuffer;
    private final int readBuffer;
    private final int viewportX;
    private final int viewportY;
    private final int viewportWidth;
    private final int viewportHeight;
    private final int scissorX;
    private final int scissorY;
    private final int scissorWidth;
    private final int scissorHeight;
    private final int program;
    private final int vertexArray;
    private final int arrayBuffer;
    private final int activeTexture;
    private final int texture0;
    private final int texture1;
    private final int texture2;
    private final int texture3;
    private final int sampler0;
    private final int sampler1;
    private final int sampler2;
    private final int sampler3;
    private final boolean blend;
    private final boolean depthTest;
    private final boolean cull;
    private final boolean scissor;
    private final boolean depthMask;
    private final int colorMaskBits;
    private final int blendSrcRgb;
    private final int blendDstRgb;
    private final int blendSrcAlpha;
    private final int blendDstAlpha;
    private final int blendEquationRgb;
    private final int blendEquationAlpha;
    private final int depthFunction;
    private final int cullFace;
    private final int frontFace;
    private final float depthRangeNear;
    private final float depthRangeFar;
    private final float clearRed;
    private final float clearGreen;
    private final float clearBlue;
    private final float clearAlpha;
    private final GpuTextureView outputColorOverride;
    private final GpuTextureView outputDepthOverride;
    private boolean closed;

    private GlState() {
        RenderSystem.assertOnRenderThread();
        IntBuffer ints = INT_QUERY;
        ints.clear();
        this.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        this.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        this.drawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        this.readBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, ints);
        this.viewportX = ints.get(0);
        this.viewportY = ints.get(1);
        this.viewportWidth = ints.get(2);
        this.viewportHeight = ints.get(3);
        ints.clear();
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, ints);
        this.scissorX = ints.get(0);
        this.scissorY = ints.get(1);
        this.scissorWidth = ints.get(2);
        this.scissorHeight = ints.get(3);
        this.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        this.vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        this.arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        this.activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        this.texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        this.sampler0 = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        this.texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        this.sampler1 = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        GlStateManager._activeTexture(GL13.GL_TEXTURE2);
        this.texture2 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        this.sampler2 = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        GlStateManager._activeTexture(GL13.GL_TEXTURE3);
        this.texture3 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        this.sampler3 = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        GlStateManager._activeTexture(this.activeTexture);

        this.blend = GL11.glIsEnabled(GL11.GL_BLEND);
        this.depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        this.cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        this.scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        this.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        ByteBuffer mask = BYTE_QUERY;
        mask.clear();
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
        this.colorMaskBits = (mask.get(0) != 0 ? 1 : 0)
                | (mask.get(1) != 0 ? 2 : 0)
                | (mask.get(2) != 0 ? 4 : 0)
                | (mask.get(3) != 0 ? 8 : 0);
        this.blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        this.blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        this.blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        this.blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        this.blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        this.blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        this.depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        this.cullFace = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
        this.frontFace = GL11.glGetInteger(GL11.GL_FRONT_FACE);
        FloatBuffer floats = FLOAT_QUERY;
        floats.clear();
        GL11.glGetFloatv(GL11.GL_DEPTH_RANGE, floats);
        this.depthRangeNear = floats.get(0);
        this.depthRangeFar = floats.get(1);
        floats.clear();
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, floats);
        this.clearRed = floats.get(0);
        this.clearGreen = floats.get(1);
        this.clearBlue = floats.get(2);
        this.clearAlpha = floats.get(3);
        this.outputColorOverride = RenderSystem.outputColorTextureOverride;
        this.outputDepthOverride = RenderSystem.outputDepthTextureOverride;
    }

    public static GlState capture() {
        return new GlState();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        int draw = GL30.glIsFramebuffer(drawFramebuffer) ? drawFramebuffer : 0;
        int read = GL30.glIsFramebuffer(readFramebuffer) ? readFramebuffer : 0;
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GL11.glDrawBuffer(drawBuffer);
        GL11.glReadBuffer(readBuffer);
        GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
        GlStateManager._viewport(viewportX, viewportY, viewportWidth, viewportHeight);
        GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
        GlStateManager._scissorBox(scissorX, scissorY, scissorWidth, scissorHeight);
        GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        GlStateManager._blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        GL11.glDepthFunc(depthFunction);
        GlStateManager._depthFunc(depthFunction);
        GL11.glDepthMask(depthMask);
        GlStateManager._depthMask(depthMask);
        boolean red = (colorMaskBits & 1) != 0;
        boolean green = (colorMaskBits & 2) != 0;
        boolean blue = (colorMaskBits & 4) != 0;
        boolean alpha = (colorMaskBits & 8) != 0;
        GL11.glColorMask(red, green, blue, alpha);
        GlStateManager._colorMask(red, green, blue, alpha);
        GL11.glCullFace(cullFace);
        GL11.glFrontFace(frontFace);
        GL11.glDepthRange(depthRangeNear, depthRangeFar);
        GL11.glClearColor(clearRed, clearGreen, clearBlue, clearAlpha);
        setBlend(blend);
        setDepthTest(depthTest);
        setCull(cull);
        setScissor(scissor);
        GL20.glUseProgram(program);
        GlStateManager._glUseProgram(program);
        GL30.glBindVertexArray(vertexArray);
        GlStateManager._glBindVertexArray(vertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture0);
        GlStateManager._bindTexture(texture0);
        GL33.glBindSampler(0, sampler0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture1);
        GlStateManager._bindTexture(texture1);
        GL33.glBindSampler(1, sampler1);
        GlStateManager._activeTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture2);
        GlStateManager._bindTexture(texture2);
        GL33.glBindSampler(2, sampler2);
        GlStateManager._activeTexture(GL13.GL_TEXTURE3);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture3);
        GlStateManager._bindTexture(texture3);
        GL33.glBindSampler(3, sampler3);
        GlStateManager._activeTexture(activeTexture);
        GL13.glActiveTexture(activeTexture);
        RenderSystem.outputColorTextureOverride = outputColorOverride;
        RenderSystem.outputDepthTextureOverride = outputDepthOverride;
    }

    private static void setBlend(boolean enabled) {
        if (enabled) {
            GL11.glEnable(GL11.GL_BLEND);
            GlStateManager._enableBlend();
        } else {
            GL11.glDisable(GL11.GL_BLEND);
            GlStateManager._disableBlend();
        }
    }

    private static void setDepthTest(boolean enabled) {
        if (enabled) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GlStateManager._enableDepthTest();
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GlStateManager._disableDepthTest();
        }
    }

    private static void setCull(boolean enabled) {
        if (enabled) {
            GL11.glEnable(GL11.GL_CULL_FACE);
            GlStateManager._enableCull();
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
            GlStateManager._disableCull();
        }
    }

    private static void setScissor(boolean enabled) {
        if (enabled) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GlStateManager._enableScissorTest();
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager._disableScissorTest();
        }
    }
}
