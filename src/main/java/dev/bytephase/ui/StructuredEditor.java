package dev.bytephase.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.bytephase.BytePhase;
import dev.bytephase.setting.ActionSetting;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.LabelSetting;
import dev.bytephase.setting.Setting;
import dev.bytephase.setting.StringSetting;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.screen.Screen;

/** Native forms for nested feature data; Apply commits, Back leaves the backend unchanged. */
public final class StructuredEditor {
    private StructuredEditor() { }

    public static Screen open(String title, Screen parent, Supplier<JsonElement> read, Consumer<JsonElement> write) {
        Session session = new Session(read, write);
        return page(title, parent, session, List.of());
    }

    private static Screen page(String title, Screen parent, Session session, List<Object> path) {
        return ClickGuiScreen.editor(title, parent, () -> fields(title, session, path));
    }

    private static List<Setting<?>> fields(String title, Session session, List<Object> path) {
        List<Setting<?>> fields = new ArrayList<>();
        fields.add(new ActionSetting("Apply changes", "Apply", session::commit));
        JsonElement node = session.at(path);
        if (node.isJsonObject()) {
            for (var entry : node.getAsJsonObject().entrySet())
                add(fields, title, session, append(path, entry.getKey()), readable(entry.getKey()), entry.getValue());
            StringSetting key = new StringSetting("New field name", session.newKey, 64);
            key.onChange(value -> session.newKey = value.getAsString());
            fields.add(key);
            fields.add(new ActionSetting("Add field", "Add", () -> {
                String name = session.newKey.trim();
                if (name.isEmpty() || node.getAsJsonObject().has(name)) throw new IllegalArgumentException("Choose a new field name");
                node.getAsJsonObject().addProperty(name, "");
                session.newKey = "";
            }));
        } else if (node.isJsonArray()) {
            JsonArray array = node.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement value = array.get(i);
                add(fields, title, session, append(path, i), entryName(value, i), value);
            }
            fields.add(new ActionSetting("Add entry", "Add", () -> {
                if (array.size() >= 4096) throw new IllegalArgumentException("Too many entries");
                array.add(array.isEmpty() ? new JsonObject() : blank(array.get(0)));
            }));
        } else add(fields, title, session, path, "Value", node);
        if (!path.isEmpty()) fields.add(new ActionSetting("Remove this entry", "Remove", () -> {
            session.remove(path);
            BytePhase.mc.currentScreen.close();
        }));
        if (fields.size() == 1) fields.add(new LabelSetting("Data", "No entries"));
        return fields;
    }

    private static void add(List<Setting<?>> fields, String title, Session session, List<Object> path,
                            String name, JsonElement value) {
        if (value.isJsonObject() || value.isJsonArray()) {
            String summary = value.isJsonArray() ? value.getAsJsonArray().size() + " entries" : "Open";
            fields.add(new ActionSetting(name, summary, () ->
                BytePhase.mc.setScreen(page(title + " / " + name, BytePhase.mc.currentScreen, session, path))));
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            BooleanSetting setting = new BooleanSetting(name, value.getAsBoolean());
            setting.onChange(json -> session.set(path, json));
            fields.add(setting);
        } else {
            boolean numeric = value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
            String initial = value.isJsonNull() ? "" : value.getAsString();
            StringSetting setting = new StringSetting(name, initial, numeric ? 64 : 8192);
            setting.onChange(json -> session.set(path, numeric
                ? new JsonPrimitive(new BigDecimal(json.getAsString().trim())) : json));
            fields.add(setting);
        }
    }

    private static List<Object> append(List<Object> path, Object key) {
        List<Object> result = new ArrayList<>(path); result.add(key); return List.copyOf(result);
    }

    public static String readable(String key) {
        return key.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').replace('-', ' ');
    }

    private static String entryName(JsonElement value, int index) {
        if (value.isJsonObject()) for (String key : List.of("name", "title", "item", "id", "station", "path")) {
            JsonElement label = value.getAsJsonObject().get(key);
            if (label != null && label.isJsonPrimitive() && !label.getAsString().isBlank()) return label.getAsString();
        }
        return "Entry " + (index + 1);
    }

    private static JsonElement blank(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            value.getAsJsonObject().entrySet().forEach(entry -> result.add(entry.getKey(), blank(entry.getValue())));
            return result;
        }
        if (value.isJsonArray()) return new JsonArray();
        if (value.isJsonNull()) return JsonNull.INSTANCE;
        if (value.getAsJsonPrimitive().isBoolean()) return new JsonPrimitive(false);
        if (value.getAsJsonPrimitive().isNumber()) return new JsonPrimitive(0);
        return new JsonPrimitive("");
    }

    private static final class Session {
        private final Supplier<JsonElement> read;
        private final Consumer<JsonElement> write;
        private JsonElement root;
        private String newKey = "";
        Session(Supplier<JsonElement> read, Consumer<JsonElement> write) {
            this.read = read; this.write = write;
            JsonElement value = read.get();
            root = value == null ? new JsonObject() : value.deepCopy();
        }
        JsonElement at(List<Object> path) {
            JsonElement value = root;
            for (Object key : path) value = key instanceof Integer index
                ? value.getAsJsonArray().get(index) : value.getAsJsonObject().get((String) key);
            return value;
        }
        void set(List<Object> path, JsonElement value) {
            if (path.isEmpty()) { root = value.deepCopy(); return; }
            Object key = path.getLast();
            JsonElement parent = at(path.subList(0, path.size() - 1));
            if (key instanceof Integer index) parent.getAsJsonArray().set(index, value.deepCopy());
            else parent.getAsJsonObject().add((String) key, value.deepCopy());
        }
        void remove(List<Object> path) {
            Object key = path.getLast();
            JsonElement parent = at(path.subList(0, path.size() - 1));
            if (key instanceof Integer index) parent.getAsJsonArray().remove(index);
            else parent.getAsJsonObject().remove((String) key);
        }
        void commit() {
            if (root.toString().length() > 1024 * 1024) throw new IllegalArgumentException("Data is too large");
            JsonElement previous = read.get();
            try { write.accept(root.deepCopy()); }
            catch (RuntimeException error) {
                if (previous != null) write.accept(previous.deepCopy());
                throw error;
            }
            if (BytePhase.config != null) BytePhase.config.requestSave();
        }
    }
}
