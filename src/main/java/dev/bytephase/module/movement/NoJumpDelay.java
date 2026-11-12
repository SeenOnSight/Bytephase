package dev.bytephase.module.movement;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;

public final class NoJumpDelay extends Module {
    public NoJumpDelay() {
        super("NoJumpDelay", Category.MOVEMENT, "Removes the cooldown after jumping");
    }
}
