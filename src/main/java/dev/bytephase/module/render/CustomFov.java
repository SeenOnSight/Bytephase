package dev.bytephase.module.render;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

/**
 * Replaces the vanilla field of view.
 *
 * <p>Ported from Anubis' {@code CustomFovModule}; Zoom keeps priority when both
 * modules are active because it is applied first in {@code GameRendererMixin}.</p>
 */
public final class CustomFov extends Module {
    private final NumberSetting fov = add(new NumberSetting("Fov", 110.0, 30.0, 160.0, 1.0));

    public CustomFov() {
        super("CustomFov", Category.RENDER, "Changes your field of view");
    }

    public float getFov() {
        return fov.asFloat();
    }
}
