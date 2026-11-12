package dev.bytephase.module.movement;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

public final class HighJump extends Module {
    private final NumberSetting boost = add(new NumberSetting("Boost", 1.5, 1.1, 3.0, 0.05));

    public HighJump() {
        super("HighJump", Category.MOVEMENT, "Makes you jump higher");
    }

    public float getBoost() {
        return boost.asFloat();
    }
}
