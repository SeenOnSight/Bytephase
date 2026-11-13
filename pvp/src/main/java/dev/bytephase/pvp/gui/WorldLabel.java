package dev.bytephase.pvp.gui;

import dev.bytephase.pvp.CombatIntegration;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class WorldLabel {
    private WorldLabel() { }
    public static float[] project(Minecraft mc, Vec3 position, float width, float height) {
        var services = CombatIntegration.services();
        if (services == null) return null;
        double[] screen = services.project(position.x, position.y, position.z);
        if (screen == null || screen[0] < 0 || screen[1] < 0 || screen[0] >= width || screen[1] >= height) return null;
        return new float[] {(float) screen[0], (float) screen[1]};
    }
    public static void drawText(OverlayCanvas canvas, float x, float y, String damage, String self, float alpha) {
        int opacity = Math.max(4, Math.min(255, Math.round(alpha * 255)));
        int width = Minecraft.getInstance().font.width(damage);
        int selfWidth = Minecraft.getInstance().font.width(self);
        int labelWidth = Math.max(width, selfWidth);
        if (!CombatIntegration.services().reserveLabel((int) x - labelWidth / 2, (int) y, labelWidth, 22)) return;
        canvas.text(damage, (int) x - width / 2, (int) y, opacity << 24 | 0xFFFFFF);
        canvas.text(self, (int) x - selfWidth / 2, (int) y + 11, opacity << 24 | 0xFFAA88);
    }
}
