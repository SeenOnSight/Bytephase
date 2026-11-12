package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.sky.ClaritySkyRenderer;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;

/**
 * Replaces the vanilla sky with one of the supplied Clarity sky shader
 * presets. The shader files are unmodified; only the uniform source and
 * render timing are provided by BytePhase.
 */
public final class SkyShaders extends Module {
    private final ModeSetting preset = add(new ModeSetting("Preset", ClaritySkyRenderer.PRESETS, "Aurora"));
    private final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.05, 3.0, 0.05));
    private final NumberSetting brightness = add(new NumberSetting("Brightness", 1.0, 0.1, 3.0, 0.05));
    private final NumberSetting intensity = add(new NumberSetting("Intensity", 1.0, 0.0, 3.0, 0.05));
    private final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.25, 3.0, 0.05));
    private final NumberSetting stars = add(new NumberSetting("Stars", 1.0, 0.0, 2.0, 0.05));
    private final NumberSetting celestialSpeed = add(new NumberSetting("CelestialSpeed", 0.3, 0.0, 2.0, 0.05));
    private final NumberSetting horizonFog = add(new NumberSetting("HorizonFog", 0.5, 0.0, 1.0, 0.05));
    private final NumberSetting quality = add(new NumberSetting("Quality", 0.75, 0.25, 1.0, 0.05));
    private final ColorSetting primary = add(new ColorSetting("Primary", 0xFF7AA2F7));
    private final ColorSetting secondary = add(new ColorSetting("Secondary", 0xFF3D59A1));
    private final BooleanSetting allDimensions = add(new BooleanSetting("AllDimensions", false));

    public SkyShaders() {
        super("SkyShaders", Category.RENDER, "Replaces the sky with a supplied Clarity shader preset");
    }

    public String preset() {
        return preset.getValue();
    }

    public float speed() {
        return speed.asFloat();
    }

    public float brightness() {
        return brightness.asFloat();
    }

    public float intensity() {
        return intensity.asFloat();
    }

    public float scale() {
        return scale.asFloat();
    }

    public float stars() {
        return stars.asFloat();
    }

    public float celestialSpeed() {
        return celestialSpeed.asFloat();
    }

    public float horizonFog() {
        return horizonFog.asFloat();
    }

    public float quality() {
        return quality.asFloat();
    }

    public ColorSetting primary() {
        return primary;
    }

    public ColorSetting secondary() {
        return secondary;
    }

    public boolean allDimensions() {
        return allDimensions.getValue();
    }

    public void render() {
        if (BytePhase.mc == null || BytePhase.mc.world == null) {
            return;
        }
        ClaritySkyRenderer.render(this);
    }
}
