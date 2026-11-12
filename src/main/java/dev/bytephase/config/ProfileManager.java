package dev.bytephase.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.bytephase.BytePhase;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.setting.Setting;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

/** Named configurations contain module state and settings, never coordinates or server data. */
public final class ProfileManager {
    private static final int VERSION = 1;
    private static final int MAX_PROFILES = 128;
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_DEPTH = 8;
    private static final int MAX_VALUES = 32768;
    private static final int MAX_DIRECTORY_ENTRIES = 4096;
    private static final int MAX_TEMP_ATTEMPTS = 8;
    private static final String NAME_PATTERN = "[A-Za-z0-9_-]{1,32}";
    private final Path configDirectory = FabricLoader.getInstance().getConfigDir();

    private record ModuleValues(boolean enabled, Map<Setting<?>, JsonElement> settings) {
    }

    /** Only this exception's fixed message is suitable for displaying a load/recovery failure. */
    public static final class LoadException extends IllegalStateException {
        private LoadException(boolean restored, RuntimeException cause) {
            super(restored ? "Load failed; previous configuration restored."
                    : "Load failed; recovery incomplete. Check module states.", cause);
        }
    }

    /*
     * Secure streams keep file operations attached to the opened directory even
     * if a fixed child is replaced by a link. Providers without them are checked
     * before and after each operation and still refuse final-component links.
     */
    private final class ProfileDirectory implements AutoCloseable {
        private final Path path;
        private final DirectoryStream<Path> files;
        private final SecureDirectoryStream<Path> secure;
        private final BasicFileAttributes parentBefore;
        private final BasicFileAttributes directoryBefore;

        private ProfileDirectory(Path path, DirectoryStream<Path> files,
                BasicFileAttributes parentBefore, BasicFileAttributes directoryBefore) {
            this.path = path;
            this.files = files;
            this.secure = files instanceof SecureDirectoryStream<Path> stream ? stream : null;
            this.parentBefore = parentBefore;
            this.directoryBefore = directoryBefore;
        }

        private void checkLocation() throws IOException {
            if (!path.equals(directory(false))
                    || !sameDirectory(parentBefore, attributes(path.getParent()))
                    || !sameDirectory(directoryBefore, attributes(path))) {
                throw new IOException("Profile storage changed during the operation.");
            }
            if (secure != null && !sameDirectory(directoryBefore,
                    secure.getFileAttributeView(BasicFileAttributeView.class).readAttributes())) {
                throw new IOException("Profile storage changed during the operation.");
            }
        }

        private BasicFileAttributes fileAttributes(Path filename) throws IOException {
            if (secure == null) return attributes(path.resolve(filename));
            try {
                return secure.getFileAttributeView(filename, BasicFileAttributeView.class,
                        LinkOption.NOFOLLOW_LINKS).readAttributes();
            } catch (NoSuchFileException ignored) {
                return null;
            }
        }

        private boolean regularFile(Path filename) throws IOException {
            BasicFileAttributes attributes = fileAttributes(filename);
            if (attributes == null) return false;
            if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                throw new IOException("Profile must be a regular file, not a link.");
            }
            return true;
        }

        private SeekableByteChannel open(Path filename, Set<? extends java.nio.file.OpenOption> options,
                FileAttribute<?>... permissions) throws IOException {
            checkLocation();
            return secure == null
                    ? Files.newByteChannel(path.resolve(filename), options, permissions)
                    : secure.newByteChannel(filename, options, permissions);
        }

        private boolean deleteFile(Path filename) throws IOException {
            try {
                if (secure != null) secure.deleteFile(filename);
                else return Files.deleteIfExists(path.resolve(filename));
                return true;
            } catch (NoSuchFileException ignored) {
                return false;
            }
        }

