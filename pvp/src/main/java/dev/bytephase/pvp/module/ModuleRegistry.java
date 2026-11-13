package dev.bytephase.pvp.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Registry for the combat component; Bytephase owns its visible module controls. */
public final class ModuleRegistry {
    private static final List<ClientModule> modules = new ArrayList<>();
    private static final Map<Class<?>, ClientModule> classes = new LinkedHashMap<>();
    private static volatile List<ClientModule> enabled = List.of();
    private static volatile List<HudOverlayProvider> hudOverlays = List.of();

    private ModuleRegistry() { }

    public static void add(ClientModule module) {
        if (classes.putIfAbsent(module.getClass(), module) != null) return;
        module.captureDefaults();
        module.registered = true;
        modules.add(module);
    }

    public static List<ClientModule> all() { return List.copyOf(modules); }
    public static List<ClientModule> enabled() { return enabled; }
    public static List<HudOverlayProvider> hudOverlays() { return hudOverlays; }
    public static long revision() { return ModuleRevision.current(); }
    public static <T extends ClientModule> T get(Class<T> type) { return type.cast(classes.get(type)); }

    static void switched(ClientModule ignored) {
        enabled = modules.stream().filter(ClientModule::isEnabled).toList();
        hudOverlays = enabled.stream().filter(HudOverlayProvider.class::isInstance)
            .map(HudOverlayProvider.class::cast).toList();
    }
}
