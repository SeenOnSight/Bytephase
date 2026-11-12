package dev.bytephase.setting;

import com.google.gson.JsonElement;

public abstract class Setting<T> {
    private final String name;
    private volatile T value;

    protected Setting(String name, T value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement element);
}
