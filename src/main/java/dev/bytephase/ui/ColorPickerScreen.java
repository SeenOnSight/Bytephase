package dev.bytephase.ui;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.UiSettings;
import dev.bytephase.render.ClientFonts;
import dev.bytephase.render.ui.LiquidBackdrop;
import dev.bytephase.render.ui.LiquidGui;
import dev.bytephase.render.ui.UiBackgroundImage;
import dev.bytephase.setting.ColorSetting;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Full HSV/RGBA picker with live preview, exact hex entry and reversible edits. */
public final class ColorPickerScreen extends Screen {
    private static final int WIDTH = 760, HEIGHT = 500;
    private final Screen parent;
    private final ColorSetting setting;
    private final int original;
    private int current;
    private float hue, saturation, brightness;
    private float scale, originX, originY;
    private TextFieldWidget hex;
    private boolean updatingHex, committed;
    private String error = "";
    private LiquidGui.Rect square, hueBar, hexInput, saveButton, cancelButton, originalButton;
    private final List<Channel> channels = new ArrayList<>();
    private Drag drag;
    private int draggedShift;
    private record Channel(LiquidGui.Rect rect, int shift) { }
    private enum Drag { SQUARE, HUE, CHANNEL }

    public ColorPickerScreen(Screen parent, ColorSetting setting) {
        super(Text.literal(setting.getName() + " color"));
        this.parent = parent;
        this.setting = setting;
        original = current = setting.getValue();
        readHsv();
    }

    @Override protected void init() {
        scale = Math.max(0.05f, Math.min(1, Math.min(width * 0.94f / WIDTH, height * 0.92f / HEIGHT)));
        float pixels = LiquidGui.pixelScale();
        originX = Math.round((width - WIDTH * scale) * 0.5f * pixels) / pixels;
        originY = Math.round((height - HEIGHT * scale) * 0.5f * pixels) / pixels;
        hex = new TextFieldWidget(textRenderer, 0, 0, 288, 40, Text.literal("Hex color"));
        hex.setMaxLength(9);
        hex.setText(hexValue());
        hex.setChangedListener(this::readHex);
        addSelectableChild(hex);
        setInitialFocus(hex);
    }

    private void readHsv() {
        float[] hsv = Color.RGBtoHSB(current >> 16 & 255, current >> 8 & 255, current & 255, null);
        if (hsv[1] > 0) hue = hsv[0];
        saturation = hsv[1];
        brightness = hsv[2];
    }

    private String hexValue() {
        return current >>> 24 == 255
            ? String.format(Locale.ROOT, "#%06X", current & 0xFFFFFF)
            : String.format(Locale.ROOT, "#%08X", current);
    }

    private void readHex(String text) {
        if (updatingHex) return;
        String value = text.startsWith("#") ? text.substring(1) : text;
        if (!value.matches("(?:[A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})")) {
            error = "Use #RRGGBB or #AARRGGBB.";
            return;
        }
        current = (int)Long.parseLong(value, 16);
        if (value.length() == 6) current |= 0xFF000000;
        readHsv();
        setting.setValue(current);
        error = "";
    }

    private void updateColor(boolean readHsv) {
        if (readHsv) readHsv();
        setting.setValue(current);
        updatingHex = true;
        try { hex.setText(hexValue()); }
        finally { updatingHex = false; }
        error = "";
    }

    private static float fraction(double coordinate, float origin, float size) {
        return (float)Math.clamp((coordinate - origin) / Math.max(1, size), 0, 1);
    }

    private void updateDrag(double x, double y) {
        if (drag == Drag.SQUARE) {
            saturation = fraction(x, square.x(), square.w());
            brightness = 1 - fraction(y, square.y(), square.h());
            current = current & 0xFF000000 | Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
            updateColor(false);
        } else if (drag == Drag.HUE) {
            hue = fraction(y, hueBar.y(), hueBar.h());
            current = current & 0xFF000000 | Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
            updateColor(false);
        } else if (drag == Drag.CHANNEL) {
            for (Channel channel : channels) if (channel.shift() == draggedShift) {
                int value = Math.round(fraction(x, channel.rect().x(), channel.rect().w()) * 255);
                current = current & ~(255 << draggedShift) | value << draggedShift;
                updateColor(draggedShift != 24);
                break;
            }
        }
    }

    private void save() {
        readHex(hex.getText());
        if (!error.isEmpty()) return;
        committed = true;
        persist();
        close();
    }

