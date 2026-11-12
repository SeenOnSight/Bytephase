package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import net.minecraft.client.gui.DrawContext;

public final class Fps extends Module {
    private int lastFps = Integer.MIN_VALUE;
    private String cachedText = "";

    public Fps() {
        super("FPS", Category.RENDER, "Shows client FPS");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.world == null) {
            lastFps = Integer.MIN_VALUE;
            cachedText = "";
            return;
        }
        int fps = mc.getCurrentFps();
        if (fps != lastFps) {
            lastFps = fps;
            cachedText = "FPS " + fps;
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var mc = BytePhase.mc;
        if (mc != null && mc.world != null && !cachedText.isEmpty()) {
            context.drawTextWithShadow(mc.textRenderer, cachedText, 4, 54, 0xFFFFFFFF);
        }
    }
}
