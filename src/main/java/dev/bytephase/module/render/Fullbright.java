package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

public final class Fullbright extends Module {
    // Vanilla's gamma option only accepts 0.0-1.0. Larger values are rejected
    // with an "Illegal option value" error on every setValue call, so the old
    // 15.0 brightness did nothing except spam the log once per tick.
    private final NumberSetting brightness = add(new NumberSetting("Brightness", 1.0, 0.5, 1.0, 0.05));
    private double previous = -1.0;
    private double applied = -1.0;

    public Fullbright() {
        super("Fullbright", Category.RENDER, "Maximizes ambient brightness");
    }

    @Override
    public void onEnable() {
        var mc = BytePhase.mc;
        if (mc == null || mc.options == null) {
            return;
        }
        if (previous < 0.0) {
            previous = mc.options.getGamma().getValue();
        }
    }

    @Override
    public void onDisable() {
        var mc = BytePhase.mc;
        if (mc != null && mc.options != null && previous >= 0.0) {
            mc.options.getGamma().setValue(previous);
            previous = -1.0;
            applied = -1.0;
        }
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.options == null) {
            return;
        }
        double target = brightness.getValue();
        double current = mc.options.getGamma().getValue();
        if (Math.abs(target - applied) < 1.0e-4 && Math.abs(target - current) < 1.0e-4) {
            return;
        }
        mc.options.getGamma().setValue(target);
        applied = target;
    }
}
