package dev.bytephase.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class ColorSetting extends Setting<Integer> {
    private static final int[] PALETTE = {
            0xFFFFFF, 0xFF5555, 0x55FF55, 0x55FFFF, 0xFFFF55, 0xFF55FF, 0x7FD6FF
    };

    public ColorSetting(String name, int argb) {
        super(name, argb);
    }

    public void cycle() {
        int index = paletteIndex();
        int next = index < 0 ? 0 : (index + 1) % PALETTE.length;
        setValue((getValue() & 0xFF000000) | PALETTE[next]);
    }

    public void cycleBack() {
        int index = paletteIndex();
        int previous = index < 0 ? PALETTE.length - 1
                : (index + PALETTE.length - 1) % PALETTE.length;
        setValue((getValue() & 0xFF000000) | PALETTE[previous]);
    }

    private int paletteIndex() {
        int rgb = getValue() & 0xFFFFFF;
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i] == rgb) {
                return i;
            }
        }
        return -1;
    }

    public int rgb() {
        return getValue() & 0xFFFFFF;
    }

    public int red() {
        return (getValue() >> 16) & 0xFF;
    }

    public int green() {
        return (getValue() >> 8) & 0xFF;
    }

    public int blue() {
        return getValue() & 0xFF;
    }

    public void setRed(int value) {
        setChannel(16, value);
    }

    public void setGreen(int value) {
        setChannel(8, value);
    }

    public void setBlue(int value) {
        setChannel(0, value);
    }

    public void setChannel(int shift, int value) {
        int channel = Math.max(0, Math.min(255, value));
        int current = getValue();
        setValue((current & ~(0xFF << shift)) | (channel << shift));
    }

    public int alpha() {
        return (getValue() >>> 24) & 0xFF;
    }

    public float redF() {
        return red() / 255.0f;
    }

    public float greenF() {
        return green() / 255.0f;
    }

    public float blueF() {
        return blue() / 255.0f;
    }

    public float alphaF() {
        return alpha() / 255.0f;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element.isJsonPrimitive()) {
            setValue(element.getAsInt());
        }
    }
}
