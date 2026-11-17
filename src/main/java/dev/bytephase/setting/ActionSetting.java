package dev.bytephase.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A button whose action is never executed by loading a config or a profile. */
public final class ActionSetting extends Setting<Boolean> {
    private final String label;
    private final Runnable action;

    public ActionSetting(String name, String label, Runnable action) {
        super(name, false);
        this.label = label;
        this.action = action;
    }

    public String label() { return label; }
    public void run() { action.run(); }
    @Override public void setValue(Boolean value) { }
    @Override public JsonElement toJson() { return new JsonPrimitive(false); }
    @Override public void fromJson(JsonElement value) { }
}
