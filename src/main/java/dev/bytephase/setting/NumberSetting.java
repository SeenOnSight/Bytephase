package dev.bytephase.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String name, double value, double min, double max, double step) {
        super(name, clamp(value, min, max));
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max
                || !Double.isFinite(step) || step <= 0.0) {
            throw new IllegalArgumentException("Invalid numeric setting bounds for " + name);
        }
        this.min = min;
        this.max = max;
        this.step = step;
    }

    @Override
    public void setValue(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return;
        }
        super.setValue(clamp(value, min, max));
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value) || !Double.isFinite(min) || !Double.isFinite(max) || min > max) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    public int asInt() {
        return (int) Math.round(getValue());
    }

    public float asFloat() {
        return (float) (double) getValue();
    }

    public void increment() {
        setValue(Math.min(max, getValue() + step));
    }

    public void decrement() {
        setValue(Math.max(min, getValue() - step));
    }

    public boolean isFloat() {
        return step < 1.0;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            double value = element.getAsDouble();
            if (Double.isFinite(value)) {
                setValue(value);
            }
        }
    }
}
