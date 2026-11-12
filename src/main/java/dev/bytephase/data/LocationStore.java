package dev.bytephase.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.bytephase.BytePhase;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

/** Local coordinates, separated by server (or save directory) and dimension. */
public final class LocationStore {
    private static final int MAX_FINDINGS = 1024;
    private static final int MAX_WAYPOINTS = 256;
    private static final long MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final Set<String> KINDS = Set.of("waypoint", "stash", "spawner", "activity", "geode");
    private final Path path = FabricLoader.getInstance().getConfigDir()
            .resolve("bytephase").resolve("locations.json");
    private final Map<String, Location> locations = new LinkedHashMap<>();
    private String selected;
    private boolean readOnly;
    private boolean dirty;
    private int dirtyTicks;
    private long entryBytes;
    private ClientWorld lastWorld;

    public record Location(String name, String kind, String server, String dimension,
                           int x, int y, int z, long updated) {
        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }

        public boolean waypoint() {
            return kind.equals("waypoint");
        }
    }

    public static String server() {
        var mc = BytePhase.mc;
        if (mc == null || mc.world == null) return null;
        var integrated = mc.getServer();
        if (integrated != null) return "local:" + integrated.getSavePath(WorldSavePath.ROOT)
                .toAbsolutePath().normalize();
        var entry = mc.getCurrentServerEntry();
        return entry == null || entry.address == null ? null : entry.address.trim().toLowerCase(Locale.ROOT);
    }

    public static String dimension() {
        return BytePhase.mc == null || BytePhase.mc.world == null ? null
                : BytePhase.mc.world.getRegistryKey().getValue().toString();
    }

    private static String key(Location location) {
        String target = location.waypoint() ? location.name().toLowerCase(Locale.ROOT)
                : location.kind().equals("spawner") ? location.x() + "," + location.y() + "," + location.z()
                : (location.x() >> 4) + "," + (location.z() >> 4);
        return location.server() + "\n" + location.dimension() + "\n" + location.kind() + "\n" + target;
    }

    private static boolean text(String value, int limit) {
        if (value == null || value.isBlank() || value.length() > limit) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) || Character.isLowSurrogate(character)) return false;
            if (Character.isHighSurrogate(character)
                    && (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index)))) return false;
        }
        return true;
    }

    private static boolean valid(Location location) {
        return text(location.name(), 80) && text(location.server(), 1024)
                && text(location.dimension(), 256) && KINDS.contains(location.kind())
                && Math.abs((long) location.x()) <= 30_000_000
                && Math.abs((long) location.z()) <= 30_000_000
                && Math.abs((long) location.y()) <= 4096 && location.updated() >= 0;
    }

    private static String string(JsonObject object, String name) throws IOException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("Invalid location string");
        }
        return value.getAsString();
    }

    private static long integer(JsonObject object, String name) throws IOException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IOException("Invalid location number");
        }
        try {
            if (value.getAsString().length() > 64) throw new IOException("Location number is too long");
            return value.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException error) {
            throw new IOException("Invalid location integer");
        }
    }

    private static JsonElement readJson(JsonReader reader, int depth) throws IOException {
        if (depth > 3) throw new IOException("Location data is nested too deeply");
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    // Duplicate version/coordinate fields must not silently replace earlier values.
                    if (object.has(name) || object.size() >= 16) throw new IOException("Invalid location fields");
                    object.add(name, readJson(reader, depth + 1));
                }
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    if (array.size() >= MAX_FINDINGS + MAX_WAYPOINTS) {
                        throw new IOException("Too many location entries");
                    }
                    array.add(readJson(reader, depth + 1));
                }
                reader.endArray();
                return array;
            case STRING, NUMBER, BOOLEAN, NULL:
                return JsonParser.parseReader(reader);
            default:
                throw new IOException("Invalid location JSON");
        }
    }

    private byte[] readFile() throws IOException {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.size() > MAX_FILE_BYTES) {
                throw new IOException("Invalid location file");
            }
        } catch (NoSuchFileException error) {
            return null;
        }
        // Bound the actual read as well: the file can grow after the size check.
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes((int) MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES) throw new IOException("Location file is too large");
            return bytes;
        }
    }

    public void load() {
        // A reload must not discard edits that have not reached disk yet.
        if (dirty) return;
        try {
            Map<String, Location> loaded = new LinkedHashMap<>();
            String loadedSelected = null;
            byte[] bytes = readFile();
            if (bytes != null) {
                JsonObject root;
                String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                try (JsonReader reader = new JsonReader(new StringReader(json))) {
                    reader.setStrictness(Strictness.STRICT);
                    root = readJson(reader, 0).getAsJsonObject();
                    if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing location data");
                }
                JsonElement entries = root.get("locations");
                if (integer(root, "version") != 1 || entries == null || !entries.isJsonArray()) {
                    throw new IOException("Unsupported location format");
                }
                if (entries.getAsJsonArray().size() > MAX_FINDINGS + MAX_WAYPOINTS) {
                    throw new IOException("Too many location entries");
                }
                for (JsonElement element : entries.getAsJsonArray()) {
                    JsonObject value = element.getAsJsonObject();
                    Location location = new Location(string(value, "name"), string(value, "kind"),
                            string(value, "server"), string(value, "dimension"),
                            Math.toIntExact(integer(value, "x")), Math.toIntExact(integer(value, "y")),
                            Math.toIntExact(integer(value, "z")), integer(value, "updated"));
                    if (!valid(location)) throw new IOException("Invalid location entry");
                    String key = key(location);
                    Location previous = loaded.get(key);
                    if (previous == null || previous.updated() < location.updated()) loaded.put(key, location);
                }
                if (root.has("selected") && !root.get("selected").isJsonNull()) {
                    loadedSelected = string(root, "selected");
                    Location target = loaded.get(loadedSelected);
                    if (target == null || !target.waypoint()) loadedSelected = null;
                }
            }
            long waypoints = loaded.values().stream().filter(Location::waypoint).count();
            if (waypoints > MAX_WAYPOINTS || loaded.size() - waypoints > MAX_FINDINGS) {
                throw new IOException("Location capacity exceeded");
            }
            long loadedBytes = loaded.values().stream().mapToLong(LocationStore::size).sum();
            if (!fits(loadedBytes, loaded.size(), loadedSelected)) {
                throw new IOException("Location file exceeds the serialized size limit");
            }
            // Commit only after every entry and the selection have been validated.
            locations.clear();
            locations.putAll(loaded);
            selected = loadedSelected;
            entryBytes = loadedBytes;
            readOnly = false;
            dirtyTicks = 0;
        } catch (Exception error) {
            readOnly = true;
            // Parser exceptions can contain private coordinates and server identifiers.
            BytePhase.LOGGER.warn("Cannot read local locations; preserving the original file ({})",
                    error.getClass().getSimpleName());
        }
    }

    private void writable() {
        if (readOnly) throw new IllegalStateException("The location file could not be read; restore it before editing.");
    }

    public List<Location> current() {
        String server = server();
        String dimension = dimension();
        if (server == null || dimension == null) return List.of();
        return locations.values().stream().filter(location -> location.server().equals(server)
                && location.dimension().equals(dimension))
                .sorted(Comparator.comparing(Location::waypoint).reversed()
                        .thenComparing(Comparator.comparingLong(Location::updated).reversed()))
                .toList();
    }

    public Location addWaypoint(String name, BlockPos pos) {
        writable();
        if (name == null) throw new IllegalArgumentException("Use a waypoint name with 1–48 characters.");
        name = name.trim();
        if (!text(name, 48)) {
            throw new IllegalArgumentException("Use a waypoint name with 1–48 characters.");
        }
        String server = server();
        String dimension = dimension();
        if (server == null || dimension == null) throw new IllegalStateException("Join a world first.");
        if (pos == null) throw new IllegalArgumentException("Choose a waypoint position.");
        Location location = new Location(name, "waypoint", server, dimension,
                pos.getX(), pos.getY(), pos.getZ(), System.currentTimeMillis());
        if (!valid(location)) throw new IllegalArgumentException("Invalid waypoint position or world scope.");
        String key = key(location);
        Location previous = locations.get(key);
        if (previous == null && locations.values().stream().filter(Location::waypoint).count()
                >= MAX_WAYPOINTS) throw new IllegalStateException("Waypoint limit reached (256).");
        long bytes = entryBytes + size(location) - (previous == null ? 0 : size(previous));
        if (!fits(bytes, locations.size() + (previous == null ? 1 : 0), key)) {
            throw new IllegalStateException("Location file size limit reached; remove old findings first.");
        }
        put(location);
        selected = key;
        markDirty();
        return location;
    }

    /** Returns true only for a newly recorded finding, allowing callers to deduplicate alerts. */
    public boolean remember(String kind, String name, BlockPos pos) {
        String server = server();
        String dimension = dimension();
        if (readOnly || server == null || dimension == null || kind == null
                || kind.equals("waypoint") || !KINDS.contains(kind) || pos == null) return false;
        Location location = new Location(name, kind, server, dimension,
                pos.getX(), pos.getY(), pos.getZ(), System.currentTimeMillis());
        if (!valid(location)) return false;
        String key = key(location);
        Location previous = locations.get(key);
        // A saved timestamp ahead of the clock must not suppress updates indefinitely.
        if (previous != null && previous.updated() <= location.updated()
                && location.updated() - previous.updated() < 60_000L) return false;
        long bytes = entryBytes + size(location) - (previous == null ? 0 : size(previous));
        int count = locations.size() + (previous == null ? 1 : 0);
        long findings = locations.values().stream().filter(value -> !value.waypoint()).count()
                + (previous == null ? 1 : 0);
        List<Location> evicted = new ArrayList<>();
        if (findings > MAX_FINDINGS || !fits(bytes, count, selected)) {
            List<Location> oldest = locations.values().stream().filter(value -> !value.waypoint()
                    && value != previous).sorted(Comparator.comparingLong(Location::updated)).toList();
            for (Location value : oldest) {
                evicted.add(value);
                bytes -= size(value);
                count--;
                findings--;
                if (findings <= MAX_FINDINGS && fits(bytes, count, selected)) break;
            }
            // Plan eviction first so an oversized finding cannot destroy existing entries.
            if (findings > MAX_FINDINGS || !fits(bytes, count, selected)) return false;
        }
        for (Location value : evicted) remove(key(value));
        put(location);
        markDirty();
        return previous == null;
    }

    public Location select(String name) {
        writable();
        Location location = waypoint(name);
        if (location == null) throw new IllegalArgumentException("No waypoint named " + name);
        String key = key(location);
        if (!key.equals(selected)) {
            if (!fits(entryBytes, locations.size(), key)) {
                throw new IllegalStateException("Location file size limit reached; remove old findings first.");
            }
            selected = key;
            markDirty();
        }
        return location;
    }

    private Location waypoint(String name) {
        if (name == null) return null;
        String server = server();
        String dimension = dimension();
        if (server == null || dimension == null) return null;
        // Use exactly the same case folding as insertion, including for Unicode names.
        String prefix = server + "\n" + dimension + "\nwaypoint\n";
        Location exact = locations.get(prefix + name.toLowerCase(Locale.ROOT));
        return exact != null ? exact : locations.get(prefix + name.trim().toLowerCase(Locale.ROOT));
    }

    public Location selected() {
        Location location = locations.get(selected);
        return location != null && location.waypoint() && location.server().equals(server())
                && location.dimension().equals(dimension()) ? location : null;
    }

    public boolean removeWaypoint(String name) {
        writable();
        Location location = waypoint(name);
        if (location == null) return false;
        String key = key(location);
        remove(key);
        markDirty();
        return true;
    }

    public int clearFindings() {
        writable();
        List<Location> current = current();
        int removed = 0;
        for (Location location : current) {
            if (!location.waypoint() && remove(key(location)) != null) removed++;
        }
        if (removed > 0) markDirty();
        return removed;
    }

    /** Manual waypoints never expire. Only discoveries older than the requested age are pruned. */
    public int prune(int days) {
        writable();
        if (days < 1) throw new IllegalArgumentException("Use a positive finding age in days.");
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        int before = locations.size();
        for (Location location : new ArrayList<>(locations.values())) {
            if (!location.waypoint() && location.updated() < cutoff) remove(key(location));
        }
        int removed = before - locations.size();
        if (removed > 0) markDirty();
        return removed;
    }

    private void markDirty() {
        if (!dirty) {
            dirtyTicks = 0;
            lastWorld = BytePhase.mc == null ? null : BytePhase.mc.world;
        }
        dirty = true;
    }

    public void tick() {
        ClientWorld world = BytePhase.mc == null ? null : BytePhase.mc.world;
        boolean changedWorld = world != lastWorld;
        lastWorld = world;
        if (dirty && (changedWorld || ++dirtyTicks >= 40)) save();
    }

    private static JsonObject json(Location location) {
        JsonObject value = new JsonObject();
        value.addProperty("name", location.name());
        value.addProperty("kind", location.kind());
        value.addProperty("server", location.server());
        value.addProperty("dimension", location.dimension());
        value.addProperty("x", location.x());
        value.addProperty("y", location.y());
        value.addProperty("z", location.z());
        value.addProperty("updated", location.updated());
        return value;
    }

    private static JsonObject document(JsonArray entries, String selection) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        if (selection != null) root.addProperty("selected", selection);
        root.add("locations", entries);
        return root;
    }

    private static long size(Location location) {
        return json(location).toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private static boolean fits(long bytes, int count, String selection) {
        long overhead = document(new JsonArray(), selection).toString().getBytes(StandardCharsets.UTF_8).length;
        return overhead + bytes + Math.max(0, count - 1) <= MAX_FILE_BYTES;
    }

    private void put(Location location) {
        Location previous = locations.put(key(location), location);
        entryBytes += size(location) - (previous == null ? 0 : size(previous));
    }

    private Location remove(String key) {
        Location removed = locations.remove(key);
        if (removed != null) {
            entryBytes -= size(removed);
            if (key.equals(selected)) selected = null;
        }
        return removed;
    }

    public void save() {
        if (readOnly || !dirty) return;
        Path temporary = null;
        try {
            JsonArray entries = new JsonArray();
            for (Location location : new ArrayList<>(locations.values())) {
                entries.add(json(location));
            }
            byte[] bytes = document(entries, selected).toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_FILE_BYTES) throw new IOException("Location file is too large");
            Files.createDirectories(path.getParent());
            if (Files.getFileStore(path.getParent()).supportsFileAttributeView(PosixFileAttributeView.class)) {
                temporary = Files.createTempFile(path.getParent(), "locations-", ".tmp",
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } else {
                temporary = Files.createTempFile(path.getParent(), "locations-", ".tmp");
            }
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            dirtyTicks = 0;
        } catch (IOException | SecurityException error) {
            dirtyTicks = 0;
            BytePhase.LOGGER.warn("Could not save local locations; pending changes retained ({})",
                    error.getClass().getSimpleName());
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException | SecurityException error) {
                    BytePhase.LOGGER.warn("Could not remove the temporary local location file ({})",
                            error.getClass().getSimpleName());
                }
            }
        }
    }
}
