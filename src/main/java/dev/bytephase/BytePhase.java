package dev.bytephase;

import dev.bytephase.config.ConfigManager;
import dev.bytephase.config.ProfileManager;
import dev.bytephase.data.LocationStore;
import dev.bytephase.module.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BytePhase {
    public static final String NAME = "Bytephase";
    public static final String VERSION = "0.1.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    public static MinecraftClient mc;
    public static ModuleManager modules;
    public static ConfigManager config;
    public static ProfileManager profiles;
    public static LocationStore locations;

    private static String version;

    private BytePhase() {
    }

    public static String version() {
        if (version == null) {
            version = FabricLoader.getInstance().getModContainer("bytephase")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse(VERSION);
        }
        return version;
    }
}
