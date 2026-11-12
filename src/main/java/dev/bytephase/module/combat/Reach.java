package dev.bytephase.module.combat;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

public final class Reach extends Module {
    private final NumberSetting entityRange = add(new NumberSetting("EntityRange", 4.5, 3.0, 8.0, 0.05));
    private final NumberSetting blockRange = add(new NumberSetting("BlockRange", 5.0, 3.0, 9.0, 0.05));

    public Reach() {
        super("Reach", Category.COMBAT, "Extends interaction range");
    }

    public double getEntityRange() {
        return entityRange.getValue();
    }

    public double getBlockRange() {
        return blockRange.getValue();
    }
}