    @Override public boolean mouseClicked(Click click, boolean doubled) {
        if (click.buttonInfo().button() != 0) return true;
        double x = click.x(), y = click.y();
        if (saveButton != null && saveButton.contains(x, y)) { save(); return true; }
        if (cancelButton != null && cancelButton.contains(x, y)) { close(); return true; }
        if (originalButton != null && originalButton.contains(x, y)) {
            current = original; updateColor(true); return true;
        }
        if (hexInput != null && hexInput.contains(x, y)) {
            setFocused(hex);
            hex.setFocused(true);
            ClientFonts.ui(() -> {
                int available = (int)Math.max(0, ((x - hexInput.x()) / scale - 12) / 1.3f);
                int cursor = textRenderer.trimToWidth(hex.getText(), available).length();
                hex.setCursor(cursor, false);
            });
            return true;
        }
        setFocused(null);
        hex.setFocused(false);
        if (square != null && square.contains(x, y)) drag = Drag.SQUARE;
        else if (hueBar != null && hueBar.contains(x, y)) drag = Drag.HUE;
        else for (Channel channel : channels) if (channel.rect().contains(x, y)) {
            drag = Drag.CHANNEL; draggedShift = channel.shift(); break;
        }
        if (drag != null) updateDrag(x, y);
        return true;
    }

    @Override public boolean mouseDragged(Click click, double dx, double dy) {
        if (drag != null) updateDrag(click.x(), click.y());
        return true;
    }

