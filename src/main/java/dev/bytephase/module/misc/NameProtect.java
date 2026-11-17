package dev.bytephase.module.misc;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.StringSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.text.CharacterVisitor;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextVisitFactory;
import net.minecraft.util.Formatting;

public final class NameProtect extends Module {
    private final StringSetting alias = add(new StringSetting("Alias", "You", 64));
    private static volatile Mask cachedMask;
    private Mask chatMask;

    public NameProtect() {
        super("NameProtect", Category.MISC, "Hides your username everywhere the client draws text");
    }

    public static boolean active() {
        return mask() != null;
    }

    public static String alias() {
        Mask mask = mask();
        return mask == null ? "You" : mask.alias();
    }

    private static Mask mask() {
        var mc = BytePhase.mc;
        if (mc == null || BytePhase.modules == null) {
            return null;
        }
        NameProtect protect = BytePhase.modules.get(NameProtect.class);
        // Privacy also applies to StreamProof's captured frame.
        if (protect == null || !protect.isConfiguredEnabled() || mc.getSession() == null) {
            return null;
        }
        String name = mc.getSession().getUsername();
        String profile = mc.player == null || mc.player.getGameProfile() == null
                ? null : mc.player.getGameProfile().name();
        name = name == null || name.isBlank() ? null : name;
        profile = profile == null || profile.isBlank() ? null : profile;
        if (name == null && profile == null) {
            return null;
        }
        String configuredAlias = protect.alias.getValue();
        Mask previous = cachedMask;
        if (previous != null && java.util.Objects.equals(previous.name(), name)
                && java.util.Objects.equals(previous.profile(), profile)
                && previous.configuredAlias().equals(configuredAlias)) {
            return previous;
        }
        String alias = Formatting.strip(configuredAlias).strip();
        if (alias.isEmpty()) alias = "You";
        // Repeated measurement/drawing hooks must never mask their own result.
        if (contains(alias, name) || contains(alias, profile)) {
            alias = contains("You", name) || contains("You", profile) ? "..." : "You";
        }
        Mask next = new Mask(name, profile, alias, configuredAlias);
        cachedMask = next;
        return next;
    }

    @Override protected void onEnable() { refreshChat(); }
    @Override protected void onDisable() { refreshChat(); }
    @Override public void onSettingsLoaded() { refreshChat(); }

    @Override public void onTickAlways() {
        if (!java.util.Objects.equals(chatMask, mask())) refreshChat();
    }

    private void refreshChat() {
        var mc = BytePhase.mc;
        if (mc == null) return;
        if (!mc.isOnThread()) {
            mc.execute(this::refreshChat);
            return;
        }
        chatMask = mask();
        if (mc.inGameHud != null) mc.inGameHud.getChatHud().reset();
    }

    /** Filters a display copy; account identity and outgoing text stay intact. */
    public static String filter(String value) {
        Mask mask = mask();
        if (mask == null || value == null || value.isEmpty()) return value;
        String visible = value.indexOf('\u00a7') < 0 ? value : Formatting.strip(value);
        Match match = mask.find(visible, 0);
        if (match == null) return value;

        // Map visible characters back to the original string when colour codes
        // occur inside a name. Keep the final formatting for the following text.
        int[] offsets = null;
        if (!visible.equals(value)) {
            offsets = new int[visible.length()];
            for (int raw = 0, index = 0; raw < value.length(); raw++) {
                if (isFormatting(value, raw)) raw++;
                else offsets[index++] = raw;
            }
        }
        StringBuilder result = new StringBuilder(value.length());
        int cursor = 0;
        do {
            int start = offsets == null ? match.start() : offsets[match.start()];
            int end = offsets == null ? match.end() : offsets[match.end() - 1] + 1;
            result.append(value, cursor, start).append(mask.alias());
            if (offsets != null) {
                for (int raw = start; raw < end; raw++) {
                    if (isFormatting(value, raw)) {
                        result.append(value, raw, raw + 2);
                        raw++;
                    }
                }
            }
            cursor = end;
            match = mask.find(visible, match.end());
        } while (match != null);
        return result.append(value, cursor, value.length()).toString();
    }

    private static boolean isFormatting(String value, int index) {
        return value.charAt(index) == '\u00a7' && index + 1 < value.length()
                && "0123456789abcdefklmnor".indexOf(Character.toLowerCase(value.charAt(index + 1))) >= 0;
    }

    public static Text filter(Text message) {
        return (Text) filter((StringVisitable) message);
    }

