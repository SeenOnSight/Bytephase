package dev.bytephase.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** Structured data managed by a feature's item, schematic or rule editor. */
public final class StructuredSetting extends Setting<JsonElement> {
    private final java.util.function.Supplier<JsonElement> read;
    private final java.util.function.Consumer<JsonElement> write;

    public StructuredSetting(String name, java.util.function.Supplier<JsonElement> read,
                             java.util.function.Consumer<JsonElement> write) {
        super(name, copy(read.get()));
        this.read = read;
        this.write = write;
        visibleWhen(() -> false);
    }

    private static JsonElement copy(JsonElement value) { return value == null ? JsonNull.INSTANCE : value.deepCopy(); }
    @Override public JsonElement toJson() { return copy(read.get()); }
    @Override public void fromJson(JsonElement value) {
        if (value != null && value.toString().length() <= 1024 * 1024) write.accept(copy(value));
    }
    @Override public void setValue(JsonElement value) { if (value != null) write.accept(copy(value)); }
}
