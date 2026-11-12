package dev.bytephase.module.misc;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Hides incoming chat lines that match the enabled filters. Cyrillic filtering
 * is useful on busy public servers where a lot of chat is in a script you
 * cannot read.
 */
public final class ChatFilter extends Module {
    private final BooleanSetting cyrillic = add(new BooleanSetting("Cyrillic", true));
    private final BooleanSetting links = add(new BooleanSetting("Links", false));
    private final BooleanSetting joinLeave = add(new BooleanSetting("JoinLeave", false));

    public ChatFilter() {
        super("ChatFilter", Category.MISC, "Hides chat messages that match the selected filters");
    }

    /** Whether the message should be dropped. Called for every line added to chat. */
    public static boolean blocks(Text message) {
        if (BytePhase.modules == null) {
            return false;
        }
        ChatFilter filter = BytePhase.modules.get(ChatFilter.class);
        if (filter == null || !filter.isEnabled()) {
            return false;
        }
        String flat = message.getString();
        // Never hide the client's own feedback.
        if (flat.isEmpty() || flat.startsWith("[BytePhase]")) {
            return false;
        }
        if (filter.cyrillic.getValue() && containsCyrillic(flat)) {
            return true;
        }
        if (filter.links.getValue() || filter.joinLeave.getValue()) {
            String lower = flat.toLowerCase(Locale.ROOT);
            if (filter.links.getValue() && (lower.contains("http://") || lower.contains("https://")
                    || lower.contains("discord.gg") || lower.contains("www."))) {
                return true;
            }
            return filter.joinLeave.getValue()
                    && (lower.endsWith(" joined the game") || lower.endsWith(" left the game"));
        }
        return false;
    }

    private static boolean containsCyrillic(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u0400' && c <= '\u04FF') {
                return true;
            }
        }
        return false;
    }
}
