package dev.bytephase.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class ModeSetting extends Setting<String> {
    private final String[] modes;

    public ModeSetting(String name, String[] modes, String value) {
        super(name, value);
        if (modes == null || modes.length == 0) {
            throw new IllegalArgumentException("Mode setting must have at least one option: " + name);
        }
        this.modes = modes.clone();
        if (!contains(value)) {
            throw new IllegalArgumentException("Invalid default mode for " + name + ": " + value);
        }
    }

    @Override
    public void setValue(String value) {
        if (contains(value)) {
            super.setValue(value);
        }
    }

    private boolean contains(String value) {
        if (value == null) {
            return false;
        }
        for (String mode : modes) {
            if (mode.equals(value)) {
                return true;
            }
        }
        return false;
    }

    public String[] getModes() {
        return modes.clone();
    }

    public int getIndex() {
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(getValue())) {
                return i;
            }
        }
        return 0;
    }

    public void cycle() {
        setValue(modes[(getIndex() + 1) % modes.length]);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element.isJsonPrimitive()) {
            String s = element.getAsString();
            for (String mode : modes) {
                if (mode.equals(s)) {
                    setValue(s);
                    return;
                }
            }
        }
    }
}
