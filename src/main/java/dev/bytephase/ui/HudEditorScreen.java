package dev.bytephase.ui;

import dev.bytephase.BytePhase;
import dev.bytephase.render.HudLayout;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Live drag-and-resize editor. Closing it saves positions without toggling modules. */
public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private HudLayout.Element selected;
    private boolean resizing;
    private float grabX, grabY, startingScale;
    private List<HudLayout.Element> elements = List.of();

    public HudEditorScreen(Screen parent) { super(Text.literal("HUD Editor")); this.parent = parent; }
    @Override protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(width / 2 - 104, height - 28, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset all"), b -> HudLayout.resetAll()).dimensions(width / 2 + 4, height - 28, 100, 20).build());
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        elements = HudLayout.visible();
        for (HudLayout.Element element : elements) {
            int x = Math.round(element.x()), y = Math.round(element.y());
            int right = Math.round(element.right()), bottom = Math.round(element.bottom());
            int accent = Theme.accent();
            int color = element == selected ? accent : element.contains(mouseX, mouseY)
                ? 0xFFE6E6E6 : 0x80000000 | accent & 0xFFFFFF;
            context.fill(x, y, right, y + 1, color); context.fill(x, bottom - 1, right, bottom, color);
            context.fill(x, y, x + 1, bottom, color); context.fill(right - 1, y, right, bottom, color);
            if (element == selected || element.contains(mouseX, mouseY)) {
                String label = element.label() + String.format(Locale.ROOT, " · %.2f×", element.scale);
                int labelY = Math.max(36, y - 12);
                context.fill(x, labelY - 1, x + textRenderer.getWidth(label) + 6, labelY + 10, 0xD0101010);
                context.drawText(textRenderer, label, x + 3, labelY, 0xFFF0F0F0, false);
            }
        }
        context.fill(0, 0, width, 33, 0xB0101010);
        context.drawCenteredTextWithShadow(textRenderer, "HUD Editor · Drag to move", width / 2, 5, 0xFFF0F0F0);
        context.drawCenteredTextWithShadow(textRenderer, "Right-click + scroll to resize · R resets", width / 2, 18, 0xFFA6A6A6);
        if (elements.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, "Enable HUD modules in Visuals, then return here.", width / 2, height / 2, 0xFFF0F0F0);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        for (int index = elements.size() - 1; index >= 0; index--) {
            HudLayout.Element element = elements.get(index);
            if (!element.contains(click.x(), click.y())) continue;
            selected = element; resizing = click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            grabX = (float)click.x() - element.x(); grabY = (float)click.y() - element.y();
            startingScale = element.scale;
            return true;
        }
        selected = null; resizing = false;
        return true;
    }
    @Override public boolean mouseDragged(Click click, double dx, double dy) {
        if (selected == null) return super.mouseDragged(click, dx, dy);
        if (resizing) HudLayout.resize(selected, startingScale + ((float)click.y() - selected.y() - grabY) * 0.01f);
        else HudLayout.move(selected, (float)click.x() - grabX, (float)click.y() - grabY);
        return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (selected != null && resizing) { HudLayout.resize(selected, selected.scale + (float)vertical * 0.05f); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_R && selected != null) { HudLayout.reset(selected); return true; }
        return super.keyPressed(input);
    }
    @Override public void renderBackground(DrawContext context, int x, int y, float delta) { }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }
    @Override public void removed() { if (BytePhase.config != null) BytePhase.config.save(); super.removed(); }
}
