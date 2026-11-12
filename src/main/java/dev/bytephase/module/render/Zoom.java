package dev.bytephase.module.render;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

/**
 * Smooth zoom.
 *
 * <p>The field of view is eased
 * toward the target in log space so the change feels linear at any zoom level,
 * and the easing is time based instead of frame based.</p>
 */
public final class Zoom extends Module {
    private static final float MAX_FRAME_SECONDS = 0.1f;

    private final NumberSetting fov = add(new NumberSetting("Fov", 20.0, 5.0, 70.0, 1.0));
    private final NumberSetting smoothness = add(new NumberSetting("Smoothness", 60.0, 0.0, 100.0, 5.0));

    private float current;
    private float start;
    private long lastNanos;
    private boolean active;

    public Zoom() {
        super("Zoom", Category.RENDER, "Smoothly zooms your camera while enabled");
    }

    @Override
    protected void onDisable() {
        active = false;
        lastNanos = 0L;
    }

    /**
     * @param vanilla the field of view vanilla would use this frame
     * @return the zoomed field of view, eased from {@code vanilla}
     */
    public float apply(float vanilla) {
        float target = fov.asFloat();
        long now = System.nanoTime();
        if (!active) {
            active = true;
            current = vanilla;
            start = vanilla;
            lastNanos = now;
        }
        float seconds = lastNanos == 0L ? 0.0f
                : Math.min(MAX_FRAME_SECONDS, (now - lastNanos) / 1.0e9f);
        lastNanos = now;

        double smooth = smoothness.getValue() / 100.0;
        if (smooth <= 0.0) {
            current = target;
            return current;
        }
        float rate = 30.0f - 25.0f * (float) smooth;
        float blend = 1.0f - (float) Math.exp(-seconds * rate);
        double from = Math.log(Math.max(1.0e-3, current));
        double to = Math.log(Math.max(1.0e-3, target));
        current = (float) Math.exp(from + (to - from) * blend);
        if (Math.abs(current - target) < 0.05f) {
            current = target;
        }
        return current;
    }

    /** Instant value used by tests and by the GUI readout. */
    public float getFov() {
        return active ? current : start;
    }
}
