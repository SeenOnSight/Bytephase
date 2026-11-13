package dev.bytephase.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.UiSettings;
import java.util.Optional;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Font changes are scoped to Bytephase drawing and measurement on the client thread. */
public final class ClientFonts {
    public static final String[] OPTIONS = {"Inter", "Outfit", "JetBrains Mono", "Minecraft"};
    private record Family(StyleSpriteSource.Font regular, StyleSpriteSource.Font semibold) { }
    private static final Family INTER = family("inter");
    private static final Family OUTFIT = family("outfit");
    private static final Family MONO = family("jetbrains_mono");
    private static Family current;

    private ClientFonts() { }

    private static StyleSpriteSource.Font font(String name) {
        return new StyleSpriteSource.Font(Identifier.of("bytephase", name));
    }

    private static Family family(String name) {
        return new Family(font(name), font(name + "_semibold"));
    }

    public static boolean bundledFont(Identifier file) {
        if (!file.getNamespace().equals("bytephase")) return false;
        return switch (file.getPath()) {
            case "inter.ttf", "inter_semibold.ttf", "outfit.ttf", "outfit_semibold.ttf",
                 "jetbrains_mono.ttf", "jetbrains_mono_semibold.ttf" -> true;
            default -> false;
        };
    }

    /** Atlas labels identify the font after GUI drawing has left its font scope. */
    public static boolean smoothAtlas(String label) {
        if (label == null) return false;
        return label.startsWith("bytephase:inter/") || label.startsWith("bytephase:inter_semibold/")
            || label.startsWith("bytephase:outfit/") || label.startsWith("bytephase:outfit_semibold/")
            || label.startsWith("bytephase:jetbrains_mono/") || label.startsWith("bytephase:jetbrains_mono_semibold/");
    }

    public static String hudName() {
        UiSettings ui = BytePhase.modules == null ? null : BytePhase.modules.get(UiSettings.class);
        return ui == null ? "Inter" : ui.hudFont();
    }

    public static void hud(Runnable draw) { scoped(hudName(), draw); }

    public static void ui(Runnable draw) {
        UiSettings ui = BytePhase.modules == null ? null : BytePhase.modules.get(UiSettings.class);
        scoped(ui == null ? "Inter" : ui.uiFont(), draw);
    }

    private static void scoped(String name, Runnable draw) {
        Family previous = current;
        current = switch (name) {
            case "Outfit" -> OUTFIT;
            case "JetBrains Mono" -> MONO;
            case "Minecraft" -> null;
            default -> INTER;
        };
        try { draw.run(); }
        finally { current = previous; }
    }

    public static boolean active() { return current != null; }
    public static Style style() { return active() ? Style.EMPTY.withFont(current.regular()) : Style.EMPTY; }
    public static Text text(String value) { return Text.literal(value).setStyle(style()); }
    public static Text text(Text value) {
        return active() && value != null ? value.copy().setStyle(value.getStyle().withFont(current.regular())) : value;
    }
    public static StringVisitable visitable(StringVisitable value) {
        if (!active() || value == null) return value;
        Family selected = current;
        return new StringVisitable() {
            @Override public <T> Optional<T> visit(Visitor<T> visitor) {
                return value.visit(visitor);
            }
            @Override public <T> Optional<T> visit(StyledVisitor<T> visitor, Style inherited) {
                return value.visit((style, text) -> visitor.accept(resolve(style, selected), text), inherited);
            }
        };
    }
    public static OrderedText ordered(OrderedText value) {
        if (!active() || value == null) return value;
        Family selected = current;
        return visitor -> value.accept((index, style, codePoint) ->
            visitor.accept(index, resolve(style, selected), codePoint));
    }

    private static Style resolve(Style style, Family selected) {
        // A real semibold face supplies both glyph shapes and advance widths.
        // Keep this idempotent when a measured or drawn value passes two hooks.
        boolean semibold = style.isBold() || selected.semibold().equals(style.getFont());
        return style.withFont(semibold ? selected.semibold() : selected.regular()).withBold(false);
    }
}
