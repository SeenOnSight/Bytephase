package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.input.KeyOverride;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;

/**
 * Holds sneak for you.
 *
 * <p>1.21.11 removed the standalone
 * sneak command packet, so sneaking is applied through the sneak key, which
 * also makes the client send the matching player-input packet.</p>
 */
public final class Sneak extends Module {
    private final BooleanSetting keepWhileAirborne = add(new BooleanSetting("InAir", false));

    public Sneak() {
        super("Sneak", Category.MOVEMENT, "Sneaks for you");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) {
            KeyOverride.releaseAll(this);
            return;
        }
        if (mc.currentScreen != null) {
            KeyOverride.releaseAll(this);
            return;
        }
        boolean flying = mc.player.getAbilities().flying;
        boolean grounded = mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.isClimbing();
        if (flying || (!keepWhileAirborne.getValue() && !grounded)) {
            KeyOverride.releaseAll(this);
            return;
        }
        KeyOverride.set(this, mc.options.sneakKey, true);
    }

    @Override
    protected void onDisable() {
        KeyOverride.releaseAll(this);
    }
}
