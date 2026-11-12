package dev.bytephase.module.movement;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

public final class NoFall extends Module {
    private final NumberSetting threshold = add(new NumberSetting("SendOnGround", 3.0, 0.5, 20.0, 0.5));

    public NoFall() {
        super("NoFall", Category.MOVEMENT, "Mitigates fall damage");
    }

    public float getThreshold() {
        return threshold.asFloat();
    }
}
