package dev.bytephase.render.shader;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL33;

/** Shared sampler objects for the raw shader passes. */
public final class GlSamplers {
    private static int linearClamp;
    private static int linearRepeat;

    private GlSamplers() {
    }

    public static int linearClamp() {
        if (linearClamp == 0) {
            linearClamp = create(GL12.GL_CLAMP_TO_EDGE);
        }
        return linearClamp;
    }

    public static int linearRepeat() {
        if (linearRepeat == 0) {
            linearRepeat = create(GL11.GL_REPEAT);
        }
        return linearRepeat;
    }

    /** Binds a texture and sampler to a unit without disturbing the active unit. */
    public static void bind(int unit, int texture, int sampler) {
        int previous = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int selected = GL13.GL_TEXTURE0 + unit;
        // Set both raw GL and the GlStateManager cache. A deleted texture or
        // recycled GL name can leave the cache thinking a bind is already in
        // place, so the raw calls are the source of truth here.
        GL13.glActiveTexture(selected);
        GlStateManager._activeTexture(selected);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GlStateManager._bindTexture(texture);
        GL33.glBindSampler(unit, sampler);
        GL13.glActiveTexture(previous);
        GlStateManager._activeTexture(previous);
    }

    public static void dispose() {
        if (linearClamp != 0) {
            GL33.glDeleteSamplers(linearClamp);
            linearClamp = 0;
        }
        if (linearRepeat != 0) {
            GL33.glDeleteSamplers(linearRepeat);
            linearRepeat = 0;
        }
    }

    private static int create(int wrap) {
        int sampler = GL33.glGenSamplers();
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_S, wrap);
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_T, wrap);
        return sampler;
    }
}
