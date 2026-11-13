package dev.bytephase.pvp.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class pvpKeyPolling {
    private pvpKeyPolling() {
    }

    public static boolean isKeyDown(Window window, int key) {
        return InputConstants.isKeyDown(window, key);
    }
}
