package dev.bytephase.setting;

import com.google.gson.JsonElement;

public abstract class Setting<T> {
    private final String name;
    private final T defaultValue;
    private volatile T value;
    private java.util.function.Consumer<JsonElement> changeListener;
    private java.util.function.BooleanSupplier visibility;

    protected Setting(String name, T value) {
        this.name = name;
        this.defaultValue = value;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public T getValue() {
        return value;
    }

    public void reset() { setValue(defaultValue); }

    public void setValue(T value) {
        if (java.util.Objects.equals(this.value, value)) return;
        this.value = value;
        if (changeListener != null) changeListener.accept(toJson());
    }

    public void onChange(java.util.function.Consumer<JsonElement> listener) { changeListener = listener; }
    public void visibleWhen(java.util.function.BooleanSupplier condition) { visibility = condition; }
    public boolean isVisible() { return visibility == null || visibility.getAsBoolean(); }

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement element);
}
