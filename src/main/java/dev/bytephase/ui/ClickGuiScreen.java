package dev.bytephase.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.bytephase.BytePhase;
import dev.bytephase.config.ProfileManager;
import dev.bytephase.input.Keybinds;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.module.combat.CombatIntegration;
import dev.bytephase.module.render.UiSettings;
import dev.bytephase.render.ClientFonts;
import dev.bytephase.render.Render2D;
import dev.bytephase.setting.ActionKeySetting;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.setting.Setting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/** Native version of the user's glass ClickGUI, backed by the live module catalog. */
public final class ClickGuiScreen extends Screen {
    private static final int W = 640, H = 440, SIDE = 144, BODY_X = 160, BODY_Y = 59;
    private static final int BODY_W = W - BODY_X - 16, BODY_BOTTOM = H - 24;
    private static final int MUTED = 0xFFA6A9D6, LINE = 0x24FFFFFF, WHITE = 0xFFF0F1FF;
    private static final Identifier LOGO = Identifier.of("bytephase", "clickgui-logo.png");
    private static final int ICON_TEXTURE_SIZE = 48;
    private static final String[] THEME_NAMES = {"Indigo", "Mint", "Ocean", "Violet", "Rose", "Amber", "Crimson"};
    private static final int[] THEME_COLORS = {0xFF6C6FF5, 0xFF3FB68B, 0xFF3B9BE8, 0xFF9B6CF0, 0xFFEE6D9B, 0xFFE8A23B, 0xFFE5534B};
    private static Tab savedTab = Tab.COMBAT;
    private static final Map<Tab, Double> SAVED_SCROLL = new EnumMap<>(Tab.class);

    private enum Tab {
        COMBAT("Combat", Category.COMBAT, "combat"), RENDER("Visuals", Category.RENDER, "visuals"),
        PLAYER("Player", Category.PLAYER, "player"), MOVEMENT("Movement", Category.MOVEMENT, "movement"),
        WORLD("World", Category.WORLD, "world"), EXPLORE("Explore", Category.EXPLORE, "explore"),
        MISC("Other", Category.MISC, "other"), CONFIGS("Configs", null, "configs"),
        FRIENDS("Friends", null, "friends"), THEMES("Themes", null, "themes"),
        SETTINGS("Settings", null, "settings");
        final String label, icon;
        final Category category;
        Tab(String label, Category category, String icon) { this.label = label; this.category = category; this.icon = icon; }
    }
    private enum Field { SEARCH, CONFIG_NAME, FRIEND_NAME, IMPORT_NAME, IMPORT_JSON }
    private enum Action { TAB, MODULE, CLOSE_DIALOG, ENABLE, BIND, ACTION_KEY, BOOL, NUMBER, MODE, COLOR,
        RESET, SAVE, LOAD, OVERWRITE, COPY, DELETE, IMPORT, FRIEND_ADD, FRIEND_REMOVE, THEME, FIELD }
    private record Hit(Action action, int x, int y, int w, int h, Object target, int data) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final UiSettings appearance;
    private final Map<Category, List<Module>> catalog = new EnumMap<>(Category.class);
    private final Map<Tab, Double> scrolls = new EnumMap<>(Tab.class);
    private final Map<Field, String> fields = new EnumMap<>(Field.class);
    private final List<Hit> hits = new ArrayList<>();
    private Tab tab = savedTab;
    private Field focused;
    private int caret;
    private boolean selectedAll;
    private float scale = 1, originX, originY;
    private double mouseX, mouseY;
    private int contentHeight;
    private Module dialog, binding;
    private ActionKeySetting actionBinding;
    private Hit dragging;
    private double dialogScroll;
    private int dialogX, dialogY, dialogH, dialogContentHeight;
    private List<String> profiles = List.of(), friends = List.of();
    private String loadedProfile = "", confirmDelete = "", confirmOverwrite = "";
    private String toast = "";
    private long toastUntil;
    private boolean profileStorage = true;
    private int keyboardCard = -1;

