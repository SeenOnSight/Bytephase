package dev.bytephase.render.shader;

import com.mojang.blaze3d.opengl.GlStateManager;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

/** Shared fullscreen triangle used by the Clarity post-processing passes. */
public final class FullscreenQuad {
    private static int vao;
    private static int vbo;
    // Apple's Metal-backed GL driver can still be reading the client buffer
    // after glBufferData returns, so the vertex data outlives the call.
    private static ByteBuffer vertexData;

    private FullscreenQuad() {
    }

    public static void draw() {
        ensure();
        GL30.glBindVertexArray(vao);
        GlStateManager._glBindVertexArray(vao);
        GlStateManager._drawArrays(GL11.GL_TRIANGLES, 0, 3);
        GL30.glBindVertexArray(0);
        GlStateManager._glBindVertexArray(0);
    }

    private static void ensure() {
        if (vao != 0) {
            return;
        }
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        vao = GlStateManager._glGenVertexArrays();
        vbo = GlStateManager._glGenBuffers();
        GL30.glBindVertexArray(vao);
        GlStateManager._glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        vertexData = MemoryUtil.memAlloc(9 * Float.BYTES);
        vertexData.putFloat(-1.0f).putFloat(-1.0f).putFloat(0.0f);
        vertexData.putFloat(3.0f).putFloat(-1.0f).putFloat(0.0f);
        vertexData.putFloat(-1.0f).putFloat(3.0f).putFloat(0.0f);
        vertexData.flip();
        GlStateManager._glBufferData(GL15.GL_ARRAY_BUFFER, vertexData, GL15.GL_STATIC_DRAW);
        GlStateManager._enableVertexAttribArray(0);
        GlStateManager._vertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0L);
        // Restoring both bindings keeps GlStateManager's cache aligned with
        // the raw GL state; otherwise a later buffer upload can silently land
        // in the fullscreen VBO instead of the caller's buffer.
        GL30.glBindVertexArray(previousVao);
        GlStateManager._glBindVertexArray(previousVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
    }

    public static void dispose() {
        if (vbo != 0) {
            GlStateManager._glDeleteBuffers(vbo);
            vbo = 0;
        }
        if (vertexData != null) {
            MemoryUtil.memFree(vertexData);
            vertexData = null;
        }
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
            vao = 0;
        }
    }
}
