package dev.bytephase.module.movement;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

/**
 * Overrides block slipperiness. Vanilla values are around 0.6, so values above
 * that make every block slide like ice.
 */
public final class Slippy extends Module {
    private final NumberSetting friction = add(new NumberSetting("Friction", 1.0, 0.01, 1.1, 0.01));

    public Slippy() {
        super("Slippy", Category.MOVEMENT, "Changes the friction of every block");
    }

    public float friction() {
        return friction.asFloat();
    }
}