    public ClickGuiScreen() {
        super(Text.literal("Bytephase"));
        appearance = BytePhase.modules == null ? null : BytePhase.modules.get(UiSettings.class);
        if (BytePhase.modules != null) for (Category category : Category.values()) {
            catalog.put(category, BytePhase.modules.byCategory(category).stream()
                .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER)).toList());
        }
        scrolls.putAll(SAVED_SCROLL);
        for (Field field : Field.values()) fields.put(field, "");
    }

    public static JsonObject saveLayout() {
        JsonObject object = new JsonObject();
        object.addProperty("version", 2);
        object.addProperty("tab", savedTab.name());
        JsonObject scroll = new JsonObject();
        SAVED_SCROLL.forEach((key, value) -> scroll.addProperty(key.name(), value));
        object.add("scroll", scroll);
        return object;
    }

    public static void loadLayout(JsonElement value) {
        if (value == null || !value.isJsonObject()) return;
        JsonObject object = value.getAsJsonObject();
        try { if (object.has("tab")) savedTab = Tab.valueOf(object.get("tab").getAsString()); }
        catch (RuntimeException ignored) { }
        if (object.has("scroll") && object.get("scroll").isJsonObject()) {
            for (var entry : object.getAsJsonObject("scroll").entrySet()) try {
                double scroll = entry.getValue().getAsDouble();
                if (Double.isFinite(scroll)) SAVED_SCROLL.put(Tab.valueOf(entry.getKey()), MathHelper.clamp(scroll, 0, 100000));
            } catch (RuntimeException ignored) { }
        }
    }

    @Override protected void init() {
        scale = Math.max(0.05f, Math.min(1f, Math.min((width - 16f) / W, (height - 16f) / H)));
        originX = (width - W * scale) / 2;
        originY = (height - H * scale) / 2;
        if (client != null) {
            float pixels = (float) client.getWindow().getScaleFactor();
            originX = Math.round(originX * pixels) / pixels;
            originY = Math.round(originY * pixels) / pixels;
        }
        readProfiles();
        friends = CombatIntegration.friends();
        hits.clear();
    }

    @Override public void render(DrawContext context, int mx, int my, float delta) {
        mouseX = (mx - originX) / scale;
        mouseY = (my - originY) / scale;
        context.fill(0, 0, width, height, 0x28050520);
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(originX, originY);
        matrices.scale(scale, scale);
        try { ClientFonts.ui(() -> draw(context)); }
        finally { matrices.popMatrix(); }
    }

    private void draw(DrawContext ctx) {
        hits.clear();
        int alpha = Math.round((appearance == null ? 45 : appearance.panelOpacity()) * 2.55f);
        Render2D.shadow(ctx, 0, 0, W, H, 21, 5, 0x60050528);
        glass(ctx, 0, 0, W, H, 21, alpha << 24 | 0x1A1850, 0x38FFFFFF);
        ctx.enableScissor(1, 1, W - 1, H - 1);
        ctx.fillGradient(20, 1, W - 20, 45, 0x14FFFFFF, 0x00FFFFFF);
        ctx.fill(SIDE, 14, SIDE + 1, H - 14, LINE);
        drawSidebar(ctx);
        text(ctx, tab.label, BODY_X, 24, color(), true);
        field(ctx, Field.SEARCH, "Search modules", W - 159, 16, 137, 26);
        ctx.enableScissor(BODY_X, BODY_Y, W - 12, BODY_BOTTOM);
        if (tab.category != null || !value(Field.SEARCH).isEmpty()) drawModules(ctx);
        else switch (tab) {
            case CONFIGS -> drawConfigs(ctx);
            case FRIENDS -> drawFriends(ctx);
            case THEMES -> drawThemes(ctx);
            case SETTINGS -> drawSettings(ctx);
            default -> { }
        }
        ctx.disableScissor();
        drawScrollbar(ctx, W - 10, BODY_Y, BODY_BOTTOM - BODY_Y, scrolls.getOrDefault(tab, 0.0), contentHeight);
        ctx.disableScissor();
        if (dialog == null && (tab.category != null || !value(Field.SEARCH).isEmpty())) {
            text(ctx, "Left-click to toggle  /  Right-click to configure", BODY_X, H - 15, MUTED, false);
        }
        if (dialog != null) drawDialog(ctx);
        if (System.nanoTime() < toastUntil && !toast.isEmpty()) {
            String shown = trim(toast, W - 44);
            int tw = textRenderer.getWidth(shown) + 22;
            glass(ctx, (W - tw) / 2, H - 29, tw, 22, 7, 0xEF000000 | accent() & 0xFFFFFF, 0x40FFFFFF);
            text(ctx, shown, (W - tw) / 2 + 11, H - 22, WHITE, false);
        }
    }

    private void drawSidebar(DrawContext ctx) {
        image(ctx, LOGO, 53, 20, 38, 38, 256, 256, 0xFFFFFFFF);
        centered(ctx, "bytephase", SIDE / 2, 69, WHITE, true);
        int y = 98;
        for (Tab entry : Tab.values()) {
            if (entry == Tab.CONFIGS) { ctx.fill(16, y + 1, SIDE - 16, y + 2, LINE); y += 12; }
            boolean selected = tab == entry, hover = inside(mouseX, mouseY, 12, y, SIDE - 24, 23);
            if (selected) glass(ctx, 12, y, SIDE - 24, 23, 8, accent(), 0x60FFFFFF);
            else if (hover) rounded(ctx, 12, y, SIDE - 24, 23, 8, 0x14FFFFFF);
            icon(ctx, entry.icon, 23, y + 5, 13, selected ? WHITE : color());
            text(ctx, entry.label, 45, y + 7, selected ? WHITE : color(), selected);
            hit(Action.TAB, 12, y, SIDE - 24, 23, entry, 0);
            y += 25;
        }
        ctx.fill(16, H - 45, SIDE - 16, H - 44, LINE);
        glass(ctx, 16, H - 34, 25, 25, 7, 0xFF8A6FD1, 0x50FFFFFF);
        icon(ctx, "player", 22, H - 29, 14, WHITE);
        String player = BytePhase.mc.player == null ? "player" : BytePhase.mc.player.getName().getString();
        text(ctx, trim(player, 83), 49, H - 29, WHITE, true);
        text(ctx, BytePhase.version(), 49, H - 17, MUTED, false);
    }

    private List<Module> visibleModules() {
        String query = value(Field.SEARCH).toLowerCase(Locale.ROOT).replace(" ", "");
        if (query.isEmpty()) return catalog.getOrDefault(tab.category, List.of());
        return catalog.values().stream().flatMap(List::stream)
            .filter(module -> module.getName().toLowerCase(Locale.ROOT).contains(query))
            .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private void drawModules(DrawContext ctx) {
        List<Module> modules = visibleModules();
        int gap = 9, cardW = (BODY_W - gap) / 2, cardH = 35;
        contentHeight = ((modules.size() + 1) / 2) * (cardH + gap);
        double scroll = clampScroll();
        if (modules.isEmpty()) { empty(ctx, "No modules match your search.", BODY_Y + 12); return; }
        for (int i = 0; i < modules.size(); i++) {
            int x = BODY_X + i % 2 * (cardW + gap), y = BODY_Y + i / 2 * (cardH + gap) - (int) scroll;
            if (y + cardH <= BODY_Y || y >= BODY_BOTTOM) continue;
            Module module = modules.get(i);
            boolean hovered = inside(mouseX, mouseY, x, y, cardW, cardH) && dialog == null;
            boolean on = module.isEnabled();
            int border = on ? 0xAA000000 | accent() & 0xFFFFFF : LINE;
            if (hovered || keyboardCard == i) border = 0x70FFFFFF;
            glass(ctx, x, y, cardW, cardH, 11, hovered ? 0x1BFFFFFF : 0x10FFFFFF, border);
            icon(ctx, categoryIcon(module.getCategory()), x + 11, y + 11, 13, on ? WHITE : MUTED);
            String name = trim(module.getName(), cardW - 106);
            text(ctx, name, x + 32, y + 12, on ? WHITE : MUTED, false);
            String bind = Keybinds.getKeyName(module);
            if (!bind.equalsIgnoreCase("unknown") && !bind.equalsIgnoreCase("none") && !bind.equals("?")) {
                Render2D.scaledText(ctx, textRenderer, trim(bind, 35), x + cardW - 65, y + 13, 0.85f, MUTED);
            }
            toggle(ctx, x + cardW - 30, y + 12, on, true);
            hit(Action.MODULE, x, y, cardW, cardH, module, i);
        }
    }

    private void drawConfigs(DrawContext ctx) {
        contentHeight = 186 + Math.max(1, profiles.size()) * 40;
        int y = BODY_Y - (int) clampScroll();
        text(ctx, "Create config", BODY_X, y + 2, color(), true);
        field(ctx, Field.CONFIG_NAME, "Config name", BODY_X, y + 20, BODY_W - 102, 26);
        button(ctx, "Save current", Action.SAVE, BODY_X + BODY_W - 95, y + 20, 95, 26, null, 0, false);
        text(ctx, "Your configs", BODY_X, y + 63, color(), true);
        if (!profileStorage) empty(ctx, "Profile storage unavailable. Reopen Configs to retry.", y + 82);
        else if (profiles.isEmpty()) empty(ctx, "No configs yet. Save the current settings above.", y + 82);
        for (int i = 0; i < profiles.size(); i++) {
            String name = profiles.get(i);
            int cy = y + 82 + i * 40;
            if (cy + 35 <= BODY_Y || cy >= BODY_BOTTOM) continue;
            glass(ctx, BODY_X, cy, BODY_W, 34, 8, 0x10FFFFFF, name.equals(loadedProfile) ? accent() : LINE);
            text(ctx, trim(name, 140), BODY_X + 10, cy + 7, color(), true);
            text(ctx, name.equals(loadedProfile) ? "Loaded" : "Module settings and states", BODY_X + 10, cy + 20, MUTED, false);
            int bx = BODY_X + BODY_W - 231;
            button(ctx, "Load", Action.LOAD, bx, cy + 7, 38, 21, name, 0, false);
            button(ctx, "Overwrite", Action.OVERWRITE, bx + 43, cy + 7, 69, 21, name, 0, true);
            button(ctx, "Copy", Action.COPY, bx + 117, cy + 7, 41, 21, name, 0, true);
            button(ctx, name.equals(confirmDelete) ? "Confirm" : "Delete", Action.DELETE, bx + 163, cy + 7, 60, 21, name, 0, true);
        }
        int iy = y + 94 + Math.max(1, profiles.size()) * 40;
        text(ctx, "Import a copied Bytephase config", BODY_X, iy, color(), true);
        field(ctx, Field.IMPORT_JSON, "Paste config JSON (Ctrl+V)", BODY_X, iy + 17, BODY_W, 31);
        field(ctx, Field.IMPORT_NAME, "Name for imported config", BODY_X, iy + 55, BODY_W - 83, 26);
        button(ctx, "Import", Action.IMPORT, BODY_X + BODY_W - 75, iy + 55, 75, 26, null, 0, false);
        contentHeight = iy + 92 - y;
    }

    private void drawFriends(DrawContext ctx) {
        contentHeight = 52 + Math.max(1, friends.size()) * 40;
        int y = BODY_Y - (int) clampScroll();
        field(ctx, Field.FRIEND_NAME, "Add a friend by name", BODY_X, y, BODY_W - 69, 26);
        button(ctx, "Add", Action.FRIEND_ADD, BODY_X + BODY_W - 61, y, 61, 26, null, 0, false);
        if (friends.isEmpty()) empty(ctx, "No friends added yet.", y + 45);
        for (int i = 0; i < friends.size(); i++) {
            int cy = y + 44 + i * 40;
            if (cy + 34 <= BODY_Y || cy >= BODY_BOTTOM) continue;
            glass(ctx, BODY_X, cy, BODY_W, 34, 8, 0x10FFFFFF, LINE);
            icon(ctx, "player", BODY_X + 11, cy + 10, 14, MUTED);
            text(ctx, friends.get(i), BODY_X + 34, cy + 12, color(), false);
            button(ctx, "Remove", Action.FRIEND_REMOVE, BODY_X + BODY_W - 76, cy + 7, 66, 21, friends.get(i), 0, true);
        }
    }

    private void drawThemes(DrawContext ctx) {
        List<Setting<?>> settings = appearance == null ? List.of() : appearance.getSettings().stream()
            .filter(ColorSetting.class::isInstance).toList();
        contentHeight = 97 + settings.stream().filter(Setting::isVisible).mapToInt(ClickGuiScreen::settingHeight).sum();
        int y = BODY_Y - (int) clampScroll();
        text(ctx, "Accent color", BODY_X, y, color(), true);
        for (int i = 0; i < THEME_COLORS.length; i++) {
            int x = BODY_X + i * 61;
            glass(ctx, x, y + 24, 34, 34, 9, THEME_COLORS[i], accent() == THEME_COLORS[i] ? WHITE : 0x00FFFFFF);
            hit(Action.THEME, x, y + 24, 44, 47, null, i);
            Render2D.scaledText(ctx, textRenderer, THEME_NAMES[i], x, y + 65, 0.9f, MUTED);
        }
        int sy = y + 97;
        for (Setting<?> setting : settings) {
            if (!setting.isVisible()) continue;
            drawSetting(ctx, setting, BODY_X, sy, BODY_W);
            sy += settingHeight(setting);
        }
    }

    private void drawSettings(DrawContext ctx) {
        List<Setting<?>> settings = appearance == null ? List.of() : appearance.getSettings().stream()
            .filter(setting -> !(setting instanceof ColorSetting)).toList();
        contentHeight = 28 + settings.stream().filter(Setting::isVisible)
            .mapToInt(ClickGuiScreen::settingHeight).sum();
        int y = BODY_Y - (int) clampScroll();
        text(ctx, "Interface and HUD", BODY_X, y, color(), true);
        int sy = y + 28;
        for (Setting<?> setting : settings) {
            if (!setting.isVisible()) continue;
            drawSetting(ctx, setting, BODY_X, sy, BODY_W);
            sy += settingHeight(setting);
        }
    }

    private void drawDialog(DrawContext ctx) {
        hits.clear(); // Modal settings own input until closed.
        focused = null;
        ctx.fill(0, 0, W, H, 0x88050528);
        int settingsH = dialog.getSettings().stream().filter(Setting::isVisible).mapToInt(ClickGuiScreen::settingHeight).sum();
        dialogH = Math.min(390, Math.max(174, settingsH + 145));
        dialogX = (W - 310) / 2;
        dialogY = (H - dialogH) / 2;
        glass(ctx, dialogX, dialogY, 310, dialogH, 15, 0xEF2C2A65, 0x50FFFFFF);
        text(ctx, trim(dialog.getName(), 212), dialogX + 14, dialogY + 15, WHITE, true);
        button(ctx, "Close", Action.CLOSE_DIALOG, dialogX + 250, dialogY + 10, 46, 23, null, 0, true);
        int y = dialogY + 43;
        text(ctx, "Enabled", dialogX + 14, y + 6, color(), false);
        toggle(ctx, dialogX + 270, y + 3, dialog.isEnabled(), false);
        hit(Action.ENABLE, dialogX + 14, y, 282, 23, dialog, 0);
        text(ctx, "Keybind", dialogX + 14, y + 33, color(), false);
        String bind = binding == dialog ? "Press a key..." : Keybinds.getKeyName(dialog);
        button(ctx, trim(bind, 118), Action.BIND, dialogX + 164, y + 25, 132, 23, dialog, 0, true);
        int top = y + 59, bottom = dialogY + dialogH - 47;
        dialogContentHeight = settingsH;
        dialogScroll = MathHelper.clamp(dialogScroll, 0, Math.max(0, settingsH - (bottom - top)));
        ctx.enableScissor(dialogX + 12, top, dialogX + 299, bottom);
        int sy = top - (int) dialogScroll;
        for (Setting<?> setting : dialog.getSettings()) {
            if (!setting.isVisible()) continue;
            drawSetting(ctx, setting, dialogX + 14, sy, 282);
            sy += settingHeight(setting);
        }
        ctx.disableScissor();
        drawScrollbar(ctx, dialogX + 302, top, bottom - top, dialogScroll, settingsH);
        ctx.fill(dialogX + 14, dialogY + dialogH - 39, dialogX + 296, dialogY + dialogH - 38, LINE);
        button(ctx, "Reset module", Action.RESET, dialogX + 14, dialogY + dialogH - 31, 108, 23, dialog, 0, true);
        text(ctx, "Esc: close / Backspace: clear bind", dialogX + 134, dialogY + dialogH - 23, MUTED, false, 0.85f);
    }

    private void drawSetting(DrawContext ctx, Setting<?> setting, int x, int y, int w) {
        int h = settingHeight(setting);
        ctx.fill(x, y, x + w, y + 1, LINE);
        String label = setting.getName();
        text(ctx, trim(label, w / 2 - 9), x, y + 10, color(), false);
        if (setting instanceof BooleanSetting bool) {
            toggle(ctx, x + w - 26, y + 7, bool.getValue(), false);
            if (setting.getName().equals("StreamProof")) {
                String state = bool.getValue() ? dev.bytephase.render.capture.StreamProof.status()
                    : "Window capture - Ctrl+Shift+F9 toggles";
                Render2D.scaledText(ctx, textRenderer, trim(state, w - 30),
                    x, y + 23, 0.85f, MUTED);
            }
            hit(Action.BOOL, x, y, w, h, setting, 0);
        } else if (setting instanceof ModeSetting mode) {
            int bx = x + w / 2;
            glass(ctx, bx, y + 5, w / 2, 22, 6, 0x18FFFFFF, LINE);
            text(ctx, trim(mode.getValue(), w / 2 - 24), bx + 8, y + 12, color(), false);
            text(ctx, ">", x + w - 12, y + 12, MUTED, false);
            hit(Action.MODE, bx, y + 5, w / 2, 22, setting, 0);
        } else if (setting instanceof ActionKeySetting key) {
            String shown = actionBinding == key ? "Press key or mouse..." : actionKeyName(key.getValue());
            button(ctx, trim(shown, w / 2 - 12), Action.ACTION_KEY, x + w / 2, y + 5, w / 2, 22, key, 0, true);
        } else if (setting instanceof NumberSetting number) {
            int trackX = x + w / 2, trackW = w / 2 - 45;
            text(ctx, trim(formatNumber(number.getValue()), 40), x + w - 38, y + 11, MUTED, false);
            slider(ctx, trackX, y + 16, trackW,
                (number.getValue() - number.getMin()) / Math.max(1e-9, number.getMax() - number.getMin()), accent());
            hit(Action.NUMBER, trackX, y + 6, trackW, 23, setting, 0);
        } else if (setting instanceof ColorSetting color) {
            rounded(ctx, x + w - 24, y + 7, 22, 16, 5, 0xFF000000 | color.rgb());
            for (int i = 0; i < 3; i++) {
                int cy = y + 31 + i * 15;
                int channel = i == 0 ? color.red() : i == 1 ? color.green() : color.blue();
                text(ctx, new String[] {"R", "G", "B"}[i], x + 6, cy, MUTED, false);
                slider(ctx, x + 23, cy + 5, w - 55, channel / 255.0, new int[] {0xFFF17A9C, 0xFF6DD8B0, 0xFF8FA8FF}[i]);
                text(ctx, Integer.toString(channel), x + w - 26, cy, MUTED, false);
                hit(Action.COLOR, x + 23, cy - 2, w - 55, 13, color, i == 0 ? 16 : i == 1 ? 8 : 0);
            }
        }
    }

    private static int settingHeight(Setting<?> setting) { return setting instanceof ColorSetting ? 81 : 34; }
    private void slider(DrawContext ctx, int x, int y, int w, double fraction, int color) {
        int fill = (int) Math.round(w * MathHelper.clamp(fraction, 0, 1));
        rounded(ctx, x, y - 1, w, 3, 1, 0x50050528);
        if (fill > 0) rounded(ctx, x, y - 1, fill, 3, 1, color);
        rounded(ctx, x + fill - 3, y - 3, 6, 7, 3, WHITE);
    }
    private void toggle(DrawContext ctx, int x, int y, boolean on, boolean small) {
        int w = small ? 20 : 26, h = small ? 12 : 16;
        rounded(ctx, x, y, w, h, h / 2, on ? accent() : 0x990B110E);
        Render2D.roundedOutline(ctx, x, y, w, h, h / 2, LINE);
        rounded(ctx, x + (on ? w - h + 2 : 2), y + 2, h - 4, h - 4, (h - 4) / 2, WHITE);
    }
    private void drawScrollbar(DrawContext ctx, int x, int y, int h, double scroll, int content) {
        if (content <= h || h <= 0) return;
        rounded(ctx, x, y, 2, h, 1, 0x16FFFFFF);
        int thumb = Math.max(16, h * h / content);
        int top = y + (int) ((h - thumb) * MathHelper.clamp(scroll / Math.max(1, content - h), 0, 1));
        rounded(ctx, x, top, 2, thumb, 1, 0x80C9CCFF);
    }
    private double clampScroll() {
        double value = MathHelper.clamp(scrolls.getOrDefault(tab, 0.0), 0, Math.max(0, contentHeight - (BODY_BOTTOM - BODY_Y)));
        scrolls.put(tab, value);
        return value;
    }
    private void empty(DrawContext ctx, String message, int y) {
        glass(ctx, BODY_X, y, BODY_W, 48, 9, 0x06FFFFFF, LINE);
        centered(ctx, trim(message, BODY_W - 20), BODY_X + BODY_W / 2, y + 19, MUTED, false);
    }
    private void button(DrawContext ctx, String label, Action action, int x, int y, int w, int h, Object target, int data, boolean secondary) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        int bg = secondary ? hover ? 0x30FFFFFF : 0x18FFFFFF : accent();
        int fg = action == Action.DELETE || action == Action.RESET || action == Action.FRIEND_REMOVE ? 0xFFFF9AAB : WHITE;
        glass(ctx, x, y, w, h, 6, bg, 0x28FFFFFF);
        centered(ctx, trim(label, w - 10), x + w / 2, y + (h - 9) / 2, fg, false);
        hit(action, x, y, w, h, target, data);
    }
    private void field(DrawContext ctx, Field field, String placeholder, int x, int y, int w, int h) {
        boolean active = focused == field;
        glass(ctx, x, y, w, h, 7, 0x10FFFFFF, active ? accent() : 0x00FFFFFF);
        String value = value(field);
        String display = value.isEmpty() ? placeholder : value;
        if (field == Field.IMPORT_JSON && !value.isEmpty()) display = value.length() + " characters of config JSON";
        display = trim(display, w - 16);
        text(ctx, display, x + 8, y + (h - 9) / 2, value.isEmpty() ? MUTED : color(), false);
        if (active && System.nanoTime() / 500000000L % 2 == 0) {
            String prefix = field == Field.IMPORT_JSON ? display : value.substring(0, Math.min(caret, value.length()));
            int cw = Math.min(w - 12, textRenderer.getWidth(trim(prefix, w - 16)));
            ctx.fill(x + 8 + cw, y + 7, x + 9 + cw, y + h - 6, accent());
        }
        if (active && selectedAll && !value.isEmpty()) ctx.fill(x + 6, y + 4, x + w - 6, y + h - 4, 0x30FFFFFF);
        hit(Action.FIELD, x, y, w, h, field, 0);
    }
    private void icon(DrawContext ctx, String name, int x, int y, int size, int tint) {
        image(ctx, Identifier.of("bytephase", "ui/" + name + ".png"),
            x, y, size, size, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, tint);
    }
    private static String categoryIcon(Category category) {
        return switch (category) {
            case COMBAT -> "combat";
            case RENDER -> "visuals";
            case PLAYER -> "player";
            case MOVEMENT -> "movement";
            case WORLD -> "world";
            case EXPLORE -> "explore";
            case MISC -> "other";
            case UI -> "settings";
        };
    }
    private static void image(DrawContext ctx, Identifier texture, int x, int y,
            int width, int height, int textureWidth, int textureHeight, int tint) {
        // Destination size and sampled source region are independent.
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0,
            width, height, textureWidth, textureHeight, textureWidth, textureHeight, tint);
    }
    private static void glass(DrawContext ctx, int x, int y, int w, int h, int r, int bg, int border) {
        rounded(ctx, x, y, w, h, r, bg);
        if ((border >>> 24) != 0) Render2D.roundedOutline(ctx, x, y, w, h, r, border);
        ctx.fill(x + r, y + 1, x + w - r, y + 2, 0x22FFFFFF);
    }
    private static void rounded(DrawContext ctx, int x, int y, int w, int h, int r, int color) { Render2D.roundedRect(ctx, x, y, w, h, r, color); }
    private int accent() { return appearance == null ? 0xFF6C6FF5 : appearance.accent(); }
    private int color() { return appearance == null ? WHITE : appearance.text(); }
    private String trim(String value, int width) { return textRenderer.trimToWidth(value, Math.max(1, width)); }
    private void text(DrawContext ctx, String value, int x, int y, int color, boolean bold) {
        Text text = Text.literal(value).styled(style -> style.withBold(bold && (appearance == null || appearance.boldHeaders())));
        ctx.drawText(textRenderer, text, x, y, color, appearance != null && appearance.textShadow());
    }
    private void text(DrawContext ctx, String value, int x, int y, int color, boolean bold, float scale) {
        var matrix = ctx.getMatrices(); matrix.pushMatrix(); matrix.translate(x, y); matrix.scale(scale, scale);
        text(ctx, value, 0, 0, color, bold); matrix.popMatrix();
    }
    private void centered(DrawContext ctx, String value, int x, int y, int color, boolean bold) {
        Text text = Text.literal(value).styled(style -> style.withBold(bold && (appearance == null || appearance.boldHeaders())));
        ctx.drawText(textRenderer, text, x - textRenderer.getWidth(text) / 2, y, color, false);
    }
    private static String formatNumber(double number) {
        if (number == Math.rint(number)) return Long.toString((long) number);
        return java.math.BigDecimal.valueOf(number).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    private static String actionKeyName(int key) {
        if (key < 0) return "None";
        if (key >= 1000) return "Mouse " + (key - 999);
        return net.minecraft.client.util.InputUtil.fromKeyCode(new KeyInput(key, 0, 0)).getLocalizedText().getString();
    }
    private void hit(Action action, int x, int y, int w, int h, Object target, int data) {
        hits.add(new Hit(action, x, y, w, h, target, data));
    }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    @Override public boolean mouseClicked(Click click, boolean doubled) {
        double mx = (click.x() - originX) / scale, my = (click.y() - originY) / scale;
        int button = click.buttonInfo().button();
        if (actionBinding != null) { actionBinding.setValue(1000 + button); actionBinding = null; persist(); return true; }
        if (binding != null) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(mx, my)) continue;
            boolean headerHit = hit.action == Action.TAB || hit.action == Action.FIELD && hit.target == Field.SEARCH;
            if (dialog == null && !headerHit && (my < BODY_Y || my >= BODY_BOTTOM)) continue;
            if (dialog != null && isSetting(hit.action) && (my < dialogY + 102 || my >= dialogY + dialogH - 47)) continue;
            if (hit.action != Action.FIELD) focused = null;
            switch (hit.action) {
                case TAB -> { if (button == 0) selectTab((Tab) hit.target); }
                case FIELD -> { if (button == 0) focus((Field) hit.target); }
                case MODULE -> {
                    if (button == 0) ((Module) hit.target).toggle();
                    if (button == 1) { dialog = (Module) hit.target; dialogScroll = 0; dragging = null; }
                    keyboardCard = hit.data;
                }
                case CLOSE_DIALOG -> closeDialog();
                case ENABLE -> { if (button == 0) dialog.toggle(); }
                case BIND -> { if (button == 0) binding = dialog; else if (button == 1) { Keybinds.set(dialog, GLFW.GLFW_KEY_UNKNOWN); persist(); } }
                case ACTION_KEY -> { if (button == 0) actionBinding = (ActionKeySetting) hit.target; else if (button == 1) ((ActionKeySetting) hit.target).setValue(-1); }
                case BOOL -> { if (button == 0) ((BooleanSetting) hit.target).toggle(); }
                case MODE -> {
                    if (button == 0 || button == 1) { ModeSetting mode = (ModeSetting) hit.target; String[] modes = mode.getModes();
                        mode.setValue(modes[Math.floorMod(mode.getIndex() + (button == 1 ? -1 : 1), modes.length)]); }
                }
                case NUMBER, COLOR -> { if (button == 0) { dragging = hit; setSlider(hit, mx); } }
                case RESET -> { if (button == 0) resetModule(); }
                case THEME -> { if (button == 0 && appearance != null) { appearance.setAccent(THEME_COLORS[hit.data]); toast(THEME_NAMES[hit.data] + " theme"); } }
                default -> { if (button == 0) perform(hit); }
            }
            if (isSetting(hit.action) || hit.action == Action.THEME) {
                persist();
                hits.clear();
            }
            return true;
        }
        focused = null;
        if (dialog != null && !inside(mx, my, dialogX, dialogY, 310, dialogH)) closeDialog();
        return true;
    }
    private static boolean isSetting(Action action) { return action == Action.BOOL || action == Action.NUMBER || action == Action.MODE || action == Action.COLOR || action == Action.ACTION_KEY; }
    private void selectTab(Tab value) {
        tab = value; savedTab = value; focused = null; keyboardCard = -1; fields.put(Field.SEARCH, "");
        dragging = null; confirmDelete = ""; confirmOverwrite = ""; hits.clear();
        if (tab == Tab.CONFIGS) readProfiles();
        if (tab == Tab.FRIENDS) friends = CombatIntegration.friends();
        persist();
    }
    private void closeDialog() { dialog = null; binding = null; actionBinding = null; dragging = null; hits.clear(); }
    private void resetModule() {
        dialog.setEnabled(false);
        for (Setting<?> setting : dialog.getSettings()) setting.reset();
        Keybinds.set(dialog, GLFW.GLFW_KEY_UNKNOWN);
        dialogScroll = 0; actionBinding = null; binding = null; persist(); toast("Reset " + dialog.getName());
    }
    private void setSlider(Hit hit, double mx) {
        double fraction = MathHelper.clamp((mx - hit.x) / Math.max(1, hit.w), 0, 1);
        if (hit.target instanceof NumberSetting number) {
            double raw = number.getMin() + fraction * (number.getMax() - number.getMin());
            double snapped = number.getMin() + Math.round((raw - number.getMin()) / number.getStep()) * number.getStep();
            number.setValue(MathHelper.clamp(snapped, number.getMin(), number.getMax()));
        } else if (hit.target instanceof ColorSetting color) color.setChannel(hit.data, (int) Math.round(fraction * 255));
    }
    @Override public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging != null) { setSlider(dragging, (click.x() - originX) / scale); return true; }
        return true;
    }
    @Override public boolean mouseReleased(Click click) { if (dragging != null) persist(); dragging = null; return true; }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        double x = (mx - originX) / scale, y = (my - originY) / scale;
        dragging = null;
        if (dialog != null) {
            if (inside(x, y, dialogX, dialogY, 310, dialogH)) dialogScroll = Math.max(0, dialogScroll - vertical * 27);
        } else if (inside(x, y, BODY_X, BODY_Y, BODY_W, BODY_BOTTOM - BODY_Y)) {
            scrolls.put(tab, Math.max(0, scrolls.getOrDefault(tab, 0.0) - vertical * 29));
        }
        hits.clear(); return true;
    }

    private void perform(Hit hit) {
        String name = hit.target instanceof String string ? string : value(Field.CONFIG_NAME).trim();
        try {
            switch (hit.action) {
                case SAVE -> {
                    if (!name.matches("[A-Za-z0-9_-]{1,32}")) { toast("Use 1-32 letters, numbers, underscores or dashes."); return; }
                    if (profiles.contains(name) && !name.equals(confirmOverwrite)) { confirmOverwrite = name; toast("Click Save current again to overwrite " + name); return; }
                    BytePhase.profiles.save(name); confirmOverwrite = ""; toast("Saved " + name); readProfiles();
                }
                case LOAD -> { BytePhase.profiles.load(name); loadedProfile = name; toast("Loaded " + name); }
                case OVERWRITE -> { BytePhase.profiles.save(name); toast("Saved " + name); }
                case COPY -> { client.keyboard.setClipboard(BytePhase.profiles.exportProfile(name)); toast("Copied " + name + " to clipboard"); }
                case DELETE -> {
                    if (!name.equals(confirmDelete)) { confirmDelete = name; toast("Click Confirm to delete " + name); return; }
                    BytePhase.profiles.delete(name); confirmDelete = ""; if (loadedProfile.equals(name)) loadedProfile = ""; readProfiles(); toast("Deleted " + name);
                }
                case IMPORT -> {
                    String imported = value(Field.IMPORT_NAME).trim();
                    if (!imported.matches("[A-Za-z0-9_-]{1,32}")) { toast("Enter a valid name for the imported config."); return; }
                    if (profiles.contains(imported) && !imported.equals(confirmOverwrite)) { confirmOverwrite = imported; toast("Click Import again to overwrite " + imported); return; }
                    BytePhase.profiles.importProfile(imported, value(Field.IMPORT_JSON)); confirmOverwrite = ""; readProfiles(); toast("Imported " + imported);
                }
                case FRIEND_ADD -> {
                    String friend = value(Field.FRIEND_NAME).trim();
                    if (!CombatIntegration.addFriend(friend)) { toast("Enter a valid player name that is not already a friend."); return; }
                    fields.put(Field.FRIEND_NAME, ""); friends = CombatIntegration.friends(); toast("Added " + friend);
                }
                case FRIEND_REMOVE -> { CombatIntegration.removeFriend(name); friends = CombatIntegration.friends(); toast("Removed " + name); }
                default -> { }
            }
        } catch (ProfileManager.LoadException error) { toast(error.getMessage()); }
        catch (Exception error) {
            BytePhase.LOGGER.warn("ClickGUI action {} failed", hit.action, error);
            toast(hit.action == Action.IMPORT ? "Invalid Bytephase config or unavailable profile storage." : "Could not complete that config action. Check profile storage.");
        }
        hits.clear();
    }
    private void readProfiles() {
        try { profiles = BytePhase.profiles == null ? List.of() : BytePhase.profiles.list(); profileStorage = true; }
        catch (Exception error) { profiles = List.of(); profileStorage = false; }
    }
    private void toast(String value) { toast = value; toastUntil = System.nanoTime() + 3000000000L; }
    private static void persist() { if (BytePhase.config != null) BytePhase.config.requestSave(); }
    private String value(Field field) { return fields.getOrDefault(field, ""); }
    private void focus(Field field) { focused = field; caret = value(field).length(); selectedAll = false; }
    private void edit(String insert) {
        if (focused == null) return;
        String old = value(focused);
        int max = focused == Field.IMPORT_JSON ? 1024 * 1024 : focused == Field.SEARCH ? 64 : 32;
        if (selectedAll) { old = ""; caret = 0; selectedAll = false; }
        insert = insert.replace("\r", "").replace("\n", focused == Field.IMPORT_JSON ? "\n" : "");
        int remaining = Math.max(0, max - old.length());
        if (insert.length() > remaining) insert = insert.substring(0, remaining);
        fields.put(focused, old.substring(0, caret) + insert + old.substring(caret));
        caret += insert.length();
        afterEdit();
    }
    private void afterEdit() {
        if (focused == Field.SEARCH) { scrolls.put(tab, 0.0); keyboardCard = -1; }
        confirmDelete = ""; confirmOverwrite = ""; hits.clear();
    }

    @Override public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (actionBinding != null) { actionBinding.setValue(key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE ? -1 : key); actionBinding = null; persist(); return true; }
        if (binding != null) { Keybinds.set(binding, key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE ? GLFW.GLFW_KEY_UNKNOWN : key); binding = null; persist(); return true; }
        if (Keybinds.matchesClickGui(input)) { Keybinds.suppressNextClickGui(); close(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (dialog != null) closeDialog(); else if (focused != null) focused = null; else close();
            return true;
        }
        boolean control = (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (control && key == GLFW.GLFW_KEY_F && dialog == null) { focus(Field.SEARCH); return true; }
        if (focused != null) {
            String old = value(focused);
            if (key == GLFW.GLFW_KEY_TAB) {
                List<Field> order = tab == Tab.CONFIGS ? List.of(Field.SEARCH, Field.CONFIG_NAME, Field.IMPORT_JSON, Field.IMPORT_NAME)
                    : tab == Tab.FRIENDS ? List.of(Field.SEARCH, Field.FRIEND_NAME) : List.of(Field.SEARCH);
                int direction = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
                focus(order.get(Math.floorMod(order.indexOf(focused) + direction, order.size())));
            }
            else if (control && key == GLFW.GLFW_KEY_A) selectedAll = true;
            else if (control && key == GLFW.GLFW_KEY_V) edit(client.keyboard.getClipboard());
            else if (control && key == GLFW.GLFW_KEY_C) client.keyboard.setClipboard(old);
            else if (control && key == GLFW.GLFW_KEY_X) { client.keyboard.setClipboard(old); fields.put(focused, ""); caret = 0; selectedAll = false; afterEdit(); }
            else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                if (selectedAll || control) { fields.put(focused, ""); caret = 0; }
                else if (key == GLFW.GLFW_KEY_BACKSPACE && caret > 0) {
                    int start = old.offsetByCodePoints(caret, -1); fields.put(focused, old.substring(0, start) + old.substring(caret)); caret = start;
                } else if (key == GLFW.GLFW_KEY_DELETE && caret < old.length()) {
                    int end = old.offsetByCodePoints(caret, 1); fields.put(focused, old.substring(0, caret) + old.substring(end));
                }
                selectedAll = false; afterEdit();
            } else if (key == GLFW.GLFW_KEY_LEFT && caret > 0) { caret = old.offsetByCodePoints(caret, -1); selectedAll = false; }
            else if (key == GLFW.GLFW_KEY_RIGHT && caret < old.length()) { caret = old.offsetByCodePoints(caret, 1); selectedAll = false; }
            else if (key == GLFW.GLFW_KEY_HOME) caret = 0;
            else if (key == GLFW.GLFW_KEY_END) caret = old.length();
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (focused == Field.FRIEND_NAME) perform(new Hit(Action.FRIEND_ADD, 0, 0, 0, 0, null, 0));
                else if (focused == Field.CONFIG_NAME) perform(new Hit(Action.SAVE, 0, 0, 0, 0, null, 0));
                else focused = null;
            }
            return true;
        }
        if (dialog == null && (tab.category != null || !value(Field.SEARCH).isEmpty())) {
            List<Module> list = visibleModules();
            if (!list.isEmpty()) {
                int step = key == GLFW.GLFW_KEY_DOWN ? 2 : key == GLFW.GLFW_KEY_UP ? -2 : key == GLFW.GLFW_KEY_RIGHT ? 1 : key == GLFW.GLFW_KEY_LEFT ? -1 : 0;
                if (step != 0) { keyboardCard = MathHelper.clamp(keyboardCard < 0 ? 0 : keyboardCard + step, 0, list.size() - 1); scrolls.put(tab, Math.max(0, keyboardCard / 2 * 44.0 - 88)); return true; }
                if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) && keyboardCard >= 0 && keyboardCard < list.size()) {
                    if ((input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0) { dialog = list.get(keyboardCard); dialogScroll = 0; }
                    else list.get(keyboardCard).toggle();
                    return true;
                }
            }
        }
        if (key == GLFW.GLFW_KEY_TAB && dialog == null) { focus(Field.SEARCH); return true; }
        return true;
    }
    @Override public boolean charTyped(CharInput input) { if (focused != null && input.isValidChar()) edit(input.asString()); return true; }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        if (appearance == null || appearance.backgroundBlur()) applyBlur(ctx);
    }
    @Override public void removed() {
        savedTab = tab; SAVED_SCROLL.clear(); SAVED_SCROLL.putAll(scrolls);
        if (BytePhase.config != null) BytePhase.config.save();
        super.removed();
    }
}
