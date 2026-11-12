package dev.bytephase.module.combat;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

public final class Velocity extends Module {
    private final NumberSetting horizontal = add(new NumberSetting("Horizontal", 0.0, 0.0, 1.0, 0.05));
    private final NumberSetting vertical = add(new NumberSetting("Vertical", 0.0, 0.0, 1.0, 0.05));

    public Velocity() {
        super("Velocity", Category.COMBAT, "Reduces incoming knockback");
    }

    public double getHorizontal() {
        return horizontal.getValue();
    }

    public double getVertical() {
        return vertical.getValue();
    }
}
