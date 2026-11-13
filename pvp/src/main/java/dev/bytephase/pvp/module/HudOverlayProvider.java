package dev.bytephase.pvp.module;

import dev.bytephase.pvp.gui.OverlayCanvas;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface HudOverlayProvider {
    boolean isHudOverlayEnabled();

    void renderHudOverlay(OverlayCanvas var1, float var2, float var3);
}
