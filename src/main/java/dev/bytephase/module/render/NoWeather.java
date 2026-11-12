package dev.bytephase.module.render;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;

/**
 * Clears the weather, client-side only.
 *
 * <p>Vanilla drives rain particles, weather fog, sky darkening and lightning
 * flashes from the world's rain and thunder gradients, so zeroing those
 * gradients removes all of it without touching the server's weather state or
 * desyncing anything. The level itself still reports rain, which is what
 * servers use for mechanics like cauldrons filling.</p>
 */
public final class NoWeather extends Module {
    private final BooleanSetting rain = add(new BooleanSetting("Rain", true));
    private final BooleanSetting thunder = add(new BooleanSetting("Thunder", true));

    public NoWeather() {
        super("NoWeather", Category.RENDER, "Hides rain and thunder on the client");
    }

    public boolean hidesRain() {
        return isEnabled() && rain.getValue();
    }

    public boolean hidesThunder() {
        return isEnabled() && thunder.getValue();
    }
}
