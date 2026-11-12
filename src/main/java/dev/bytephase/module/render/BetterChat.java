package dev.bytephase.module.render;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;

/**
 * Raises the two hard limits of the vanilla chat: how much history is kept and
 * how long a single message may be.
 *
 * <p>Vanilla drops the oldest line once 100 messages are stored, so scrolling
 * back loses information that the server already sent, and it refuses input
 * past 256 characters. Both are pure client-side limits; nothing extra is sent
 * to the server beyond what you actually type.</p>
 */
public final class BetterChat extends Module {
    private final NumberSetting history = add(new NumberSetting("History", 300.0, 100.0, 1000.0, 50.0));
    private final NumberSetting messageLength = add(new NumberSetting("MessageLength", 512.0, 256.0, 1024.0, 64.0));

    public BetterChat() {
        super("BetterChat", Category.RENDER, "Keeps more chat history and allows longer messages");
    }

    public int historyLimit() {
        return history.asInt();
    }

    public int messageLengthLimit() {
        return messageLength.asInt();
    }
}
