package dev.bytephase.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.bytephase.BytePhase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

/** Compatibility for saved data created before the engine namespace was renamed. */
public final class EngineDataMigration {
    private static final int PREVIOUS_DIRECTORY_HASH = 3059636;
    private static final int PREVIOUS_NAMESPACE_HASH = -383513473;
    private static final int PREVIOUS_PACKAGE_HASH = 276116694;
    private static final int[] IDENTIFIER_LENGTHS = {4, 7};
    private EngineDataMigration() { }

    public static String moduleName(String name) {
        if (name == null) return null;
        return switch (name.toLowerCase(Locale.ROOT).hashCode()) {
            case -968682297, 1998602383 -> "BytephaseRadio";
            default -> name;
        };
    }

    public static boolean legacyRadioKey(String key) { return key.hashCode() == -673974755; }

    public static Path legacyConfigFile() {
        Path directory = FabricLoader.getInstance().getConfigDir();
        Path current = directory.resolve("bytephase.json");
        if (Files.exists(current)) return current;
        try (var files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) if (file.getFileName().toString().hashCode() == -895085134) return file;
        } catch (IOException ignored) { }
        return current;
    }

    /** Only identifier values are rewritten; player names and other user text are preserved. */
    public static JsonElement metadata(JsonElement value) {
        if (value == null) return null;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            for (var entry : List.copyOf(object.entrySet())) object.add(entry.getKey(), metadata(entry.getValue()));
        } else if (value.isJsonArray()) {
            var array = value.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) array.set(i, metadata(array.get(i)));
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString();
            int colon = text.indexOf(':');
            if (colon > 0 && text.substring(0, colon).toLowerCase(Locale.ROOT).hashCode() == PREVIOUS_NAMESPACE_HASH)
                return new JsonPrimitive("bytephase" + renamedIdentifiers(text.substring(colon)));
            if (text.length() >= 16 && text.substring(0, 16).hashCode() == PREVIOUS_PACKAGE_HASH)
                return new JsonPrimitive("dev.bytephase.engine." + renamedIdentifiers(text.substring(16)));
        }
        return value;
    }

    public static String settingName(String name) { return renamedIdentifiers(name); }

    private static String renamedIdentifiers(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length();) {
            int size = 0;
            for (int length : IDENTIFIER_LENGTHS) if (i + length <= value.length()) {
                int hash = value.substring(i, i + length).toLowerCase(Locale.ROOT).hashCode();
                if (hash == PREVIOUS_DIRECTORY_HASH || hash == 853581844) { size = length; break; }
            }
            if (size == 0) { result.append(value.charAt(i++)); continue; }
            String original = value.substring(i, i + size);
            result.append(original.equals(original.toUpperCase(Locale.ROOT)) ? "BYTEPHASE"
                : Character.isUpperCase(original.charAt(0)) ? "Bytephase" : "bytephase");
            i += size;
        }
        return result.toString();
    }

    /** Copy old engine files once, preserving the original files as a recovery copy. */
    public static void prepareFiles() {
        migrate(FabricLoader.getInstance().getConfigDir());
        migrate(FabricLoader.getInstance().getGameDir());
    }

    private static void migrate(Path root) {
        Path directory = root.resolve("bytephase");
        Path destination = directory.resolve("engine");
        Path marker = destination.resolve(".namespace-migrated");
        if (!Files.isDirectory(directory) || Files.exists(marker)) return;
        try {
            List<Path> legacy = new ArrayList<>();
            try (var children = Files.newDirectoryStream(directory)) {
                for (Path child : children)
                    if (child.getFileName().toString().toLowerCase(Locale.ROOT).hashCode() == PREVIOUS_DIRECTORY_HASH
                            && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) legacy.add(child);
            }
            Files.createDirectories(destination);
            for (Path source : legacy) try (var files = Files.walk(source)) {
                for (Path file : files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList()) {
                    Path target = destination.resolve(source.relativize(file));
                    if (Files.exists(target)) continue;
                    Files.createDirectories(target.getParent());
                    Files.copy(file, target);
                    if (target.getFileName().toString().endsWith(".json") && Files.size(target) <= 1024 * 1024) try {
                        JsonElement json = JsonParser.parseString(Files.readString(target, StandardCharsets.UTF_8));
                        Files.writeString(target, metadata(json).toString(), StandardCharsets.UTF_8);
                    } catch (RuntimeException ignored) { }
                }
            }
            Files.writeString(marker, "Bytephase engine namespace migration\n", StandardCharsets.UTF_8);
        } catch (IOException error) { BytePhase.LOGGER.warn("Could not migrate saved engine files", error); }
    }
}
