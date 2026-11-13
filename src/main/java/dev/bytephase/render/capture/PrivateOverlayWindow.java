package dev.bytephase.render.capture;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import dev.bytephase.render.shader.GlTextures;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeCocoa;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;

/** A distinct, noninteractive native window. The Minecraft window never receives this image. */
final class PrivateOverlayWindow implements AutoCloseable {
    private static final String VERTEX = """
        #version 150
        out vec2 uv;
        void main() {
            vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
            uv = p;
            gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
        }
        """;
    private static final String FRAGMENT = """
        #version 150
        uniform sampler2D privateImage;
        in vec2 uv;
        out vec4 color;
        void main() { color = texture(privateImage, uv); }
        """;
    private long window;
    private long parent;
    private GLCapabilities capabilities;
    private int image, imageWidth, imageHeight, program, vao;
    private long readyFence;
    private boolean shown;
    private boolean protectedWindow;
    private int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE, lastW, lastH;

    void create(long minecraftWindow) {
        if (window != 0) return;
        if (Platform.get() != Platform.WINDOWS && Platform.get() != Platform.MACOSX) {
            throw new IllegalStateException("StreamProof supports macOS and Windows.");
        }
        parent = minecraftWindow;
        if (GLFW.glfwGetCurrentContext() != parent) {
            throw new IllegalStateException("StreamProof needs Minecraft's render context.");
        }
        GLFW.glfwDefaultWindowHints();
        try {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FLOATING, GLFW.GLFW_TRUE);
            GLFW.glfwWindowHint(GLFW.GLFW_MOUSE_PASSTHROUGH, GLFW.GLFW_TRUE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
            window = GLFW.glfwCreateWindow(32, 32, "Bytephase Private View - do not capture", 0, parent);
        } finally { GLFW.glfwDefaultWindowHints(); }
        if (window == 0) throw new IllegalStateException("Could not create the private view window.");
        GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_MOUSE_PASSTHROUGH, GLFW.GLFW_TRUE);
        try {
            if (Platform.get() == Platform.WINDOWS) protectWindowsWindow();
            else configureCocoaWindow();
            withOverlayContext(() -> {
                capabilities = GL.createCapabilities();
                GLFW.glfwSwapInterval(0);
                program = shaderProgram();
                vao = GL30.glGenVertexArrays();
            });
        } catch (RuntimeException | LinkageError error) {
            close();
            throw error;
        }
    }

    private void protectWindowsWindow() {
        Pointer hwnd = new Pointer(GLFWNativeWin32.glfwGetWin32Window(window));
        NativeLibrary user32 = NativeLibrary.getInstance("user32");
        Function getStyle = user32.getFunction("GetWindowLongW", Function.ALT_CONVENTION);
        Function setStyle = user32.getFunction("SetWindowLongW", Function.ALT_CONVENTION);
        int style = getStyle.invokeInt(new Object[] {hwnd, -20});
        // A tool window should neither appear in Alt+Tab nor activate over Minecraft.
        setStyle.invokeInt(new Object[] {hwnd, -20, (style & ~0x00040000) | 0x80 | 0x08000000});
        Function affinity = user32.getFunction("SetWindowDisplayAffinity", Function.ALT_CONVENTION);
        // Set affinity only on our overlay. Excluding Minecraft would blank the whole recording.
        protectedWindow = affinity.invokeInt(new Object[] {hwnd, 0x11}) != 0;
        if (!protectedWindow) throw new IllegalStateException("Windows capture exclusion could not be enabled.");
    }

    private void configureCocoaWindow() {
        Pointer nsWindow = new Pointer(GLFWNativeCocoa.glfwGetCocoaWindow(window));
        NativeLibrary objc = NativeLibrary.getInstance("objc");
        Function selector = objc.getFunction("sel_registerName");
        Function message = objc.getFunction("objc_msgSend");
        // Window capture selects Minecraft's NSWindow independently of this companion window.
        // Do not claim NSWindow.sharingType prevents modern ScreenCaptureKit display capture.
        Pointer ignoresMouse = selector.invokePointer(new Object[] {"setIgnoresMouseEvents:"});
        message.invokeVoid(new Object[] {nsWindow, ignoresMouse, (byte) 1});
        Pointer deactivate = selector.invokePointer(new Object[] {"setHidesOnDeactivate:"});
        message.invokeVoid(new Object[] {nsWindow, deactivate, (byte) 1});
        Pointer collection = selector.invokePointer(new Object[] {"setCollectionBehavior:"});
        message.invokeVoid(new Object[] {nsWindow, collection, 1L | 64L | 256L});
        Pointer level = selector.invokePointer(new Object[] {"setLevel:"});
        message.invokeVoid(new Object[] {nsWindow, level, 25L});
    }

    boolean isProtectedWindow() { return protectedWindow; }

    boolean followParent() {
        if (window == 0) return false;
        if (GLFW.glfwGetWindowAttrib(parent, GLFW.GLFW_ICONIFIED) != 0
                || GLFW.glfwGetWindowAttrib(parent, GLFW.GLFW_VISIBLE) == 0
                || GLFW.glfwGetWindowAttrib(parent, GLFW.GLFW_FOCUSED) == 0) {
            hide();
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var x = stack.mallocInt(1); var y = stack.mallocInt(1);
            var w = stack.mallocInt(1); var h = stack.mallocInt(1);
            GLFW.glfwGetWindowPos(parent, x, y);
            GLFW.glfwGetWindowSize(parent, w, h);
            if (w.get(0) <= 0 || h.get(0) <= 0) { hide(); return false; }
            if (x.get(0) != lastX || y.get(0) != lastY) {
                lastX = x.get(0); lastY = y.get(0);
                GLFW.glfwSetWindowPos(window, lastX, lastY);
            }
            if (w.get(0) != lastW || h.get(0) != lastH) {
                lastW = w.get(0); lastH = h.get(0);
                GLFW.glfwSetWindowSize(window, lastW, lastH);
            }
        }
        return true;
    }

    void copyPrivateFrame() {
        int width = GlTextures.mainWidth(), height = GlTextures.mainHeight();
        if (width <= 0 || height <= 0) throw new IllegalStateException("Minecraft's framebuffer has no size.");
        if (image == 0) image = GL11.glGenTextures();
        if (imageWidth != width || imageHeight != height) {
            int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, image);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812F);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 0x812F);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            } finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous); }
            imageWidth = width; imageHeight = height;
        }
        if (!GlTextures.blitMainTo(image, width, height)) {
            throw new IllegalStateException("Could not copy the private framebuffer.");
        }
        if (readyFence != 0) GL32.glDeleteSync(readyFence);
        readyFence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        if (readyFence == 0) throw new IllegalStateException("Could not synchronize the private framebuffer.");
        GL11.glFlush();
    }

    void present() {
        if (window == 0 || image == 0 || readyFence == 0) return;
        withOverlayContext(() -> {
            GL32.glWaitSync(readyFence, 0, GL32.GL_TIMEOUT_IGNORED);
            GL32.glDeleteSync(readyFence); readyFence = 0;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var w = stack.mallocInt(1); var h = stack.mallocInt(1);
                GLFW.glfwGetFramebufferSize(window, w, h);
                GL11.glViewport(0, 0, w.get(0), h.get(0));
            }
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, image);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "privateImage"), 0);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            GLFW.glfwSwapBuffers(window);
        });
        // Show only after a complete private frame has been drawn.
        if (!shown) { GLFW.glfwShowWindow(window); shown = true; }
    }

    void hide() { if (window != 0 && shown) { GLFW.glfwHideWindow(window); shown = false; } }

    private void withOverlayContext(Runnable operation) {
        long previousWindow = GLFW.glfwGetCurrentContext();
        GLCapabilities previousCaps = GL.getCapabilities();
        GLFW.glfwMakeContextCurrent(window);
        if (capabilities != null) GL.setCapabilities(capabilities);
        try { operation.run(); }
        finally {
            GLFW.glfwMakeContextCurrent(previousWindow);
            GL.setCapabilities(previousCaps);
        }
    }

    private static int shaderProgram() {
        int vertex = shader(GL20.GL_VERTEX_SHADER, VERTEX);
        int fragment = 0, linked = 0;
        try {
            fragment = shader(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
            linked = GL20.glCreateProgram();
            GL20.glAttachShader(linked, vertex); GL20.glAttachShader(linked, fragment);
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException("Private view shader: " + GL20.glGetProgramInfoLog(linked));
            }
            return linked;
        } catch (RuntimeException error) {
            if (linked != 0) GL20.glDeleteProgram(linked);
            throw error;
        } finally {
            GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
    }

    private static int shader(int kind, String source) {
        int shader = GL20.glCreateShader(kind);
        GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader); GL20.glDeleteShader(shader);
            throw new IllegalStateException("Private view shader: " + log);
        }
        return shader;
    }

    @Override public void close() {
        if (window != 0) {
            hide();
            if (capabilities != null) withOverlayContext(() -> {
                if (program != 0) GL20.glDeleteProgram(program);
                if (vao != 0) GL30.glDeleteVertexArrays(vao);
            });
            GLFW.glfwDestroyWindow(window); window = 0;
        }
        // The shared image was allocated in Minecraft's context; delete it there.
        if (readyFence != 0) GL32.glDeleteSync(readyFence);
        if (image != 0) GL11.glDeleteTextures(image);
        readyFence = 0; image = 0; imageWidth = 0; imageHeight = 0;
        program = 0; vao = 0; capabilities = null; parent = 0; protectedWindow = false;
        lastX = Integer.MIN_VALUE; lastY = Integer.MIN_VALUE; lastW = 0; lastH = 0;
    }
}
