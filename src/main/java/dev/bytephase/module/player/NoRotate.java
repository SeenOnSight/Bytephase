package dev.bytephase.module.player;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;

/**
 * The mixin stores and restores your rotation around the server teleport
 * packet; this class only exists so the feature can be toggled.
 */
public final class NoRotate extends Module {
    public NoRotate() {
        super("NoRotate", Category.PLAYER, "Ignores server rotation changes");
    }
}