    @Override public boolean mouseReleased(Click click) { drag = null; return true; }
    @Override public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) { save(); return true; }
        return super.keyPressed(input);
    }

    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        UiSettings appearance = BytePhase.modules.get(UiSettings.class);
        var backdrop = LiquidBackdrop.prepare(appearance == null || appearance.backgroundBlur());
        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(originX, originY);
        matrices.scale(scale, scale);
        try {
            ClientFonts.ui(() -> {
                LiquidGui.shadow(ctx, 0, 12, WIDTH, HEIGHT, 24, 36, 0x60000000);
                UiBackgroundImage.panel(ctx, 0, 0, WIDTH, HEIGHT, 24, 220, 0x40FFFFFF, backdrop, appearance);
                label(ctx, textRenderer.trimToWidth(title.getString(), 560), 30, 24, 34, 1.6f, 0xFFF0F0F0);
                label(ctx, "Pick any color", 30, 62, 24, 1.2f, 0xFFA6A6A6);
                drawSquare(ctx);
                drawHue(ctx);
                checker(ctx, 432, 76, 136, 40);
                LiquidGui.surface(ctx, 432, 76, 136, 40, 8, original, 0x50FFFFFF, 0);
                originalButton = LiquidGui.rect(ctx, 432, 76, 136, 40);
                checker(ctx, 580, 76, 150, 40);
                LiquidGui.surface(ctx, 580, 76, 150, 40, 8, current, 0x50FFFFFF, 0);
                label(ctx, "Original", 432, 118, 24, 1.1f, 0xFFA6A6A6);
                label(ctx, "Preview", 580, 118, 24, 1.1f, 0xFFA6A6A6);
                LiquidGui.surface(ctx, 432, 154, 298, 40, 10, 0x20FFFFFF, accent(), 0);
                hexInput = LiquidGui.rect(ctx, 432, 154, 298, 40);
                if (!hex.getSelectedText().isEmpty())
                    LiquidGui.surface(ctx, 440, 160, 280, 28, 4, 0x40C0C0C0, 0, 0);
                label(ctx, hex.getText(), 444, 154, 40, 1.3f, 0xFFF0F0F0);
                if (hex.isFocused() && System.nanoTime() / 500000000L % 2 == 0) {
                    float x = 444 + textRenderer.getWidth(hex.getText().substring(0,
                        Math.min(hex.getCursor(), hex.getText().length()))) * 1.3f;
                    LiquidGui.surface(ctx, x, 167, 1, 14, 0, accent(), 0, 0);
                }
                channels.clear();
                for (int i = 0; i < 4; i++) drawChannel(ctx, i);
                label(ctx, error.isEmpty() ? "Hex: #RRGGBB or #AARRGGBB" : error,
                    432, 369, 24, 1.1f, error.isEmpty() ? 0xFFA6A6A6 : 0xFFD5D5D5);
                label(ctx, "Drag the square and hue bar, adjust RGBA, or paste a hex color.",
                    30, 392, 24, 1.15f, 0xFFA6A6A6);
                LiquidGui.surface(ctx, 30, 438, 344, 40, 10, accent(), 0x40FFFFFF, LiquidGui.HIGHLIGHT);
                LiquidGui.surface(ctx, 386, 438, 344, 40, 10, 0x20FFFFFF, 0x40FFFFFF, LiquidGui.HIGHLIGHT);
                saveButton = LiquidGui.rect(ctx, 30, 438, 344, 40);
                cancelButton = LiquidGui.rect(ctx, 386, 438, 344, 40);
                label(ctx, "Save", 202 - textRenderer.getWidth("Save") * 0.65f, 438, 40, 1.3f,
                    appearance == null ? 0xFFF0F0F0 : appearance.accentForeground());
                label(ctx, "Cancel", 558 - textRenderer.getWidth("Cancel") * 0.65f, 438, 40, 1.3f, 0xFFF0F0F0);
            });
        } finally { matrices.popMatrix(); }
    }

    private void drawSquare(DrawContext ctx) {
        horizontalGradient(ctx, 30, 98, 330, 270, 0xFFFFFFFF, Color.HSBtoRGB(hue, 1, 1));
        ctx.fillGradient(30, 98, 360, 368, 0x00000000, 0xFF000000);
        square = LiquidGui.rect(ctx, 30, 98, 330, 270);
        float x = 30 + saturation * 330, y = 98 + (1 - brightness) * 270;
        LiquidGui.surface(ctx, x - 7, y - 7, 14, 14, 7, 0xFF000000 | current & 0xFFFFFF, 0xFFFFFFFF, 0);
    }

    private void drawHue(DrawContext ctx) {
        for (int i = 0; i < 6; i++) ctx.fillGradient(378, 98 + i * 45, 404, 98 + (i + 1) * 45,
            Color.HSBtoRGB(i / 6f, 1, 1), Color.HSBtoRGB((i + 1) / 6f, 1, 1));
        hueBar = LiquidGui.rect(ctx, 378, 98, 26, 270);
        LiquidGui.surface(ctx, 375, 98 + hue * 270 - 3, 32, 6, 3, 0xFFFFFFFF, 0xFF080808, 0);
    }

    private void drawChannel(DrawContext ctx, int index) {
        int shift = new int[]{16, 8, 0, 24}[index];
        String name = new String[]{"R", "G", "B", "A"}[index];
        int value = current >>> shift & 255;
        int y = 216 + index * 36;
        label(ctx, name, 432, y, 28, 1.2f, 0xFFF0F0F0);
        int from = current & ~(255 << shift), to = from | 255 << shift;
        if (shift == 24) checker(ctx, 456, y + 8, 226, 12);
        else { from |= 0xFF000000; to |= 0xFF000000; }
        horizontalGradient(ctx, 456, y + 8, 226, 12, from, to);
        LiquidGui.surface(ctx, 456 + value / 255f * 226 - 5, y + 9 - 3, 10, 16, 5, 0xFFF0F0F0, 0xFF080808, 0);
        label(ctx, Integer.toString(value), 693, y, 28, 1.2f, 0xFFA6A6A6);
        channels.add(new Channel(LiquidGui.rect(ctx, 456, y, 226, 28), shift));
    }

    private static void horizontalGradient(DrawContext ctx, int x, int y, int w, int h, int from, int to) {
        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x + w, y);
        matrices.rotate((float)(Math.PI * 0.5));
        try { ctx.fillGradient(0, 0, h, w, to, from); }
        finally { matrices.popMatrix(); }
    }

    private static void checker(DrawContext ctx, int x, int y, int w, int h) {
        for (int iy = 0; iy < h; iy += 8) for (int ix = 0; ix < w; ix += 8)
            ctx.fill(x + ix, y + iy, x + Math.min(w, ix + 8), y + Math.min(h, iy + 8),
                (ix / 8 + iy / 8) % 2 == 0 ? 0xFF8F8F8F : 0xFF555555);
    }

    private void label(DrawContext ctx, String value, float x, float y, float height, float size, int color) {
        var point = LiquidGui.textOrigin(ctx, x, y + height * 0.5f - ClientFonts.capCenterOffset(false) * size);
        var matrices = ctx.getMatrices();
        matrices.pushMatrix(); matrices.translate(point.x, point.y); matrices.scale(size, size);
        try { ctx.drawText(textRenderer, value, 0, 0, color, false); }
        finally { matrices.popMatrix(); }
    }

    private int accent() {
        UiSettings settings = BytePhase.modules.get(UiSettings.class);
        return settings == null ? 0xFFC0C0C0 : settings.accent();
    }
    private static void persist() { if (BytePhase.config != null) BytePhase.config.requestSave(); }
    @Override public void removed() {
        if (!committed) { setting.setValue(original); persist(); }
        super.removed();
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public void renderBackground(DrawContext ctx, int mx, int my, float delta) { }
    @Override public boolean shouldPause() { return false; }
}
