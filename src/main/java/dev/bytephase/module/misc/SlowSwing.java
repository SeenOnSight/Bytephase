package dev.bytephase.module.misc;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

/**
 * Scales the hand swing animation duration.
 *
 * <p>Only the local player's swing
 * is modified, so other entities keep vanilla animation speed.</p>
 */
public final class SlowSwing extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", 0.25, 0.1, 2.0, 0.05));

    public SlowSwing() {
        super("SlowSwing", Category.MISC, "Controls hand swing animation speed");
    }

    /** @return modified duration in ticks. */
    public int modify(int vanilla) {
        double value = Math.max(0.1, speed.getValue());
        return Math.max(1, (int) Math.round(vanilla / value));
    }
}