        private void move(Path source, Path target) throws IOException {
            checkLocation();
            regularFile(target);
            if (secure != null) {
                secure.move(source, secure, target);
            } else {
                try {
                    Files.move(path.resolve(source), path.resolve(target),
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    checkLocation();
                    regularFile(target);
                    Files.move(path.resolve(source), path.resolve(target), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            checkLocation();
        }

        @Override
        public void close() throws IOException {
            files.close();
        }
    }

    private record TemporaryProfile(ProfileDirectory directory, Path filename,
            SeekableByteChannel channel) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            try {
                channel.close();
            } finally {
                // Close first so cleanup also works on storage that locks open files.
                directory.deleteFile(filename);
            }
        }
    }

    private static void validateName(String name) {
        if (name == null || !name.matches(NAME_PATTERN)) {
            throw new IllegalArgumentException("Use 1–32 letters, digits, underscores or dashes.");
        }
    }

    /*
     * Fabric's configured root is trusted (and may itself be a deliberate link).
     * Reject links in fixed children and profile files.
     */
    private Path directory(boolean create) throws IOException {
        Path current;
        try {
            current = configDirectory.toRealPath();
        } catch (NoSuchFileException error) {
            if (!create) return null;
            Files.createDirectories(configDirectory);
            current = configDirectory.toRealPath();
        }
        for (String child : List.of("bytephase", "profiles")) {
            current = current.resolve(child);
            BasicFileAttributes attributes = attributes(current);
            if (attributes == null && create) {
                try {
                    if (Files.getFileAttributeView(current.getParent(), PosixFileAttributeView.class) != null) {
                        Files.createDirectory(current, PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
                    } else {
                        Files.createDirectory(current);
                    }
                } catch (FileAlreadyExistsException ignored) {
                    // Recheck a concurrently created child without following links.
                }
                attributes = attributes(current);
            }
            if (attributes == null) {
                if (create) throw new IOException("Profile storage changed during creation.");
                return null;
            }
            if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
                throw new IOException("Profile storage is not a safe directory.");
            }
        }
        return current;
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException ignored) {
            return null;
        }
    }

    private ProfileDirectory openDirectory(boolean create) throws IOException {
        Path path = directory(create);
        if (path == null) return null;
        BasicFileAttributes parentBefore = attributes(path.getParent());
        BasicFileAttributes directoryBefore = attributes(path);
        DirectoryStream<Path> files = Files.newDirectoryStream(path.getParent().getParent());
        try {
            if (files instanceof SecureDirectoryStream<Path> root) {
                DirectoryStream<Path> parent = root.newDirectoryStream(Path.of("bytephase"),
                        LinkOption.NOFOLLOW_LINKS);
                try {
                    DirectoryStream<Path> child = ((SecureDirectoryStream<Path>) parent)
                            .newDirectoryStream(Path.of("profiles"), LinkOption.NOFOLLOW_LINKS);
                    try {
                        parent.close();
                        parent = null;
                        files.close();
                        files = child;
                    } catch (IOException | RuntimeException error) {
                        child.close();
                        throw error;
                    }
                } finally {
                    if (parent != null) parent.close();
                }
            } else {
                files.close();
                files = Files.newDirectoryStream(path);
            }
            ProfileDirectory opened = new ProfileDirectory(path, files, parentBefore, directoryBefore);
            opened.checkLocation();
            return opened;
        } catch (IOException | RuntimeException error) {
            try {
                files.close();
            } catch (IOException | RuntimeException closeError) {
                suppress(error, closeError);
            }
            throw error;
        }
    }

    public synchronized List<String> list() throws IOException {
        try {
            try (ProfileDirectory directory = openDirectory(false)) {
                return directory == null ? List.of() : list(directory);
            }
        } catch (IOException | RuntimeException error) {
            throw new IOException("Could not list profiles. Check storage access and the 128-profile limit.", error);
        }
    }

    private static List<String> list(ProfileDirectory directory) throws IOException {
        directory.checkLocation();
        List<String> names = new ArrayList<>();
        Set<String> aliases = new HashSet<>();
        int entries = 0;
        for (Path path : directory.files) {
            if (++entries > MAX_DIRECTORY_ENTRIES) {
                throw new IOException("Profile storage contains too many files.");
            }
            String filename = path.getFileName().toString();
            if (!filename.matches(NAME_PATTERN + "\\.json")) continue;
            BasicFileAttributes attributes = directory.fileAttributes(path.getFileName());
            if (attributes == null || !attributes.isRegularFile() || attributes.isSymbolicLink()) continue;
            if (names.size() == MAX_PROFILES) {
                throw new IOException("There are more than 128 profiles.");
            }
            String name = filename.substring(0, filename.length() - 5);
            if (!aliases.add(name.toLowerCase(Locale.ROOT))) {
                throw new IOException("Profile names differ only by letter case.");
            }
            names.add(name);
        }
        directory.checkLocation();
        names.sort(String::compareTo);
        return List.copyOf(names);
    }

