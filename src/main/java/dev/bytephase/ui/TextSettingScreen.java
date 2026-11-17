package dev.bytephase.ui;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.UiSettings;
import dev.bytephase.render.ClientFonts;
import dev.bytephase.render.ui.LiquidBackdrop;
import dev.bytephase.render.ui.LiquidGui;
import dev.bytephase.render.ui.UiBackgroundImage;
import dev.bytephase.setting.StringSetting;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Native liquid-glass text editor. The vanilla widget supplies editing, never drawing. */
public final class TextSettingScreen extends Screen {
    private final Screen parent;
    private final StringSetting setting;
    private final java.util.function.Consumer<String> onSave;
    private TextFieldWidget field;
    private LiquidGui.Rect input, saveButton, cancelButton;
    private float scale, originX, originY;
    private int firstVisible;
    private String error = "";

    public TextSettingScreen(Screen parent, StringSetting setting) {
        this(parent, setting, value -> { });
    }

    public TextSettingScreen(Screen parent, StringSetting setting, java.util.function.Consumer<String> onSave) {
        super(Text.literal(setting.getName()));
        this.parent = parent;
        this.setting = setting;
        this.onSave = onSave;
    }

    @Override protected void init() {
        scale = Math.max(0.05f, Math.min(1, Math.min(width * 0.92f / 720, height * 0.9f / 240)));
        float pixels = LiquidGui.pixelScale();
        originX = Math.round((width - 720 * scale) / 2 * pixels) / pixels;
        originY = Math.round((height - 240 * scale) / 2 * pixels) / pixels;
        field = new TextFieldWidget(textRenderer, 0, 0, 664, 42, title);
        field.setMaxLength(setting.maxLength());
        field.setText(setting.getValue());
        addSelectableChild(field);
        setInitialFocus(field);
    }

    private void save() {
        String previous = setting.getValue();
        try {
            setting.setValue(field.getText());
            onSave.accept(setting.getValue());
            if (BytePhase.config != null) BytePhase.config.requestSave();
            close();
        } catch (RuntimeException invalid) {
            try { setting.setValue(previous); } catch (RuntimeException ignored) { }
            error = "Invalid value. Check the text and try again.";
        }
    }

    @Override public boolean keyPressed(KeyInput key) {
        if (key.key() == GLFW.GLFW_KEY_ENTER || key.key() == GLFW.GLFW_KEY_KP_ENTER) { save(); return true; }
        if (key.key() == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(key);
    }

    @Override public boolean mouseClicked(Click click, boolean doubled) {
        if (saveButton != null && saveButton.contains(click.x(), click.y())) { save(); return true; }
        if (cancelButton != null && cancelButton.contains(click.x(), click.y())) { close(); return true; }
        if (input != null && input.contains(click.x(), click.y())) {
            field.setFocused(true);
            ClientFonts.ui(() -> {
                int available = (int) Math.max(0, ((click.x() - input.x()) / scale - 12) / 1.3f);
                String text = field.getText().substring(Math.min(firstVisible, field.getText().length()));
                int cursor = firstVisible + textRenderer.trimToWidth(text, available).length();
                field.setCursor(Math.min(cursor, field.getText().length()), false);
            });
            return true;
        }
        return false;
    }

    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        var backdrop = LiquidBackdrop.prepare(appearance() == null || appearance().backgroundBlur());
        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(originX, originY);
        matrices.scale(scale, scale);
        try {
            ClientFonts.ui(() -> {
                LiquidGui.shadow(ctx, 0, 12, 720, 240, 22, 30, 0x60000000);
                UiBackgroundImage.panel(ctx, 0, 0, 720, 240, 22, 208, 0x40FFFFFF, backdrop, appearance());
                label(ctx, textRenderer.trimToWidth(title.getString(), 500), 28, 26, 30, 1.6f, 0xFFF0F0F0);
                LiquidGui.surface(ctx, 28, 76, 664, 42, 10, 0x18FFFFFF, accent(), LiquidGui.HIGHLIGHT);
                input = LiquidGui.rect(ctx, 28, 76, 664, 42);
                String value = field.getText();
                int cursor = Math.min(field.getCursor(), value.length());
                firstVisible = 0;
                while (firstVisible < cursor && textRenderer.getWidth(value.substring(firstVisible, cursor)) * 1.3f > 636)
                    firstVisible = value.offsetByCodePoints(firstVisible, 1);
                String shown = textRenderer.trimToWidth(value.substring(firstVisible), (int) (636 / 1.3f));
                if (!field.getSelectedText().isEmpty()) LiquidGui.surface(ctx, 38, 82,
                    Math.min(644, textRenderer.getWidth(field.getSelectedText()) * 1.3f), 30, 4, 0x40C0C0C0, 0, 0);
                label(ctx, shown, 40, 76, 42, 1.3f, 0xFFF0F0F0);
                if (System.nanoTime() / 500000000L % 2 == 0) {
                    float x = 40 + textRenderer.getWidth(value.substring(firstVisible, cursor)) * 1.3f;
                    LiquidGui.surface(ctx, x, 90, 1, 14, 0, accent(), 0, 0);
                }
                if (!error.isEmpty()) label(ctx, error, 28, 128, 24, 1.2f, 0xFFD5D5D5);
                LiquidGui.surface(ctx, 28, 168, 326, 40, 10, accent(), 0x28FFFFFF, LiquidGui.HIGHLIGHT);
                LiquidGui.surface(ctx, 366, 168, 326, 40, 10, 0x18FFFFFF, 0x28FFFFFF, LiquidGui.HIGHLIGHT);
                saveButton = LiquidGui.rect(ctx, 28, 168, 326, 40);
                cancelButton = LiquidGui.rect(ctx, 366, 168, 326, 40);
                label(ctx, "Save", 191 - textRenderer.getWidth("Save") * 0.65f, 168, 40, 1.3f,
                    appearance() == null ? 0xFFF0F0F0 : appearance().accentForeground());
                label(ctx, "Cancel", 529 - textRenderer.getWidth("Cancel") * 0.65f, 168, 40, 1.3f, 0xFFF0F0F0);
            });
        } finally { matrices.popMatrix(); }
    }

    private void label(DrawContext ctx, String value, float x, float y, float height, float size, int color) {
        var point = LiquidGui.textOrigin(ctx, x, y + height / 2 - ClientFonts.capCenterOffset(false) * size);
        var matrices = ctx.getMatrices();
        matrices.pushMatrix(); matrices.translate(point.x, point.y); matrices.scale(size, size);
        try { ctx.drawText(textRenderer, value, 0, 0, color, false); }
        finally { matrices.popMatrix(); }
    }
    private UiSettings appearance() { return BytePhase.modules.get(UiSettings.class); }
    private int accent() { return appearance() == null ? 0xFFC0C0C0 : appearance().accent(); }
    @Override public void renderBackground(DrawContext ctx, int mx, int my, float delta) { }
    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
