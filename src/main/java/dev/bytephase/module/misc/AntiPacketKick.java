package dev.bytephase.module.misc;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;

/**
 * Keeps oversized or malformed packets from disconnecting the client.
 *
 * <p>This is a client-side convenience, not a bypass: the offending packet is
 * dropped, so whatever it carried is lost.</p>
 */
public final class AntiPacketKick extends Module {
    private final BooleanSetting catchExceptions = add(new BooleanSetting("CatchExceptions", true));
    private final BooleanSetting logExceptions = add(new BooleanSetting("LogExceptions", false));

    public AntiPacketKick() {
        super("AntiPacketKick", Category.MISC, "Ignores corrupted packets instead of getting kicked");
    }

    public boolean catchExceptions() {
        return isEnabled() && catchExceptions.getValue();
    }

    public boolean logExceptions() {
        return isEnabled() && logExceptions.getValue();
    }
}
