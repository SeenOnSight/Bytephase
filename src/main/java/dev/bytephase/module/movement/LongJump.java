package dev.bytephase.module.movement;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

public final class LongJump extends Module {
    private final NumberSetting boost = add(new NumberSetting("Boost", 0.35, 0.0, 2.0, 0.05));

    public LongJump() {
        super("LongJump", Category.MOVEMENT, "Boosts horizontal velocity when jumping");
    }

    public double getBoost() {
        return boost.getValue();
    }
}
