package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.Render2D;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public final class Hitmarker extends Module {
    private final NumberSetting duration = add(new NumberSetting("DurationMs", 400.0, 100.0, 1500.0, 50.0));
    private long lastHit;

    public Hitmarker() {
        super("Hitmarker", Category.RENDER, "Marks your screen when you hit entities");
    }

    @Override
    public void onAttack(PlayerEntity self, Entity target) {
        if (isEnabled()) {
            lastHit = System.currentTimeMillis();
        }
    }

    @Override
    protected void onDisable() {
        lastHit = 0L;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (lastHit == 0L) {
            return;
        }
        long age = System.currentTimeMillis() - lastHit;
        long limit = (long) duration.asFloat();
        if (age > limit) {
            return;
        }
        var mc = BytePhase.mc;
        if (mc == null) {
            return;
        }
        double cx = mc.getWindow().getScaledWidth() / 2.0;
        double cy = mc.getWindow().getScaledHeight() / 2.0;
        float frac = 1.0f - (float) age / limit;
        int alpha = (int) (255 * frac);
        int color = (alpha << 24) | 0x00FF3030;
        Render2D.drawLine(context, cx - 9, cy - 10, cx - 4, cy - 5, color);
        Render2D.drawLine(context, cx + 4, cy - 5, cx + 9, cy - 10, color);
        Render2D.drawLine(context, cx - 9, cy + 10, cx - 4, cy + 5, color);
        Render2D.drawLine(context, cx + 4, cy + 5, cx + 9, cy + 10, color);
    }
}
