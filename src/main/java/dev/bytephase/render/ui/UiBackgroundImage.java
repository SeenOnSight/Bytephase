package dev.bytephase.render.ui;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.UiSettings;
import dev.bytephase.ui.TextSettingScreen;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageInputStream;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;

/** One cached local image. Disk access, decoding and pixel conversion run off the client thread. */
public final class UiBackgroundImage {
    private static final Identifier TEXTURE = Identifier.of("bytephase", "ui/custom_background");
    private static final int MAX_TEXTURE_SIZE = 2048;
    private static final long MAX_FILE_BYTES = 32L * 1024 * 1024;
    private static final long MAX_INPUT_PIXELS = 64L * 1024 * 1024;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(action -> {
        Thread thread = new Thread(action, "Bytephase UI images");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicLong GENERATION = new AtomicLong();
    private static NativeImageBackedTexture texture;
    private static String requestedPath;
    private static String loadedPath = "";
    private static boolean requestedEnabled;
    private static String status = "No background image selected.";
    private static boolean choosing;

    private UiBackgroundImage() { }

    public static String status() { return status; }

    public static void reload() {
        GENERATION.incrementAndGet();
        requestedPath = null;
    }

    public static void choose(UiSettings settings, Screen parent) {
        if (choosing) return;
        choosing = true;
        status = "Choose a PNG, JPG, GIF or BMP image.";
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            WORKER.execute(() -> {
                try {
                    String script = "try\n"
                        + "set picked to choose file with prompt \"Choose a Bytephase background image\" of type {\"public.image\"}\n"
                        + "return POSIX path of picked\n"
                        + "on error number -128\nreturn \"\"\nend try";
                    Process picker = new ProcessBuilder("/usr/bin/osascript", "-e", script)
                        .redirectErrorStream(true).start();
                    String path = new String(picker.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                    if (picker.waitFor() != 0) throw new IOException("Image chooser unavailable");
                    BytePhase.mc.execute(() -> finishChoice(settings, path));
                } catch (IOException | InterruptedException error) {
                    if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                    BytePhase.mc.execute(() -> pathFallback(settings, parent));
                }
            });
        } else if (GraphicsEnvironment.isHeadless()) {
            pathFallback(settings, parent);
        } else {
            EventQueue.invokeLater(() -> {
                try {
                    FileDialog picker = new FileDialog((Frame)null, "Choose a Bytephase background image", FileDialog.LOAD);
                    try {
                        picker.setFilenameFilter((directory, name) -> name.toLowerCase(Locale.ROOT)
                            .matches(".*\\.(png|jpe?g|gif|bmp)"));
                        picker.setVisible(true);
                        String file = picker.getFile();
                        String path = file == null ? "" : Path.of(picker.getDirectory(), file).toString();
                        BytePhase.mc.execute(() -> finishChoice(settings, path));
                    } finally { picker.dispose(); }
                } catch (RuntimeException error) {
                    BytePhase.mc.execute(() -> pathFallback(settings, parent));
                }
            });
        }
    }

    private static void finishChoice(UiSettings settings, String path) {
        choosing = false;
        if (!path.isBlank()) {
            settings.setBackgroundImage(path);
            prepare(settings);
        } else status = texture == null ? "No background image selected." : "Background image ready.";
    }

    private static void pathFallback(UiSettings settings, Screen parent) {
        choosing = false;
        status = "Paste the full path to a PNG, JPG, GIF or BMP image.";
        if (BytePhase.mc.currentScreen == parent)
            BytePhase.mc.setScreen(new TextSettingScreen(parent, settings.imagePathSetting(), settings::setBackgroundImage));
    }

    public static void prepare(UiSettings settings) {
        if (settings == null) return;
        String path = settings.imagePath().trim();
        boolean enabled = settings.imageEnabled();
        boolean toggledOn = path.equals(requestedPath) && enabled && !requestedEnabled;
        if (path.equals(requestedPath) && enabled == requestedEnabled) return;
        requestedPath = path;
        requestedEnabled = enabled;
        long generation = GENERATION.incrementAndGet();
        if (path.isEmpty()) {
            releaseTexture();
            if (!choosing) status = "No background image selected.";
            return;
        }
        // Disabled images load when enabled, and remain cached while temporarily switched off.
        if (!enabled) return;
        if (toggledOn && texture != null && loadedPath.equals(path)) return;
        status = "Loading background image…";
        WORKER.execute(() -> {
            if (GENERATION.get() != generation) return;
            try {
                NativeImage image = decode(path);
                if (GENERATION.get() != generation) {
                    image.close();
                    return;
                }
                BytePhase.mc.execute(() -> {
                    if (GENERATION.get() != generation || !settings.imagePath().trim().equals(path)) {
                        image.close();
                        return;
                    }
                    NativeImageBackedTexture replacement = null;
                    try {
                        replacement = new NativeImageBackedTexture(() -> "Bytephase UI background", image);
                        BytePhase.mc.getTextureManager().registerTexture(TEXTURE, replacement);
                        texture = replacement;
                        loadedPath = path;
                        status = "Background image ready.";
                    } catch (RuntimeException | LinkageError error) {
                        if (replacement != null) replacement.close();
                        else image.close();
                        status = "Image unavailable. Try another PNG or JPG.";
                        BytePhase.LOGGER.warn("Could not upload the UI background image", error);
                    }
                });
            } catch (IOException | RuntimeException error) {
                BytePhase.mc.execute(() -> {
                    if (GENERATION.get() == generation) {
                        releaseTexture();
                        status = "Image unavailable. Choose a PNG, JPG, GIF or BMP.";
                        BytePhase.LOGGER.warn("Could not load the UI background image", error);
                    }
                });
            }
        });
    }

    private static NativeImage decode(String value) throws IOException {
        String pathText = value;
        if (pathText.startsWith("~/")) pathText = System.getProperty("user.home") + pathText.substring(1);
        if (pathText.length() > 1 && pathText.startsWith("\"") && pathText.endsWith("\""))
            pathText = pathText.substring(1, pathText.length() - 1);
        Path path = Path.of(pathText);
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_FILE_BYTES)
            throw new IOException("Image is missing or larger than 32 MB");
        try (var input = new FileImageInputStream(path.toFile())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported image format");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long)width * height > MAX_INPUT_PIXELS)
                    throw new IOException("Image dimensions are too large");
                var parameters = reader.getDefaultReadParam();
                int sample = Math.max(1, (Math.max(width, height) + MAX_TEXTURE_SIZE - 1) / MAX_TEXTURE_SIZE);
                parameters.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage decoded = reader.read(0, parameters);
                NativeImage image = new NativeImage(decoded.getWidth(), decoded.getHeight(), false);
                try {
                    int[] row = new int[decoded.getWidth()];
                    for (int y = 0; y < decoded.getHeight(); y++) {
                        decoded.getRGB(0, y, row.length, 1, row, 0, row.length);
                        for (int x = 0; x < row.length; x++) image.setColorArgb(x, y, row[x]);
                    }
                    return image;
                } catch (RuntimeException error) {
                    image.close();
                    throw error;
                } finally { decoded.flush(); }
            } finally { reader.dispose(); }
        }
    }

