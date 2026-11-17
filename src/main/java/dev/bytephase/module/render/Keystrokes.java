package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;

public final class Keystrokes extends Module {
    private final BooleanSetting mouse = add(new BooleanSetting("MouseButtons", true));
    private final BooleanSetting jump = add(new BooleanSetting("JumpKey", true));
    private final ColorSetting pressed = add(new ColorSetting("PressedColor", 0xCC6C6FF5));
    public Keystrokes() { super("Keystrokes", Category.RENDER, "Shows movement, jump and mouse inputs"); }
    @Override public void onRender2D(DrawContext context, float delta) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) return;
        int x = 8, y = 72;
        key(context, mc.options.forwardKey, x + 25, y, 22, 22, null);
        key(context, mc.options.leftKey, x, y + 25, 22, 22, null);
        key(context, mc.options.backKey, x + 25, y + 25, 22, 22, null);
        key(context, mc.options.rightKey, x + 50, y + 25, 22, 22, null);
        int next = y + 50;
        if (jump.getValue()) { key(context, mc.options.jumpKey, x, next, 72, 16, "Jump"); next += 19; }
        if (mouse.getValue()) {
            key(context, mc.options.attackKey, x, next, 35, 18, "LMB");
            key(context, mc.options.useKey, x + 38, next, 34, 18, "RMB");
        }
    }
    private void key(DrawContext context, KeyBinding key, int x, int y, int width, int height, String label) {
        var renderer = BytePhase.mc.textRenderer;
        if (label == null) label = renderer.trimToWidth(key.getBoundKeyLocalizedText().getString(), width - 4);
        context.fill(x, y, x + width, y + height, key.isPressed() ? pressed.getValue() : 0xAD101020);
        context.drawText(renderer, label, x + (width - renderer.getWidth(label)) / 2, y + (height - 8) / 2, 0xFFF0F1FF, false);
    }
}
