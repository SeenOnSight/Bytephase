package dev.bytephase.module.movement;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;

/**
 * Removes movement penalties. Each flag is read from a mixin, so the accessors
 * all check {@link #isEnabled()} as well as the setting.
 */
public final class NoSlow extends Module {
    private final BooleanSetting items = add(new BooleanSetting("Items", true));
    private final BooleanSetting cobweb = add(new BooleanSetting("Cobweb", true));
    private final BooleanSetting berryBush = add(new BooleanSetting("BerryBush", true));
    private final BooleanSetting slimeBlock = add(new BooleanSetting("SlimeBlock", true));
    private final BooleanSetting soulSand = add(new BooleanSetting("SoulSand", true));
    private final BooleanSetting sneak = add(new BooleanSetting("Sneak", true));

    public NoSlow() {
        super("NoSlow", Category.MOVEMENT, "Removes item, cobweb, berry, slime and soul sand slowdowns");
    }

    public boolean items() {
        return isEnabled() && items.getValue();
    }

    public boolean cobweb() {
        return isEnabled() && cobweb.getValue();
    }

    public boolean berryBush() {
        return isEnabled() && berryBush.getValue();
    }

    public boolean slimeBlock() {
        return isEnabled() && slimeBlock.getValue();
    }

    public boolean soulSand() {
        return isEnabled() && soulSand.getValue();
    }

    public boolean sneak() {
        return isEnabled() && sneak.getValue();
    }
}
