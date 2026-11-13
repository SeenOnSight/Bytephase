package dev.bytephase.pvp.marlow;

import dev.bytephase.pvp.chat.ModuleChat;
import dev.bytephase.pvp.module.ModuleRegistry;
import dev.bytephase.pvp.module.impl.combat.MarlowOptimizerModule;

/** Keeps the source optimizer's server opt-out state across module toggles. */
public final class ServerSession {
    private volatile boolean optedOut;

    public boolean requestedOptOut() { return optedOut; }
    public boolean isOptedOut() { return optedOut || !MarlowCrystal.enabled(); }

    public void optOut() {
        boolean first = !optedOut;
        optedOut = true;
        MarlowCrystal.get().clearTransient();
        var module = ModuleRegistry.get(MarlowOptimizerModule.class);
        if (first && module != null && module.isEnabled()) {
            ModuleChat.actionBar(module, ModuleChat.body().warn("Optimizer disabled by this server"));
        }
    }

    public void disconnected() {
        optedOut = false;
        MarlowCrystal.get().clearTransient();
    }
}
