package dev.bytephase.module.engine;

import ai.auth.ModuleRegistry;
import dev.bytephase.engine.BytephaseClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.bytephase.BytePhase;
import dev.bytephase.module.Module;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Connects the new recovered features to Bytephase's existing catalog. */
public final class BytephaseIntegration {
    private static final String INVENTORY_RESOURCE = "/assets/bytephase/engine-features.json";
    private static final Set<String> EXISTING = new HashSet<>();
    private static final Map<String, String> NAMES = new HashMap<>();
    private static final Map<ai.auth.Module, BytephaseModule> ADAPTERS = new java.util.IdentityHashMap<>();
    private static boolean initialized;
    private static boolean ready;

    static {
        try (var stream = BytephaseIntegration.class.getResourceAsStream(INVENTORY_RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing Bytephase feature inventory: " + INVENTORY_RESOURCE);
            JsonArray features = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement element : features) {
                var feature = element.getAsJsonObject();
                String name = feature.get("class").getAsString();
                if (feature.get("status").getAsString().equals("existing")) EXISTING.add(name);
                else NAMES.put(name, feature.get("bytephase").getAsString());
            }
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private BytephaseIntegration() { }

    public static boolean existing(Class<?> type) { return EXISTING.contains(type.getName()); }
    public static boolean ready() { return ready; }
    static String name(ai.auth.Module module) { return NAMES.getOrDefault(module.getClass().getName(), module.getName()); }

    /** Readable names only: safe before GLFW, AWT and the game options are initialized. */
    public static List<String> plannedNames() { return NAMES.values().stream().sorted().toList(); }

    /** Event registration stays in the ordinary Fabric initialization phase. */
    public static void registerRuntimeHooks() { new BytephaseClient().onInitializeClient(); }

    public static List<BytephaseModule> modules() {
        if (initialized) return List.copyOf(ADAPTERS.values());
        if (BytePhase.mc.getWindow() == null) throw new IllegalStateException("Imported graphics need the game window");
        initialized = true;
        BytePhase.LOGGER.info("Initializing imported feature graphics after Minecraft window creation");
        BytephaseClient.fontManager = new dev.bytephase.engine.config.ClientFonts();
        ModuleRegistry.registerAllModules();
        List<BytephaseModule> modules = ModuleRegistry.getModules().stream().map(BytephaseModule::new).toList();
        for (BytephaseModule module : modules) ADAPTERS.put(module.delegate(), module);
        dev.bytephase.engine.render.GhostBuilderUtils.update();
        ready = true;
        return modules;
    }

    public static void requestSave() {
        if (BytePhase.config != null) BytePhase.config.requestSave();
    }

    public static void run(ai.auth.Module module, Runnable action) {
        if (dev.bytephase.render.capture.StreamProof.isCleanFrame()) return;
        try {
            action.run();
        } catch (RuntimeException | LinkageError error) {
            BytePhase.LOGGER.error("Imported feature {} failed", name(module), error);
            BytephaseModule adapter = ADAPTERS.get(module);
            if (adapter != null) adapter.setEnabled(false);
            else module.setEnabled(false);
        }
    }

    public static void disableAll() {
        if (BytePhase.modules == null) return;
        for (Module module : List.copyOf(BytePhase.modules.all())) module.setEnabled(false);
    }

    public static void onToggled(Module module, boolean enabled) {
        if (!ready || BytePhase.config == null || BytePhase.config.isLoading()) return;
        dev.bytephase.engine.categories.settings.ClientSounds.playNativeToggle(enabled);
    }

    public static void openSettings(ai.auth.Module module) {
        BytephaseModule adapter = ADAPTERS.get(module);
        if (BytePhase.mc != null && adapter != null)
            BytePhase.mc.setScreen(dev.bytephase.ui.ClickGuiScreen.forModule(adapter));
    }

    public static BytephaseModule adapter(ai.auth.Module module) { return ADAPTERS.get(module); }
}
