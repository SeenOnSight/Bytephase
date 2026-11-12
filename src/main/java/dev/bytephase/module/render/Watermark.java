package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import net.minecraft.client.gui.DrawContext;

public final class Watermark extends Module {
    public Watermark() {
        super("Watermark", Category.RENDER, "Client name watermark");
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var mc = BytePhase.mc;
        if (mc == null) {
            return;
        }
        var text = mc.textRenderer;
        int x = 4;
        String mark = "[01]";
        context.drawTextWithShadow(text, mark, x, 4, 0xFF6466E9);
        x += text.getWidth(mark) + 5;
        context.drawTextWithShadow(text, BytePhase.NAME, x, 4, 0xFFD7D7E2);
        x += text.getWidth(BytePhase.NAME) + 4;
        context.drawTextWithShadow(text, BytePhase.version(), x, 4, 0xFF7F84B8);
    }
}
