package dev.bytephase.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** Read-only information in a native editor. */
public final class LabelSetting extends Setting<String> {
    public LabelSetting(String name, String value) { super(name, value == null ? "" : value); }
    @Override public void setValue(String value) { }
    @Override public JsonElement toJson() { return JsonNull.INSTANCE; }
    @Override public void fromJson(JsonElement value) { }
}
