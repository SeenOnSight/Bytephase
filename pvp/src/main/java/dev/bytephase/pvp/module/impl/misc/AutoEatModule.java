package dev.bytephase.pvp.module.impl.misc;

import dev.bytephase.pvp.CombatIntegration;

/** Uses the utility client's food ownership instead of starting a second eater. */
public final class AutoEatModule {
    private AutoEatModule() { }
    public static boolean isEating() {
        return CombatIntegration.services() != null && CombatIntegration.services().isEating();
    }
}
