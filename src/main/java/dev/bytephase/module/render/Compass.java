package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

public final class Compass extends Module {
    private static final String[] DIRECTIONS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private final BooleanSetting degrees = add(new BooleanSetting("ShowDegrees", true));
    private final ColorSetting accent = add(new ColorSetting("Color", 0xFF6C6FF5));
    public Compass() { super("Compass", Category.RENDER, "A heading strip with compass directions and degrees"); }
    @Override public void onRender2D(DrawContext context, float delta) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) return;
        int center = mc.getWindow().getScaledWidth() / 2, y = 18;
        float bearing = MathHelper.floorMod(mc.player.getYaw(delta) + 180, 360);
        context.fill(center - 90, y, center + 90, y + (degrees.getValue() ? 34 : 24), 0xA0101020);
        int nearest = Math.round(bearing / 15);
        for (int tick = nearest - 6; tick <= nearest + 6; tick++) {
            float offset = MathHelper.wrapDegrees(tick * 15 - bearing) * 2;
            if (Math.abs(offset) > 86) continue;
            int x = Math.round(center + offset);
            context.fill(x, y + 15, x + 1, y + 21, 0xA0FFFFFF);
            if (Math.floorMod(tick, 3) == 0) {
                String label = DIRECTIONS[Math.floorMod(tick / 3, 8)];
                context.drawText(mc.textRenderer, label, x - mc.textRenderer.getWidth(label) / 2, y + 3, 0xFFF0F1FF, false);
            }
        }
        context.fill(center - 1, y + 14, center + 2, y + 23, accent.getValue());
        if (degrees.getValue()) {
            String label = Math.round(bearing) % 360 + "°";
            context.drawText(mc.textRenderer, label, center - mc.textRenderer.getWidth(label) / 2, y + 24, accent.getValue(), false);
        }
    }
}
