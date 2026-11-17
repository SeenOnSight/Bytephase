package dev.bytephase.render.shader;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

/** Scopes tightly packed client-memory uploads to the current OpenGL context. */
public final class GlPixelUnpack implements AutoCloseable {
    private final int buffer;
    private final int alignment;
    private final int rowLength;
    private final int skipRows;
    private final int skipPixels;
    private final int imageHeight;
    private final int skipImages;
    private final int swapBytes;
    private final int lsbFirst;
    private boolean closed;

    private GlPixelUnpack() {
        buffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        imageHeight = GL11.glGetInteger(GL12.GL_UNPACK_IMAGE_HEIGHT);
        skipImages = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_IMAGES);
        swapBytes = GL11.glGetInteger(GL11.GL_UNPACK_SWAP_BYTES);
        lsbFirst = GL11.glGetInteger(GL11.GL_UNPACK_LSB_FIRST);

        // A bound PBO turns a ByteBuffer address (or null) into a buffer offset.
        // Atlas uploads can also leave row lengths and skips behind.
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, GL11.GL_FALSE);
        GL11.glPixelStorei(GL11.GL_UNPACK_LSB_FIRST, GL11.GL_FALSE);
    }

    public static GlPixelUnpack tightlyPacked() {
        return new GlPixelUnpack();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
        GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, imageHeight);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, skipImages);
        GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, swapBytes);
        GL11.glPixelStorei(GL11.GL_UNPACK_LSB_FIRST, lsbFirst);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, buffer);
    }
}
