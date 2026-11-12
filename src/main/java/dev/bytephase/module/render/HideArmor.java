package dev.bytephase.module.render;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;

/** Hides player armor visuals without changing equipped items or combat state. */
public final class HideArmor extends Module {
    public HideArmor() {
        super("HideArmor", Category.RENDER, "Hides worn armor on player models");
    }
}
