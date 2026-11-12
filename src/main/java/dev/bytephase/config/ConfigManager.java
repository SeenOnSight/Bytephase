package dev.bytephase.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bytephase.BytePhase;
import dev.bytephase.module.Module;
import dev.bytephase.ui.ClickGuiScreen;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.Setting;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map.Entry;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public final class ConfigManager {
    private static final int CONFIG_VERSION = 9;
    private static final Map<String, Integer> PREVIOUS_ESP_DEFAULTS = Map.ofEntries(
            Map.entry("ESP.PlayerColor", 0xFFFF5555),
            Map.entry("ESP.MonsterColor", 0xFF55FF55),
            Map.entry("ESP.OtherColor", 0xFFFFC857),
            Map.entry("Tracers.PlayerColor", 0xFFFFFFFF),
            Map.entry("Tracers.MonsterColor", 0xFF00FF00),
            Map.entry("Tracers.OtherColor", 0xFFFFFF00),
            Map.entry("Nametags.PlayerColor", 0xFFFFFFFF),
            Map.entry("Nametags.MonsterColor", 0xFF55FF55),
            Map.entry("Nametags.OtherColor", 0xFFFFC857),
            Map.entry("Nametags.HealthColor", 0xFF55FF55),
            Map.entry("Nametags.LowHealthColor", 0xFFFF5555),
            Map.entry("StorageESP.ChestColor", 0xFFFF7F2A),
            Map.entry("StorageESP.ShulkerColor", 0xFFB055FF),
            Map.entry("StorageESP.BarrelColor", 0xFF9C6B3C),
            Map.entry("StorageESP.HopperColor", 0xFF9A9A9A),
            Map.entry("StorageESP.FurnaceColor", 0xFFB8B8B8),
            Map.entry("StorageESP.EnderChestColor", 0xFF2ACB7F),
            Map.entry("HoleESP.SafeColor", 0xFF4040FF),
            Map.entry("HoleESP.UnsafeColor", 0xFFFF4040),
            Map.entry("Highlight.Color", 0x44FFFFFF),
            Map.entry("ItemESP.Color", 0xFFFFD166),
            Map.entry("BlockESP.SpawnerColor", 0xFFFF5252),
            Map.entry("BlockESP.PortalColor", 0xFFB388FF),
            Map.entry("BlockESP.OreColor", 0xFF64B5F6),
            Map.entry("BlockESP.StorageColor", 0xFFFFB300),
            Map.entry("SusChunks.Color", 0xFFFF66CC),
            Map.entry("LogoutSpots.Color", 0xFFFF5050));

    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("bytephase.json");
    private final Path legacyPath = FabricLoader.getInstance().getConfigDir().resolve("aftermath.json");
    private boolean loading;
    // Preserve newer or unreadable configs when modules are toggled or on exit.
    private boolean preserveUnreadableConfig;

    public boolean isLoading() {
        return loading;
    }

    /** Ticks to wait after the last change before writing, so rapid toggles share one write. */
    private static final int SAVE_DEBOUNCE_TICKS = 20;
    private boolean dirty;
    private int dirtyTicks;

    /**
     * Schedules a save instead of writing synchronously. Toggling a module or
     * dragging a slider used to do a blocking file write on the client thread
     * every time, which showed up as a frame hitch during fights.
     */
    public void requestSave() {
        if (loading || preserveUnreadableConfig) {
            return;
        }
        dirty = true;
        dirtyTicks = 0;
    }

    /** Called once per client tick; performs the pending save after the debounce delay. */
    public void tick() {
        if (dirty && ++dirtyTicks >= SAVE_DEBOUNCE_TICKS) {
            save();
        }
    }

    public void save() {
        dirty = false;
        dirtyTicks = 0;
        if (loading || preserveUnreadableConfig || BytePhase.modules == null) {
            return;
        }
        try {
            JsonObject root = new JsonObject();
            root.addProperty("configVersion", CONFIG_VERSION);
            root.add("clickGui", ClickGuiScreen.saveLayout());
            JsonObject modulesJson = new JsonObject();
            for (Module module : BytePhase.modules.all()) {
                JsonObject moduleJson = new JsonObject();
                moduleJson.addProperty("enabled", module.isEnabled());
                if (!module.getSettings().isEmpty()) {
                    JsonObject settingsJson = new JsonObject();
                    for (Setting<?> setting : module.getSettings()) {
                        settingsJson.add(setting.getName(), setting.toJson());
                    }
                    moduleJson.add("settings", settingsJson);
                }
                modulesJson.add(module.getName(), moduleJson);
            }
            root.add("modules", modulesJson);

            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, root.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            BytePhase.LOGGER.error("Failed to save config to {}", path, e);
        }
    }

    public void load() {
        Path source = Files.exists(path) ? path : legacyPath;
        if (!Files.exists(source)) {
            return;
        }
        loading = true;
        boolean loadedSuccessfully = false;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                preserveUnreadableConfig = true;
                BytePhase.LOGGER.warn("Config {} is not an object; leaving it untouched", source);
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            int version = root.has("configVersion") && root.get("configVersion").isJsonPrimitive()
                    ? root.get("configVersion").getAsInt() : 0;
            if (version > CONFIG_VERSION) {
                preserveUnreadableConfig = true;
                BytePhase.LOGGER.warn(
                        "Config {} uses version {}, newer than this client supports ({}); leaving it untouched",
                        source, version, CONFIG_VERSION);
                return;
            }
            ClickGuiScreen.loadLayout(root.get("clickGui"));
            boolean migrateLegacyDefaults = version < 2;
            // The AntiVanish default change was introduced in config v3.
            // Keep its migration boundary fixed when later versions are added;
            // using CONFIG_VERSION here would repeatedly reinterpret newer
            // user values that happen to equal the old default.
            boolean migrateV2Defaults = version < 3;
            boolean migratePlayerEspStyle = version < 4;
            boolean migrateAutoCrystalDefaults = version < 5;
            boolean migrateNbtSafetyDefault = version < 6;
            boolean migrateAutoCrystalRange = version < 7;
            boolean migrateAutoCrystalShortRange = version < 9;
            JsonObject modulesJson = root.getAsJsonObject("modules");
            if (modulesJson == null) {
                preserveUnreadableConfig = true;
                BytePhase.LOGGER.warn("Config {} has no modules object; leaving it untouched", source);
                return;
            }
            for (Entry<String, JsonElement> entry : modulesJson.entrySet()) {
                try {
                    Module module = BytePhase.modules.getByName(entry.getKey());
                    if (module == null || !entry.getValue().isJsonObject()) {
                        continue;
                    }
                    JsonObject moduleJson = entry.getValue().getAsJsonObject();
                    if (moduleJson.has("settings") && moduleJson.get("settings").isJsonObject()) {
                        JsonObject settingsJson = moduleJson.getAsJsonObject("settings");
                        for (Setting<?> setting : module.getSettings()) {
                            JsonElement settingJson = settingsJson.get(setting.getName());
                            if (settingJson != null) {
                                try {
                                    // 6 was the old default, but the old unarmoured
                                    // damage estimate made virtually every crystal
                                    // placement fail that safety check.
                                    if (migrateLegacyDefaults && module.getName().equals("AutoCrystal")
                                            && setting.getName().equals("MaxSelfDamage")
                                            && settingJson.isJsonPrimitive()
                                            && settingJson.getAsDouble() == 6.0) {
                                        setting.fromJson(new com.google.gson.JsonPrimitive(30.0));
                                        continue;
                                    }
                                    if (migrateLegacyDefaults && isPreviousEspDefault(module, setting, settingJson)) {
                                        continue;
                                    }
                                    if (migrateV2Defaults && isPreviousAntiVanishDefault(module, setting, settingJson)) {
                                        continue;
                                    }
                                    // Model Wireframe is now the ESP default;
                                    // upgrade the old untouched box default so
                                    // existing installs receive the new style.
                                    if (migratePlayerEspStyle && module.getName().equals("ESP")
                                            && setting.getName().equals("Style")
                                            && settingJson.isJsonPrimitive()
                                            && settingJson.getAsString().equals("Highlight")) {
                                        setting.fromJson(new com.google.gson.JsonPrimitive("Model Wireframe"));
                                        continue;
                                    }
                                    // AutoCrystal previously acquired targets
                                    // only within 4.5 blocks and ignored hostile
                                    // mobs by default. Upgrade untouched old
                                    // defaults while preserving other values.
                                    if (migrateAutoCrystalDefaults
                                            && module.getName().equals("AutoCrystal")
                                            && setting.getName().equals("Range")
                                            && settingJson.isJsonPrimitive()
                                            && settingJson.getAsDouble() == 4.5) {
                                        setting.fromJson(new com.google.gson.JsonPrimitive(18.0));
                                        continue;
                                    }
                                    // The prior default of 12 did not cover all
                                    // targets reachable by a crystal placed
                                    // near the edge of the player's interaction
                                    // range. Upgrade that untouched default too.
                                    if (migrateAutoCrystalRange
                                            && module.getName().equals("AutoCrystal")
                                            && setting.getName().equals("Range")
                                            && settingJson.isJsonPrimitive()
                                            && settingJson.getAsDouble() == 12.0) {
                                        setting.fromJson(new com.google.gson.JsonPrimitive(18.0));
                                        continue;
                                    }
                                    // Older builds saved 6 as the target-acquisition
                                    // range. The current module default is 18, and the
                                    // short saved value made AutoCrystal appear to work
                                    // only when standing close to its target. Migrate
                                    // only that exact legacy value once.
                                    if (migrateAutoCrystalShortRange
                                            && module.getName().equals("AutoCrystal")
                                            && setting.getName().equals("Range")
                                            && settingJson.isJsonPrimitive()
                                            && settingJson.getAsDouble() == 6.0) {
                                        setting.fromJson(new com.google.gson.JsonPrimitive(18.0));
                                        continue;
                                    }
                                    if (migrateAutoCrystalDefaults
                                            && module.getName().equals("AutoCrystal")
                                            && setting.getName().equals("BreakRange")
                                            && settingJson.isJsonPrimitive()
                                            && settingJson.getAsDouble() == 4.5) {
                                        setting.fromJson(new com.google.gson.JsonPrimitive(6.0));
                                        continue;
                                    }
                                    if (migrateAutoCrystalDefaults
                                            && module.getName().equals("AutoCrystal")
                                            && setting.getName().equals("TargetHostiles")
                                            && settingJson.isJsonPrimitive()
                                            && !settingJson.getAsBoolean()) {
                                        setting.fromJson(new com.google.gson.JsonPrimitive(true));
                                        continue;
                                    }
                                    // Unlimited NBT bypasses vanilla's memory
                                    // bound and was previously enabled by
                                    // default. Turn only that untouched default
                                    // off when upgrading old configs.
                                    if (migrateNbtSafetyDefault
                                            && module.getName().equals("AntiPacketKick")
                                            && setting.getName().equals("UnlimitedNbt")
                                            && settingJson.isJsonPrimitive()
                                            && settingJson.getAsBoolean()) {
                                        setting.fromJson(new com.google.gson.JsonPrimitive(false));
                                        continue;
                                    }
                                    setting.fromJson(settingJson);
                                } catch (RuntimeException settingError) {
                                    BytePhase.LOGGER.warn("Ignoring invalid setting {}.{} in {}",
                                            module.getName(), setting.getName(), source, settingError);
                                }
                            }
                        }
                    }
                    boolean enabled = moduleJson.has("enabled") && moduleJson.get("enabled").getAsBoolean();
                    if (enabled) {
                        module.setEnabled(true);
                    }
                } catch (RuntimeException moduleError) {
                    BytePhase.LOGGER.warn("Ignoring invalid module config {} in {}", entry.getKey(), source,
                            moduleError);
                }
            }
            BytePhase.LOGGER.info("Loaded config from {} with {} modules enabled",
                    source, BytePhase.modules.enabled().size());
            loadedSuccessfully = true;
        } catch (Exception e) {
            preserveUnreadableConfig = true;
            BytePhase.LOGGER.error("Failed to load config from {}; leaving it untouched", source, e);
        } finally {
            loading = false;
            if (loadedSuccessfully) {
                // Persist migrations and the version marker once. This prevents
                // intentional values equal to an old default from being
                // rewritten on every subsequent launch.
                save();
            }
        }
    }

    private static boolean isPreviousEspDefault(Module module, Setting<?> setting, JsonElement value) {
        if (!(setting instanceof ColorSetting) || !value.isJsonPrimitive()) {
            return false;
        }
        Integer previousDefault = PREVIOUS_ESP_DEFAULTS.get(module.getName() + "." + setting.getName());
        return previousDefault != null && !module.getName().equals("AntiVanish")
                && value.getAsInt() == previousDefault;
    }

    private static boolean isPreviousAntiVanishDefault(Module module, Setting<?> setting, JsonElement value) {
        return module.getName().equals("AntiVanish") && setting.getName().equals("Color")
                && setting instanceof ColorSetting && value.isJsonPrimitive()
                && value.getAsInt() == 0xFFFFAA00;
    }
}
