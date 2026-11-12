package dev.bytephase.module.render;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

/**
 * Raises the player-list cap.
 *
 * <p>Vanilla sorts the listed players and then keeps only the first 80 before
 * laying them out in columns, so on a large server you simply cannot see the
 * rest. The list itself is complete; only the cut-off is configurable here.</p>
 */
public final class ExtraTab extends Module {
    private final NumberSetting limit = add(new NumberSetting("Limit", 200.0, 80.0, 1000.0, 10.0));

    public ExtraTab() {
        super("ExtraTab", Category.RENDER, "Shows more players in the tab list");
    }

    public long limit() {
        return limit.asInt();
    }
}
