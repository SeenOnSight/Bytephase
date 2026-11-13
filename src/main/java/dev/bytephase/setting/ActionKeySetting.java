package dev.bytephase.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A keyboard or mouse bind for an action inside a module. */
public final class ActionKeySetting extends Setting<Integer> {
    public ActionKeySetting(String name, int key) { super(name, key); }

    @Override
    public void setValue(Integer key) {
        if (key != null && (key == -1 || key >= 32 && key <= 348 || key >= 1000 && key <= 1007)) super.setValue(key);
    }

    @Override
    public JsonElement toJson() { return new JsonPrimitive(getValue()); }

    @Override
    public void fromJson(JsonElement value) {
        if (value != null && value.isJsonPrimitive()) setValue(value.getAsInt());
    }
}
