package dev.bytephase.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Editable text, including commands, player names and feature-specific paths. */
public final class StringSetting extends Setting<String> {
    private final int maxLength;

    public StringSetting(String name, String value) { this(name, value, 2048); }

    public StringSetting(String name, String value, int maxLength) {
        super(name, value == null ? "" : value);
        this.maxLength = maxLength;
    }

    public int maxLength() { return maxLength; }

    @Override public void setValue(String value) {
        if (value == null) return;
        super.setValue(value.substring(0, Math.min(value.length(), maxLength)));
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(getValue()); }

    @Override public void fromJson(JsonElement value) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            setValue(value.getAsString());
        }
    }
}
