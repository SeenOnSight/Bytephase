package dev.bytephase.render.shader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;

/**
 * Minimal raw-GL program wrapper for the supplied Clarity shader assets.
 *
 * <p>The shader files themselves are shipped unchanged from the approved
 * visual source. This class only compiles and links them for the 1.21.11
 * renderer, where Clarity's original 26.1 bridge classes are unavailable.</p>
 */
public final class GlShader {
    // These are raw OpenGL shaders loaded directly from the mod classpath.
    // Keep them outside assets/<namespace>/shaders/, which Minecraft scans
    // and reloads as managed core shader sources.
    private static final String ROOT = "/assets/bytephase/clarity_shaders/";

    private final int programId;
    private final String label;
    private final Map<String, Integer> locations = new HashMap<>();
    // Reused by the render-thread uniform setters to avoid a fresh primitive
    // array every frame for matrix uniforms.
    private final float[] matrix4Scratch = new float[16];
    private final float[] matrix3Scratch = new float[9];

    private GlShader(String label, int programId) {
        this.label = label;
        this.programId = programId;
    }

    public static GlShader load(String directory, String name) {
        String vertex = read(directory + "/" + name + ".vsh");
        String fragment = read(directory + "/" + name + ".fsh");
        return compile(directory + "/" + name, vertex, fragment);
    }

    public static GlShader compile(String label, String vertexSource, String fragmentSource) {
        int vertex = compileStage(vertexSource, GL20.GL_VERTEX_SHADER, label + ".vsh");
        int fragment;
        try {
            fragment = compileStage(fragmentSource, GL20.GL_FRAGMENT_SHADER, label + ".fsh");
        } catch (RuntimeException e) {
            GL20.glDeleteShader(vertex);
            throw e;
        }

        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        // crosshair.vsh predates explicit layout locations; binding the same
        // location for shaders that do declare it is harmless.
        GL20.glBindAttribLocation(program, 0, "Position");
        GL20.glLinkProgram(program);
        GL20.glDetachShader(program, vertex);
        GL20.glDetachShader(program, fragment);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            throw new IllegalStateException("Failed to link " + label + ": " + log);
        }
        return new GlShader(label, program);
    }

    private static int compileStage(String source, int type, String label) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Failed to compile " + label + ": " + log);
        }
        return shader;
    }

    public static String read(String path) {
        try (InputStream stream = GlShader.class.getResourceAsStream(ROOT + path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing shader resource " + ROOT + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read shader resource " + ROOT + path, e);
        }
    }

    public void bind() {
        GL20.glUseProgram(programId);
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public int programId() {
        return programId;
    }

    public String label() {
        return label;
    }

    public int location(String name) {
        return locations.computeIfAbsent(name, key -> GL20.glGetUniformLocation(programId, key));
    }

    public void set1i(String name, int value) {
        int location = location(name);
        if (location >= 0) {
            GL20.glUniform1i(location, value);
        }
    }

    public void setBool(String name, boolean value) {
        set1i(name, value ? 1 : 0);
    }

    public void set1f(String name, float value) {
        int location = location(name);
        if (location >= 0) {
            GL20.glUniform1f(location, value);
        }
    }

    public void set2f(String name, float x, float y) {
        int location = location(name);
        if (location >= 0) {
            GL20.glUniform2f(location, x, y);
        }
    }

    public void set3f(String name, float x, float y, float z) {
        int location = location(name);
        if (location >= 0) {
            GL20.glUniform3f(location, x, y, z);
        }
    }

    public void set3f(String name, int rgb) {
        set3f(name, ((rgb >> 16) & 0xFF) / 255.0f,
                ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f);
    }

    public void set4f(String name, float x, float y, float z, float w) {
        int location = location(name);
        if (location >= 0) {
            GL20.glUniform4f(location, x, y, z, w);
        }
    }

    public void setMatrix4f(String name, Matrix4f matrix) {
        int location = location(name);
        if (location >= 0) {
            GL20.glUniformMatrix4fv(location, false, matrix.get(matrix4Scratch));
        }
    }

    public void setMatrix4f(String name, float[] matrix) {
        int location = location(name);
        if (location >= 0) {
            GL20.glUniformMatrix4fv(location, false, matrix);
        }
    }

    public void setMatrix3f(String name, Matrix3f matrix) {
        int location = location(name);
        if (location >= 0) {
            GL20.glUniformMatrix3fv(location, false, matrix.get(matrix3Scratch));
        }
    }

    public void setMatrix3f(String name, float[] matrix) {
        int location = location(name);
        if (location >= 0) {
            GL20.glUniformMatrix3fv(location, false, matrix);
        }
    }

    public void delete() {
        GL20.glDeleteProgram(programId);
    }
}