    public static StringVisitable filter(StringVisitable message) {
        Mask mask = mask();
        if (mask == null || message == null) return message;
        String content = message.getString();
        String visible = content.indexOf('\u00a7') < 0 ? content : Formatting.strip(content);
        if (mask.find(visible, 0) == null) {
            return message;
        }
        StyledCollector collector = new StyledCollector();
        TextVisitFactory.visitFormatted(message, Style.EMPTY, collector);
        List<StyledPart> parts = replace(collector.parts(), mask);
        MutableText result = Text.empty();
        for (StyledPart part : parts) {
            result.append(Text.literal(part.value()).setStyle(part.style()));
        }
        return result;
    }

    public static OrderedText filter(OrderedText message) {
        Mask mask = mask();
        if (mask == null || message == null) return message;
        if (message instanceof ProtectedOrderedText protectedText && protectedText.mask().equals(mask)) {
            return message;
        }
        StyledCollector collector = new StyledCollector();
        message.accept(collector);
        List<StyledPart> parts = collector.parts();
        List<StyledPart> filtered = replace(parts, mask);
        if (filtered == parts) return message;
        List<OrderedText> ordered = new ArrayList<>(filtered.size());
        for (StyledPart part : filtered) {
            // Preserve the existing visual order, including surrounding RTL text.
            ordered.add(OrderedText.styledForwardsVisitedString(part.value(), part.style()));
        }
        return new ProtectedOrderedText(OrderedText.concat(ordered), mask);
    }

    private static List<StyledPart> replace(List<StyledPart> parts, Mask mask) {
        StringBuilder flattened = new StringBuilder();
        for (StyledPart part : parts) {
            flattened.append(part.value());
        }
        String content = flattened.toString();
        Match match = mask.find(content, 0);
        if (match == null) return parts;
        List<StyledPart> result = new ArrayList<>();
        int cursor = 0;
        do {
            appendStyledRange(result, parts, cursor, match.start());
            result.add(new StyledPart(mask.alias(), styleAt(parts, match.start())));
            cursor = match.end();
            match = mask.find(content, cursor);
        } while (match != null);
        appendStyledRange(result, parts, cursor, content.length());
        return result;
    }

    private record StyledPart(String value, Style style) {
    }

    private record Match(int start, int end) { }

    private record Mask(String name, String profile, String alias, String configuredAlias) {
        Match find(String value, int from) {
            for (int i = from; i < value.length(); i++) {
                int length = 0;
                if (name != null && value.regionMatches(true, i, name, 0, name.length())) length = name.length();
                if (profile != null && profile.length() > length
                        && value.regionMatches(true, i, profile, 0, profile.length())) length = profile.length();
                if (length != 0) return new Match(i, i + length);
            }
            return null;
        }
    }

    private static boolean contains(String value, String name) {
        if (name == null) return false;
        for (int i = 0; i <= value.length() - name.length(); i++) {
            if (value.regionMatches(true, i, name, 0, name.length())) return true;
        }
        return false;
    }

    private record ProtectedOrderedText(OrderedText delegate, Mask mask) implements OrderedText {
        @Override public boolean accept(CharacterVisitor visitor) { return delegate.accept(visitor); }
    }

    private static final class StyledCollector implements CharacterVisitor {
        private final List<StyledPart> parts = new ArrayList<>();
        private final StringBuilder value = new StringBuilder();
        private Style style;

        @Override public boolean accept(int index, Style nextStyle, int codePoint) {
            if (!nextStyle.equals(style)) {
                flush();
                style = nextStyle;
            }
            value.appendCodePoint(codePoint);
            return true;
        }

        private void flush() {
            if (!value.isEmpty()) {
                parts.add(new StyledPart(value.toString(), style));
                value.setLength(0);
            }
        }

        List<StyledPart> parts() {
            flush();
            return parts;
        }
    }

    private static void appendStyledRange(List<StyledPart> output, List<StyledPart> parts, int start, int end) {
        int offset = 0;
        for (StyledPart part : parts) {
            int partEnd = offset + part.value().length();
            int from = Math.max(start, offset);
            int to = Math.min(end, partEnd);
            if (from < to) {
                output.add(new StyledPart(part.value().substring(from - offset, to - offset), part.style()));
            }
            offset = partEnd;
            if (offset >= end) {
                break;
            }
        }
    }

    private static Style styleAt(List<StyledPart> parts, int position) {
        int offset = 0;
        for (StyledPart part : parts) {
            int end = offset + part.value().length();
            if (position < end) {
                return part.style();
            }
            offset = end;
        }
        return Style.EMPTY;
    }
}
