package dev.bytephase.module.misc;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class NameProtect extends Module {
    public NameProtect() {
        super("NameProtect", Category.MISC, "Hides your nickname in incoming chat");
    }

    public static Text filter(Text message) {
        var mc = BytePhase.mc;
        if (mc == null || BytePhase.modules == null) {
            return message;
        }
        NameProtect protect = BytePhase.modules.get(NameProtect.class);
        if (protect == null || !protect.isEnabled()) {
            return message;
        }
        String name = mc.getSession().getUsername();
        if (name == null || name.isBlank()) {
            return message;
        }
        String flat = message.getString();
        if (!flat.contains(name)) {
            return message;
        }
        List<StyledPart> parts = new ArrayList<>();
        message.visit((style, value) -> {
            if (!value.isEmpty()) {
                parts.add(new StyledPart(value, style));
            }
            return Optional.empty();
        }, Style.EMPTY);

        MutableText result = Text.empty();
        StringBuilder flattened = new StringBuilder();
        for (StyledPart part : parts) {
            flattened.append(part.value());
        }
        String content = flattened.toString();
        int cursor = 0;
        int match;
        while ((match = content.indexOf(name, cursor)) >= 0) {
            result = appendStyledRange(result, parts, cursor, match);
            result = result.append(Text.literal("You").setStyle(styleAt(parts, match)));
            cursor = match + name.length();
        }
        return appendStyledRange(result, parts, cursor, content.length());
    }

    private record StyledPart(String value, Style style) {
    }

    private static MutableText appendStyledRange(MutableText output, List<StyledPart> parts, int start, int end) {
        int offset = 0;
        for (StyledPart part : parts) {
            int partEnd = offset + part.value().length();
            int from = Math.max(start, offset);
            int to = Math.min(end, partEnd);
            if (from < to) {
                output = output.append(Text.literal(part.value().substring(from - offset, to - offset))
                        .setStyle(part.style()));
            }
            offset = partEnd;
            if (offset >= end) {
                break;
            }
        }
        return output;
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
