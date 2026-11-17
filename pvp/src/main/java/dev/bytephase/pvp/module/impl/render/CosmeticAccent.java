package dev.bytephase.pvp.module.impl.render;

import dev.bytephase.pvp.CombatIntegration;

/** Shared Bytephase theme for the imported entity glow. */
final class CosmeticAccent {
    private CosmeticAccent() { }

    static int argb() {
        var services = CombatIntegration.services();
        return (services == null ? 0xFFC0C0C0 : services.accentColor()) | 0xFF000000;
    }
}
