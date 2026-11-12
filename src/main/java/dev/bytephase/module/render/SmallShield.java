package dev.bytephase.module.render;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

/**
 * Shrinks the offhand item in first person.
 *
 * <p>The shield model covers a large part of the screen at vanilla scale, which
 * hides whoever you are fighting. The offhand is scaled and nudged inside the
 * hand transform, so nothing about the item, its durability or its blocking
 * behaviour changes; only the drawn model.</p>
 */
public final class SmallShield extends Module {
    private final NumberSetting scale = add(new NumberSetting("Scale", 0.5, 0.1, 1.5, 0.05));
    private final NumberSetting offsetX = add(new NumberSetting("OffsetX", 0.0, -1.0, 1.0, 0.05));
    private final NumberSetting offsetY = add(new NumberSetting("OffsetY", 0.0, -1.0, 1.0, 0.05));

    public SmallShield() {
        super("SmallShield", Category.RENDER, "Shrinks the offhand item in first person");
    }

    public float scale() {
        return scale.asFloat();
    }

    public float offsetX() {
        return offsetX.asFloat();
    }

    public float offsetY() {
        return offsetY.asFloat();
    }
}