    public static void panel(DrawContext ctx, float x, float y, float w, float h, float radius,
                             int alpha, int border, TextureSetup backdrop, UiSettings settings) {
        prepare(settings);
        int rgb = settings == null ? 0x0B0B0B : settings.panelColor() & 0xFFFFFF;
        if (settings != null) alpha = Math.round(alpha * (settings.panelColor() >>> 24) / 255f);
        LiquidGui.glass(ctx, x, y, w, h, radius, alpha << 24 | rgb, border, backdrop);
        if (settings != null && settings.imageEnabled() && settings.imageOpacity() > 0 && texture != null) {
            var view = texture.getGlTextureView();
            if (view == null || view.texture().isClosed()) {
                reload();
                return;
            }
            LiquidGui.image(ctx, x, y, w, h, radius, view, settings.imageOpacity(), settings.imageFit());
            LiquidGui.surface(ctx, x, y, w, h, radius, alpha << 24 | rgb, border, LiquidGui.HIGHLIGHT);
        }
    }

    private static void releaseTexture() {
        if (texture != null) {
            BytePhase.mc.getTextureManager().destroyTexture(TEXTURE);
            texture = null;
            loadedPath = "";
        }
    }

    public static void dispose() {
        GENERATION.incrementAndGet();
        requestedPath = null;
        releaseTexture();
    }
}
