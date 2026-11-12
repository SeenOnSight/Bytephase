package dev.bytephase.module.combat;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

public final class HitBoxes extends Module {
    private final NumberSetting expand = add(new NumberSetting("Expand", 0.1, 0.0, 1.0, 0.01));

    public HitBoxes() {
        super("HitBoxes", Category.COMBAT, "Expands other entities' bounding boxes");
    }

    public double getExpand() {
        return expand.getValue();
    }
}
