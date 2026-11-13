package dev.bytephase.pvp.module.impl.movement;

import dev.bytephase.pvp.CombatIntegration;

public final class SprintModule {
    private SprintModule() { }
    public static boolean holding() {
        return CombatIntegration.services() != null && CombatIntegration.services().sprintHeld();
    }
}
