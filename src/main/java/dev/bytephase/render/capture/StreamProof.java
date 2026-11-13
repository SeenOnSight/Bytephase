package dev.bytephase.render.capture;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.bytephase.BytePhase;
import dev.bytephase.mixin.LightmapTextureManagerAccessor;
import dev.bytephase.module.render.Fullbright;
import dev.bytephase.module.render.NoBob;
import dev.bytephase.module.render.TimeChanger;
import dev.bytephase.module.render.UiSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

/** Presents enhanced frames privately while leaving only the clean frame in Minecraft's native window. */
public final class StreamProof {
    private static final PrivateOverlayWindow OVERLAY = new PrivateOverlayWindow();
    private static final ThreadLocal<Boolean> CLEAN = ThreadLocal.withInitial(() -> false);
    private static String status = "Off";
    private static boolean requestedLastFrame;
    private static boolean originalFullscreen;
    private static long retryAfter;
    private static boolean emergencyHeld;

    private StreamProof() { }
    public static boolean isCleanFrame() { return CLEAN.get(); }
    public static String status() { return status; }

    public static void emergencyKey(MinecraftClient mc) {
        if (mc == null || mc.getWindow() == null || !mc.isWindowFocused()) { emergencyHeld = false; return; }
        long window = mc.getWindow().getHandle();
        boolean pressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F9) == GLFW.GLFW_PRESS
            && (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS)
            && (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);
        if (pressed && !emergencyHeld) {
            UiSettings ui = BytePhase.modules == null ? null : BytePhase.modules.get(UiSettings.class);
            if (ui != null) {
                ui.setStreamProof(!ui.streamProof());
                if (BytePhase.config != null) BytePhase.config.requestSave();
                retryAfter = 0;
            }
        }
        emergencyHeld = pressed;
    }

    /** Called at Minecraft's render boundary, before its main target is blitted or swapped. */
    public static void render(Runnable renderGame) {
        MinecraftClient mc = BytePhase.mc;
        UiSettings ui = BytePhase.modules == null ? null : BytePhase.modules.get(UiSettings.class);
        boolean requested = ui != null && ui.streamProof();
        if (!requested) {
            if (requestedLastFrame) {
                closeOverlay();
                restoreFullscreen(mc);
                requestedLastFrame = false;
            }
            status = "Off";
            renderGame.run();
            return;
        }
        if (!requestedLastFrame) {
            originalFullscreen = mc.getWindow().isFullscreen();
            requestedLastFrame = true;
            retryAfter = 0;
        }
        // A true exclusive fullscreen surface cannot be covered by a companion window.
        if (mc.getWindow().isFullscreen()) {
            mc.options.getFullscreen().setValue(false);
            if (mc.getWindow().isFullscreen()) mc.getWindow().toggleFullscreen();
        }
        long parent = mc.getWindow().getHandle();
        if (GLFW.glfwGetWindowMonitor(parent) != 0 || mc.getWindow().hasZeroWidthOrHeight()) {
            status = "Switching to windowed capture";
            OVERLAY.hide();
            renderClean(mc, renderGame);
            return;
        }
        boolean ready = false;
        if (System.nanoTime() >= retryAfter) {
            try {
                OVERLAY.create(parent);
                ready = OVERLAY.followParent();
            } catch (RuntimeException | LinkageError error) { failed(error); }
        }
        if (!ready) {
            if (System.nanoTime() >= retryAfter) status = "Paused while Minecraft is unfocused";
            renderClean(mc, renderGame);
            return;
        }
        // No input polling or game ticks are repeated: only the renderer is called twice.
        try {
            renderGame.run();
        } catch (RuntimeException | Error error) {
            clearMain(mc);
            OVERLAY.hide();
            throw error;
        }
        boolean copied = false;
        try { OVERLAY.copyPrivateFrame(); copied = true; }
        catch (RuntimeException | LinkageError error) { failed(error); }
        // Always overwrite the complete main target, including its depth, before window capture sees it.
        renderClean(mc, renderGame);
        if (copied) {
            try {
                OVERLAY.present();
                status = OVERLAY.isProtectedWindow() ? "Active - protected private window" : "Active - window capture only";
            } catch (RuntimeException | LinkageError error) { failed(error); }
        }
    }

    private static void renderClean(MinecraftClient mc, Runnable renderGame) {
        Screen previousScreen = mc.currentScreen;
        var fullbright = BytePhase.modules.get(Fullbright.class);
        var noBob = BytePhase.modules.get(NoBob.class);
        var time = BytePhase.modules.get(TimeChanger.class);
        double gamma = mc.options.getGamma().getValue();
        boolean bob = mc.options.getBobView().getValue();
        var world = mc.world;
        long day = world == null ? 0 : world.getLevelProperties().getTimeOfDay();
        boolean fullbrightOn = fullbright != null && fullbright.isEnabled();
        boolean noBobOn = noBob != null && noBob.isEnabled();
        boolean timeOn = time != null && time.isEnabled();
        CLEAN.set(true);
        try {
            // Keep menus, inventories and the client GUI in the private view.
            mc.currentScreen = null;
            if (fullbrightOn) mc.options.getGamma().setValue(fullbright.originalGamma());
            if (noBobOn) mc.options.getBobView().setValue(noBob.originalBob());
            if (timeOn && world != null) world.getLevelProperties().setTimeOfDay(time.originalTime(world));
            dirtyLightmap(mc);
            clearMain(mc);
            renderGame.run();
        } catch (RuntimeException | Error error) {
            // Do not expose an enhanced first pass if the clean pass fails.
            clearMain(mc);
            OVERLAY.hide();
            throw error;
        } finally {
            try {
                mc.currentScreen = previousScreen;
                mc.options.getGamma().setValue(gamma);
                mc.options.getBobView().setValue(bob);
                if (world != null) world.getLevelProperties().setTimeOfDay(day);
                dirtyLightmap(mc);
            } finally { CLEAN.remove(); }
        }
    }

    private static void dirtyLightmap(MinecraftClient mc) {
        ((LightmapTextureManagerAccessor) mc.gameRenderer.getLightmapTextureManager()).bytephase$setDirty(true);
    }

    private static void clearMain(MinecraftClient mc) {
        var target = mc.getFramebuffer();
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
            target.getColorAttachment(), 0, target.getDepthAttachment(), 1.0);
    }

    private static void failed(Throwable error) {
        status = "Private view unavailable - Ctrl+Shift+F9 exits";
        retryAfter = System.nanoTime() + 5_000_000_000L;
        BytePhase.LOGGER.error("StreamProof private window failed; Minecraft keeps the clean capture frame", error);
        closeOverlay();
    }
    private static void closeOverlay() {
        try { OVERLAY.close(); }
        catch (RuntimeException | LinkageError error) { BytePhase.LOGGER.warn("Could not close the private view", error); }
    }
    private static void restoreFullscreen(MinecraftClient mc) {
        if (originalFullscreen && mc != null && mc.getWindow() != null) {
            mc.options.getFullscreen().setValue(true);
            if (!mc.getWindow().isFullscreen()) mc.getWindow().toggleFullscreen();
        }
        originalFullscreen = false;
    }
    public static void close() {
        closeOverlay();
        restoreFullscreen(BytePhase.mc);
        requestedLastFrame = false;
        CLEAN.remove();
    }
}
