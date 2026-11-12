package dev.bytephase.module.combat;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;

/**
 * Removes the delay vanilla applies after an attack that misses.
 *
 * <p>{@code MinecraftClient.doAttack()} charges a ten tick cooldown when the
 * attack hits nothing, which throttles fast clicking in a fight. The module has
 * no settings; {@code MinecraftClientMixin} clears that counter while it is
 * enabled.</p>
 */
public final class NoHitDelay extends Module {
    public NoHitDelay() {
        super("NoHitDelay", Category.COMBAT, "Removes the delay after a missed attack");
    }
}
