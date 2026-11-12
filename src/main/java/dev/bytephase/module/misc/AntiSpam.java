package dev.bytephase.module.misc;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

/** Drops a chat line that repeats an identical one seen moments ago. */
public final class AntiSpam extends Module {
    private static final int PRUNE_AT = 512;

    private final NumberSetting window = add(new NumberSetting("Window", 10.0, 1.0, 120.0, 1.0));
    private final Map<String, Long> seen = new HashMap<>();

    public AntiSpam() {
        super("AntiSpam", Category.MISC, "Hides repeated identical chat messages");
    }

    @Override
    protected void onDisable() {
        seen.clear();
    }

    /** Whether the message repeats a recent one. Records it either way. */
    public static boolean blocks(Text message) {
        if (BytePhase.modules == null) {
            return false;
        }
        AntiSpam spam = BytePhase.modules.get(AntiSpam.class);
        if (spam == null || !spam.isEnabled()) {
            return false;
        }
        String key = message.getString();
        if (key.isBlank() || key.startsWith("[BytePhase]")) {
            return false;
        }
        long now = System.nanoTime();
        long windowNanos = (long) (spam.window.getValue() * 1_000_000_000L);
        Long previous = spam.seen.put(key, now);
        if (spam.seen.size() > PRUNE_AT) {
            spam.seen.values().removeIf(time -> now - time > windowNanos);
        }
        return previous != null && now - previous < windowNanos;
    }
}
