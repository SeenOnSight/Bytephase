package dev.bytephase.pvp.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Native HUD drawing adapter for the imported damage labels. */
public final class OverlayCanvas {
    public final GuiGraphics graphics;
    public OverlayCanvas(GuiGraphics graphics) { this.graphics = graphics; }
    public void text(String text, int x, int y, int color) {
        graphics.drawString(Minecraft.getInstance().font, text, x, y, color, true);
    }
}
