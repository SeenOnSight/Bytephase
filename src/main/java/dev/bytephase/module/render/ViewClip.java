package dev.bytephase.module.render;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;

/**
 * Controls the third-person camera's collision clip and its distance.
 *
 * <p>Vanilla asks the camera for a desired distance and then shrinks it until
 * the camera is no longer inside a block. {@code IgnoreBlocks} drops that second
 * step so the camera can pass through walls, and {@code Distance} caps the
 * result so the camera stays closer than vanilla's default. Both are applied to
 * the same value, which is why the resolution lives in one testable function.</p>
 */
public final class ViewClip extends Module {
    private final BooleanSetting ignoreBlocks = add(new BooleanSetting("IgnoreBlocks", false));
    private final NumberSetting distance = add(new NumberSetting("Distance", 4.0, 1.0, 24.0, 0.5));

    public ViewClip() {
        super("ViewClip", Category.RENDER, "Adjusts the third-person camera clip and distance");
    }

    /**
     * @param desired the distance vanilla asked for
     * @param clipped the distance vanilla could actually use without clipping
     */
    public float resolve(float desired, float clipped) {
        float result = ignoreBlocks.getValue() ? desired : clipped;
        return Math.max(0.0f, Math.min(result, distance.asFloat()));
    }
}
