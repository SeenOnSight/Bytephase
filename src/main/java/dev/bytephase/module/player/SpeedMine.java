package dev.bytephase.module.player;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

/**
 * Multiplies the local player's block breaking speed.
 *
 * <p>Only the client is affected; servers with strict fast-break checks can
 * reject the extra speed.</p>
 */
public final class SpeedMine extends Module {
    private final NumberSetting modifier = add(new NumberSetting("Modifier", 1.4, 1.0, 10.0, 0.1));

    public SpeedMine() {
        super("FastMine", Category.PLAYER, "Increases block-breaking speed");
    }

    public float modify(float original) {
        return isEnabled() ? (float) (original * modifier.getValue()) : original;
    }
}
