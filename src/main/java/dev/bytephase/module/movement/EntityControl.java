package dev.bytephase.module.movement;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;

/**
 * Removes the saddle and steering-item requirements from ridden animals.
 * Horses (and the other {@code AbstractHorseEntity} mounts) become controllable
 * while mounted, and pigs and striders no longer need a carrot on a stick or a
 * warped fungus on a stick to accept steering input.
 */
public final class EntityControl extends Module {
    private final BooleanSetting horses = add(new BooleanSetting("Horses", true));
    private final BooleanSetting pigs = add(new BooleanSetting("Pigs", true));

    public EntityControl() {
        super("EntityControl", Category.MOVEMENT, "Lets you steer ridden animals without their items");
    }

    public boolean horses() {
        return isEnabled() && horses.getValue();
    }

    public boolean pigs() {
        return isEnabled() && pigs.getValue();
    }
}
