package dev.bytephase.pvp.event.impl;

import dev.bytephase.pvp.event.Event;
import dev.bytephase.pvp.event.EventType;
import dev.bytephase.pvp.module.ClientModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ModuleToggleEvent extends Event {
    public static final EventType<ModuleToggleEvent> TYPE = new EventType<>("ModuleToggleEvent");
    private static int silentDepth;
    public final ClientModule module;
    public final boolean enabled;

    private ModuleToggleEvent(ClientModule module, boolean enabled) {
        this.module = module;
        this.enabled = enabled;
    }

    public static void dispatch(ClientModule module) {
        if (silentDepth <= 0 && TYPE.hasPreListeners()) {
            TYPE.dispatch(new ModuleToggleEvent(module, module.isEnabled()));
        }
    }

    public static void silently(Runnable changes) {
        silentDepth++;

        try {
            changes.run();
        } finally {
            silentDepth--;
        }
    }
}
