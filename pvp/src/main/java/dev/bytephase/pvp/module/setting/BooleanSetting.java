package dev.bytephase.pvp.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class BooleanSetting extends Setting {
    private boolean value;

    public BooleanSetting(String name, boolean value) {
        super(name);
        this.value = value;
    }

    public boolean get() {
        return this.value;
    }

    public void set(boolean newValue) {
        if (this.value != newValue) {
            this.value = newValue;
            this.notifyChanged();
        }
    }

    public boolean value() {
        return this.value;
    }

    @Override
    public JsonElement serialize() {
        return new JsonPrimitive(this.value);
    }

    @Override
    protected void deserializeValue(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            try {
                this.value = element.getAsBoolean();
            } catch (RuntimeException ignored) {
            }
        }
    }
}
