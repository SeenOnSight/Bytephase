package dev.bytephase.module.misc;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

/** Caps the frame rate while the game window is not focused, saving GPU and power. */
public final class UnfocusedFps extends Module {
    private final NumberSetting limit = add(new NumberSetting("Limit", 15.0, 1.0, 60.0, 1.0));

    public UnfocusedFps() {
        super("UnfocusedFPS", Category.MISC, "Limits the frame rate while the window is not focused");
    }

    /** Applies the cap to the frame-rate limit vanilla just computed. */
    public static int cap(int original) {
        var mc = BytePhase.mc;
        if (mc == null || BytePhase.modules == null || mc.isWindowFocused()) {
            return original;
        }
        UnfocusedFps module = BytePhase.modules.get(UnfocusedFps.class);
        if (module == null || !module.isEnabled()) {
            return original;
        }
        return Math.min(original, module.limit.asInt());
    }
}
