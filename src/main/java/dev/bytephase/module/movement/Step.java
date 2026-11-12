package dev.bytephase.module.movement;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

public final class Step extends Module {
    private final NumberSetting stepHeight = add(new NumberSetting("StepHeight", 1.0, 0.6, 2.0, 0.1));

    public Step() {
        super("Step", Category.MOVEMENT, "Raises step height");
    }

    public float getStepHeight() {
        return stepHeight.asFloat();
    }
}