    public synchronized void save(String name) throws IOException {
        validateName(name);
        requireClientThread();
        try {
            JsonObject root = new JsonObject();
            root.addProperty("bytephaseProfile", VERSION);
            JsonObject modules = new JsonObject();
            for (var entry : snapshot().entrySet()) {
                JsonObject value = new JsonObject();
                value.addProperty("enabled", entry.getValue().enabled());
                JsonObject settings = new JsonObject();
                for (var setting : entry.getValue().settings().entrySet()) {
                    settings.add(setting.getKey().getName(), setting.getValue());
                }
                value.add("settings", settings);
                modules.add(entry.getKey().getName(), value);
            }
            root.add("modules", modules);
            String json = root.toString();
            if (json.length() > MAX_BYTES) throw new IOException("Profile is too large.");
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_BYTES) throw new IOException("Profile is too large.");
            // Ensure a saved profile meets exactly the same limits as a loaded one.
            parse(json);

            try (ProfileDirectory directory = openDirectory(true)) {
                Path target = Path.of(name + ".json");
                boolean existing = directory.regularFile(target);
                List<String> names = list(directory);
                if (names.stream().anyMatch(saved -> saved.equalsIgnoreCase(name) && !saved.equals(name))) {
                    throw new IllegalArgumentException("Use the existing profile name's letter case.");
                }
                if (!existing && names.size() >= MAX_PROFILES) {
                    throw new IllegalStateException("Profile limit reached (128).");
                }
                try (TemporaryProfile temporary = privateTemporary(directory)) {
                    SeekableByteChannel channel = temporary.channel();
                    ByteBuffer data = ByteBuffer.wrap(bytes);
                    while (data.hasRemaining()) channel.write(data);
                    if (channel instanceof FileChannel file) file.force(true);
                    channel.close();
                    directory.move(temporary.filename(), target);
                }
            }
        } catch (IOException | SecurityException error) {
            throw new IOException("Could not save profile. Check storage access, size and available space.", error);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Could not save profile. Check settings and the 128-profile limit.", error);
        }
    }

    private static boolean sameDirectory(BasicFileAttributes before, BasicFileAttributes after) {
        if (before == null || after == null || !after.isDirectory() || after.isSymbolicLink()) return false;
        if (before.fileKey() != null) return before.fileKey().equals(after.fileKey());
        return before.creationTime().equals(after.creationTime());
    }

    private static TemporaryProfile privateTemporary(ProfileDirectory directory) throws IOException {
        boolean posix = Files.getFileAttributeView(directory.path, PosixFileAttributeView.class) != null;
        for (int attempt = 0; attempt < MAX_TEMP_ATTEMPTS; attempt++) {
            Path filename = Path.of(".bytephase-profile-" + UUID.randomUUID() + ".tmp");
            SeekableByteChannel channel;
            try {
                FileAttribute<?>[] permissions = posix
                        ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rw-------"))}
                        : new FileAttribute<?>[0];
                channel = directory.open(filename, Set.of(StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS), permissions);
            } catch (FileAlreadyExistsException ignored) {
                continue;
            }
            TemporaryProfile temporary = new TemporaryProfile(directory, filename, channel);
            try {
                if (!posix) {
                    // Restrict the still-empty file before writing profile contents.
                    AclFileAttributeView acl = directory.secure == null
                            ? Files.getFileAttributeView(directory.path.resolve(filename),
                                    AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
                            : directory.secure.getFileAttributeView(filename,
                                    AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                    if (acl == null) throw new IOException("Private profiles are unsupported by this storage.");
                    acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                            .setPrincipal(acl.getOwner())
                            .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
                }
                directory.checkLocation();
                return temporary;
            } catch (IOException | RuntimeException error) {
                try {
                    temporary.close();
                } catch (IOException | RuntimeException cleanupError) {
                    suppress(error, cleanupError);
                }
                throw error;
            }
        }
        throw new IOException("Could not create a temporary profile.");
    }

    public synchronized void load(String name) throws IOException {
        validateName(name);
        requireClientThread();
        Map<Module, ModuleValues> plan;
        Map<Module, ModuleValues> previous;
        try {
            try (ProfileDirectory directory = openDirectory(false)) {
                if (directory == null) throw new IOException("No saved profile with that name.");
                Path source = Path.of(name + ".json");
                if (!directory.regularFile(source)) throw new IOException("No saved profile with that name.");
                plan = plan(parse(read(directory, source)));
            }
            // Snapshot every setting, including omitted modules: hooks can affect other modules.
            previous = snapshot();
        } catch (IOException | SecurityException error) {
            throw new IOException("Could not read profile. Check its name, storage access and size.", error);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Could not load profile. Check its version and settings.", error);
        }
        try {
            for (Module module : plan.keySet()) setEnabled(module, false);
            for (Module module : plan.keySet()) checkEnabled(module, false);
            for (var entry : plan.entrySet()) restoreSettings(entry.getValue().settings());
            for (var entry : plan.entrySet()) setEnabled(entry.getKey(), entry.getValue().enabled());
            // Later hooks may have changed an earlier module or an omitted setting.
            verify(previous, plan);
            if (BytePhase.config != null) BytePhase.config.requestSave();
        } catch (RuntimeException error) {
            throw new LoadException(rollback(previous, error), error);
        }
    }

    private static String read(ProfileDirectory directory, Path source) throws IOException {
        try (var channel = directory.open(source, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            if (channel.size() > MAX_BYTES) throw new IOException("Profile is too large.");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            int count;
            while ((count = channel.read(buffer)) != -1) {
                if (bytes.size() + count > MAX_BYTES) throw new IOException("Profile is too large.");
                bytes.write(buffer.array(), 0, count);
                buffer.clear();
            }
            directory.checkLocation();
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        }
    }

    private static JsonObject parse(String json) {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement value = readValue(reader, 0, new int[] {MAX_VALUES});
            if (!value.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) throw invalid();
            JsonObject root = value.getAsJsonObject();
            JsonElement version = root.get("bytephaseProfile");
            if (version == null || !version.isJsonPrimitive()
                    || !version.getAsJsonPrimitive().isNumber() || !version.getAsString().equals("1")) {
                throw new IllegalArgumentException("Unsupported or invalid Bytephase profile version.");
            }
            if (root.size() != 2 || !root.has("modules") || !root.get("modules").isJsonObject()) throw invalid();
            return root;
        } catch (IOException | RuntimeException error) {
            // Parser diagnostics can include attacker-controlled keys or contents.
            throw new IllegalArgumentException("Invalid profile. Use a supported Bytephase profile with valid settings.",
                    error);
        }
    }

    private static JsonElement readValue(JsonReader reader, int depth, int[] remaining) throws IOException {
        if (depth > MAX_DEPTH || --remaining[0] < 0) throw invalid();
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                JsonObject object = new JsonObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (key.isEmpty() || key.length() > 128 || object.has(key)) throw invalid();
                    object.add(key, readValue(reader, depth + 1, remaining));
                }
                reader.endObject();
                yield object;
            }
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case STRING -> {
                String value = reader.nextString();
                if (value.length() > 4096) throw invalid();
                yield new JsonPrimitive(value);
            }
            case NUMBER -> {
                String number = reader.nextString();
                if (number.length() > 128 || !Double.isFinite(Double.parseDouble(number))) throw invalid();
                // The strict reader already validated syntax; preserve the lexeme for the version check.
                yield JsonParser.parseString(number);
            }
            // Profiles have no arrays, nulls, comments or additional documents.
            default -> throw invalid();
        };
    }

    private static Map<Module, ModuleValues> plan(JsonObject root) {
        Map<String, Module> registered = registry();
        Map<Module, ModuleValues> result = new LinkedHashMap<>();
        Set<String> aliases = new HashSet<>();
        JsonObject modules = root.getAsJsonObject("modules");
        for (var entry : modules.entrySet()) {
            String alias = entry.getKey().toLowerCase(Locale.ROOT);
            if (!aliases.add(alias) || !entry.getValue().isJsonObject()) throw invalid();
            JsonObject value = entry.getValue().getAsJsonObject();
            if (value.size() != 2 || !value.has("enabled") || !value.get("enabled").isJsonPrimitive()
                    || !value.get("enabled").getAsJsonPrimitive().isBoolean()
                    || !value.has("settings") || !value.get("settings").isJsonObject()) throw invalid();
            JsonObject saved = value.getAsJsonObject("settings");
            Set<String> settingAliases = new HashSet<>();
            for (var setting : saved.entrySet()) {
                if (!settingAliases.add(setting.getKey().toLowerCase(Locale.ROOT))
                        || !setting.getValue().isJsonPrimitive()) throw invalid();
            }
            Module module = registered.get(alias);
            if (module == null) continue; // Well-formed settings from removed/newer modules are ignored.
            Map<Setting<?>, JsonElement> settings = new LinkedHashMap<>();
            for (Setting<?> setting : module.getSettings()) {
                JsonElement element = saved.get(setting.getName());
                if (element != null) {
                    validateSetting(setting, element);
                    settings.put(setting, element.deepCopy());
                } else {
                    // Known setting names use ConfigManager's exact spelling; do not silently skip an alias.
                    for (String key : saved.keySet()) {
                        if (key.equalsIgnoreCase(setting.getName())) throw invalid();
                    }
                }
            }
            result.put(module, new ModuleValues(value.get("enabled").getAsBoolean(), settings));
        }
        // Apply in registry order regardless of JSON key order.
        Map<Module, ModuleValues> ordered = new LinkedHashMap<>();
        for (Module module : registered.values()) {
            if (result.containsKey(module)) ordered.put(module, result.get(module));
        }
        return ordered;
    }

    private static Map<String, Module> registry() {
        if (BytePhase.modules == null) throw new IllegalStateException("Modules are not ready.");
        Map<String, Module> registered = new LinkedHashMap<>();
        for (Module module : BytePhase.modules.all()) {
            if (registered.putIfAbsent(module.getName().toLowerCase(Locale.ROOT), module) != null) {
                throw new IllegalStateException("Module names are ambiguous.");
            }
            Set<String> names = new HashSet<>();
            for (Setting<?> setting : module.getSettings()) {
                if (!names.add(setting.getName().toLowerCase(Locale.ROOT))) {
                    throw new IllegalStateException("Setting names are ambiguous.");
                }
            }
        }
        return registered;
    }

    private static Map<Module, ModuleValues> snapshot() {
        Map<Module, ModuleValues> previous = new LinkedHashMap<>();
        for (Module module : registry().values()) {
            Map<Setting<?>, JsonElement> settings = new LinkedHashMap<>();
            for (Setting<?> setting : module.getSettings()) {
                JsonElement value = setting.toJson();
                validateSetting(setting, value);
                settings.put(setting, value.deepCopy());
            }
            previous.put(module, new ModuleValues(module.isEnabled(), settings));
        }
        return previous;
    }

    private static void validateSetting(Setting<?> setting, JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) throw invalid();
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (setting instanceof BooleanSetting) {
            if (!primitive.isBoolean()) throw invalid();
        } else if (setting instanceof ModeSetting mode) {
            if (!primitive.isString() || !Arrays.asList(mode.getModes()).contains(primitive.getAsString())) {
                throw invalid();
            }
        } else if (setting instanceof NumberSetting number) {
            if (!primitive.isNumber()) throw invalid();
            double saved = primitive.getAsDouble();
            if (!Double.isFinite(saved) || saved < number.getMin() || saved > number.getMax()
                    || (saved == 0.0 && primitive.getAsBigDecimal().signum() != 0)) throw invalid();
        } else if (setting instanceof ColorSetting) {
            if (!primitive.isNumber()) throw invalid();
            try {
                primitive.getAsBigDecimal().intValueExact();
            } catch (ArithmeticException | NumberFormatException error) {
                throw invalid();
            }
        } else {
            throw new IllegalArgumentException("Unsupported profile setting type.");
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid profile structure or setting value.");
    }

    private static void requireClientThread() {
        if (BytePhase.mc != null && !BytePhase.mc.isOnThread()) {
            throw new IllegalStateException("Use profiles from the client thread.");
        }
    }

    private static void setEnabled(Module module, boolean enabled) {
        module.setEnabled(enabled);
        checkEnabled(module, enabled);
    }

    private static void checkEnabled(Module module, boolean enabled) {
        // Module.setEnabled swallows hook exceptions, so an enable failure must be detected by its flag.
        if (module.isEnabled() != enabled) throw new IllegalStateException("A module could not change state.");
    }

    private static void restoreSetting(Setting<?> setting, JsonElement value) {
        setting.fromJson(value.deepCopy());
        if (!value.equals(setting.toJson())) throw new IllegalStateException("A setting could not be applied.");
    }

    private static void restoreSettings(Map<Setting<?>, JsonElement> settings) {
        for (var entry : settings.entrySet()) restoreSetting(entry.getKey(), entry.getValue());
    }

    private static void verify(Map<Module, ModuleValues> previous, Map<Module, ModuleValues> plan) {
        for (var entry : previous.entrySet()) {
            ModuleValues wanted = plan.get(entry.getKey());
            checkEnabled(entry.getKey(), wanted == null ? entry.getValue().enabled() : wanted.enabled());
            for (var setting : entry.getValue().settings().entrySet()) {
                JsonElement expected = wanted == null ? setting.getValue()
                        : wanted.settings().getOrDefault(setting.getKey(), setting.getValue());
                if (!expected.equals(setting.getKey().toJson())) {
                    throw new IllegalStateException("A module changed profile settings.");
                }
            }
        }
    }

    private static boolean rollback(Map<Module, ModuleValues> previous, RuntimeException failure) {
        boolean restored = disableAll(previous, failure);
        restored &= restoreAllSettings(previous, failure);
        // No module may run until ALL settings have been restored successfully.
        if (!restored) return false;
        for (var entry : previous.entrySet()) {
            try {
                setEnabled(entry.getKey(), entry.getValue().enabled());
            } catch (RuntimeException error) {
                suppress(failure, error);
                restored = false;
            }
        }
        try {
            verify(previous, Map.of());
        } catch (RuntimeException error) {
            suppress(failure, error);
            restored = false;
        }
        if (!restored) {
            // A recovery enable hook may have changed settings: stop everything and restore them again.
            disableAll(previous, failure);
            restoreAllSettings(previous, failure);
        }
        return restored;
    }

    private static boolean disableAll(Map<Module, ModuleValues> previous, RuntimeException failure) {
        boolean disabled = true;
        for (Module module : previous.keySet()) {
            try {
                setEnabled(module, false);
            } catch (RuntimeException error) {
                suppress(failure, error);
                disabled = false;
            }
        }
        // One bounded extra pass handles a later disable hook re-enabling an earlier module.
        for (Module module : previous.keySet()) {
            if (!module.isEnabled()) continue;
            try {
                setEnabled(module, false);
            } catch (RuntimeException error) {
                suppress(failure, error);
                disabled = false;
            }
        }
        for (Module module : previous.keySet()) if (module.isEnabled()) disabled = false;
        return disabled;
    }

    private static boolean restoreAllSettings(Map<Module, ModuleValues> previous, RuntimeException failure) {
        boolean restored = true;
        for (ModuleValues module : previous.values()) {
            for (var entry : module.settings().entrySet()) {
                try {
                    restoreSetting(entry.getKey(), entry.getValue());
                } catch (RuntimeException error) {
                    suppress(failure, error);
                    restored = false;
                }
            }
        }
        return restored;
    }

    private static void suppress(Throwable failure, Throwable recoveryError) {
        // A hook can repeatedly throw the same exception instance; self-suppression would abort recovery.
        if (failure != recoveryError) failure.addSuppressed(recoveryError);
    }

    public synchronized boolean delete(String name) throws IOException {
        validateName(name);
        try {
            try (ProfileDirectory directory = openDirectory(false)) {
                if (directory == null) return false;
                Path target = Path.of(name + ".json");
                directory.checkLocation();
                boolean deleted = directory.regularFile(target) && directory.deleteFile(target);
                directory.checkLocation();
                return deleted;
            }
        } catch (IOException | RuntimeException error) {
            throw new IOException("Could not delete profile. Check access to profile storage.", error);
        }
    }
}
