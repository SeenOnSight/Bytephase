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
import dev.bytephase.render.ui.LiquidBackdrop;
import dev.bytephase.render.ui.LiquidGui;
import dev.bytephase.render.ui.UiBackgroundImage;
import dev.bytephase.setting.ActionKeySetting;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.setting.Setting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/** Native version of the user's glass ClickGUI, backed by the live module catalog. */
public final class ClickGuiScreen extends Screen {
    private static final int W = 1000, H = 690, SIDE = 224, BODY_X = 248, BODY_Y = 75;
    private static final int BODY_W = W - BODY_X - 24, BODY_BOTTOM = H - 58, DIALOG_W = 360;
    private static final int PANEL_RADIUS = 32, DIALOG_RADIUS = 22, CARD_RADIUS = 18;
    private static final int ROW_RADIUS = 14, NAV_RADIUS = 12, CONTROL_RADIUS = 10;
    private static final int SETTING_ROW_HEIGHT = 42, CONTROL_HEIGHT = 32;
    private static final int MUTED = 0xFFA6A6A6, LINE = 0x24FFFFFF, WHITE = 0xFFF0F0F0;
    private static final Identifier LOGO = Identifier.of("bytephase", "clickgui-logo.png");
    private static final Identifier LOGO_BASE = Identifier.of("bytephase", "clickgui-logo-base.png");
    private static final int ICON_TEXTURE_SIZE = 48;
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
        RESET, SAVE, LOAD, OVERWRITE, COPY, DELETE, IMPORT, FRIEND_ADD, FRIEND_REMOVE, FIELD, TEXT_SETTING, RUN_ACTION, EDITOR_BACK, PREVIEW }
    private record Hit(Action action, float x, float y, float w, float h, Object target, int data, LiquidGui.Rect clip) {
        Hit(Action action, float x, float y, float w, float h, Object target, int data) {
            this(action, x, y, w, h, target, data, null);
        }
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h && (clip == null || clip.contains(mx, my));
        }
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
    private float baseScale = 1, animationStep = 1, openProgress = 1;
    private long lastFrame;
    private double navScroll;
    private final Map<String, Float> animations = new HashMap<>();
    private String motionKey = "";
    private Module animatedDialog;
    private DrawContext drawingContext;
    private TextureSetup backdrop = TextureSetup.empty();
    private java.util.function.Supplier<List<Setting<?>>> editorFactory;
    private List<Setting<?>> editorSettings = List.of();
    private String editorTitle;
    private Screen editorParent;
    private double editorScroll;
    private float frameSeconds;
    private long editorRefresh;
    private Tab cachedModuleTab;
    private String cachedQuery = "";
    private List<Module> cachedModules = List.of();
    private final Map<String, Text> labels = new HashMap<>();
    private final Map<String, Integer> widths = new HashMap<>();
    private String measuredFont = "";
    private boolean measuredBold;
    private final Map<dev.bytephase.render.ui.NativePreviewScene, PreviewFrame> previewFrames = new java.util.IdentityHashMap<>();
    private record PreviewFrame(com.mojang.blaze3d.textures.GpuTextureView view, long time, int w, int h) { }
    private Module editorOwner;
    private final java.util.Set<dev.bytephase.render.ui.NativePreviewScene> failedPreviews =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
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
    private Hit lastHovered;

    public ClickGuiScreen() {
        super(Text.literal("Bytephase"));
        appearance = BytePhase.modules == null ? null : BytePhase.modules.get(UiSettings.class);
        if (BytePhase.modules != null) for (Category category : Category.values()) {
            catalog.put(category, BytePhase.modules.byCategory(category).stream()
                .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER)).toList());
        }
        scrolls.putAll(SAVED_SCROLL);
        for (Field field : Field.values()) fields.put(field, "");
        animations.put("screen-open", 0f);
    }

    public static ClickGuiScreen forModule(Module module) {
        ClickGuiScreen screen = new ClickGuiScreen();
        screen.dialog = module;
        for (Tab entry : Tab.values()) if (entry.category == module.getCategory()) screen.tab = entry;
        return screen;
    }

    public static ClickGuiScreen editor(String title, Screen parent,
                                        java.util.function.Supplier<List<Setting<?>>> factory) {
        ClickGuiScreen screen = new ClickGuiScreen();
        screen.editorTitle = title;
        screen.editorParent = parent;
        screen.editorFactory = factory;
        return screen;
    }

    public static ClickGuiScreen page(String name) {
        ClickGuiScreen screen = new ClickGuiScreen();
        screen.tab = Tab.valueOf(name);
        return screen;
    }

    public Module editedModule() { return dialog == null ? editorOwner : dialog; }
    public ClickGuiScreen owner(Module owner) { editorOwner = owner; return this; }

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
        baseScale = Math.max(0.05f, Math.min(1f, Math.min(width * 0.96f / W, height * 0.92f / H)));
        scale = baseScale;
        originX = (width - W * scale) / 2;
        originY = (height - H * scale) / 2;
        if (client != null) {
            float pixels = (float) client.getWindow().getScaleFactor();
            originX = Math.round(originX * pixels) / pixels;
            originY = Math.round(originY * pixels) / pixels;
        }
        readProfiles();
        friends = CombatIntegration.friends();
        if (editorFactory != null) editorSettings = List.copyOf(editorFactory.get());
        int selectedBottom = 132 + tab.ordinal() * 44 + (tab.ordinal() >= Tab.CONFIGS.ordinal() ? 20 : 0) + 38;
        navScroll = Math.max(navScroll, selectedBottom - (H - 95));
        hits.clear();
    }

    @Override public void render(DrawContext context, int mx, int my, float delta) {
        String font = appearance == null ? "Inter" : appearance.uiFont();
        boolean bold = appearance == null || appearance.boldHeaders();
        if (!font.equals(measuredFont) || bold != measuredBold) {
            measuredFont = font; measuredBold = bold; labels.clear(); widths.clear();
        }
        long now = System.nanoTime();
        float seconds = lastFrame == 0 ? 1 / 60f : Math.min(0.25f, (now - lastFrame) / 1.0e9f);
        lastFrame = now;
        boolean animate = !client.isPaused() && client.getCurrentFps() >= 20 && seconds <= 0.05f;
        frameSeconds = animate ? seconds : 0;
        animationStep = animate ? 1f - (float) Math.exp(-seconds / 0.045f) : 1;
        openProgress = motion("screen-open", 1);
        scale = baseScale * (0.98f + 0.02f * openProgress);
        float pixels = LiquidGui.pixelScale();
        originX = Math.round((width - W * scale) * 0.5f * pixels) / pixels;
        originY = Math.round((height - H * scale) * 0.5f * pixels) / pixels;
        mouseX = (mx - originX) / scale;
        mouseY = (my - originY) / scale;
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(originX, originY);
        matrices.scale(scale, scale);
        try { ClientFonts.ui(() -> draw(context)); }
        finally { matrices.popMatrix(); }
        Hit hovered = null;
        for (int index = hits.size() - 1; index >= 0; index--) {
            Hit hit = hits.get(index);
            if (hit.contains(mouseX, mouseY)) { hovered = hit; break; }
        }
        if (hovered != null && (lastHovered == null || hovered.action != lastHovered.action
                || !Objects.equals(hovered.target, lastHovered.target) || hovered.data != lastHovered.data)) {
            dev.bytephase.engine.categories.settings.ClientSounds.updateScrollHover();
        }
        lastHovered = hovered;
    }

    private void draw(DrawContext ctx) {
        hits.clear();
        drawingContext = ctx;
        backdrop = LiquidBackdrop.prepare(appearance == null || appearance.backgroundBlur());
        int alpha = Math.round((appearance == null ? 45 : appearance.panelOpacity()) * 2.55f);
        LiquidGui.opacity(openProgress);
        LiquidGui.shadow(ctx, 0, 18, W, H, PANEL_RADIUS, 45, 0x60080808);
        UiBackgroundImage.panel(ctx, 0, 0, W, H, PANEL_RADIUS, alpha, 0x38FFFFFF, backdrop, appearance);
        ctx.enableScissor(16, 16, W - 16, H - 16);
        surface(ctx, SIDE, 22, 1, H - 44, 0, LINE, 0, 0);
        drawSidebar(ctx);
        rowText(ctx, ellipsize(editorFactory == null ? tab.label : editorTitle,
            editorFactory == null ? 500 : 380, 1.5f, true), BODY_X, 22, 35, color(), true, 1.5f);
        if (editorFactory == null) field(ctx, Field.SEARCH, "Search modules", W - 224, 22, 200, 35);
        else {
            field(ctx, Field.SEARCH, "Search settings", W - 334, 22, 200, 35);
            button(ctx, "Back", Action.EDITOR_BACK, W - 122, 22, 98, 35, null, 0, true);
        }
        ctx.enableScissor(BODY_X, BODY_Y, W - 24, BODY_BOTTOM);
        if (editorFactory != null) drawEditor(ctx);
        else if (tab.category != null || !value(Field.SEARCH).isEmpty()) drawModules(ctx);
        else switch (tab) {
            case CONFIGS -> drawConfigs(ctx);
            case FRIENDS -> drawFriends(ctx);
            case THEMES -> drawThemes(ctx);
            case SETTINGS -> drawSettings(ctx);
            default -> { }
        }
        if (contentHeight > BODY_BOTTOM - BODY_Y) {
            int tint = 0x80000000 | (appearance == null ? 0x0B0B0B : appearance.panelColor() & 0xFFFFFF);
            surface(ctx, BODY_X, BODY_Y, BODY_W, 12, 0, tint, 0, LiquidGui.GRADIENT);
            surface(ctx, BODY_X, BODY_BOTTOM - 12, BODY_W, 12, 0, tint, 0, LiquidGui.GRADIENT | 32);
        }
        ctx.disableScissor();
        drawScrollbar(ctx, W - 15, BODY_Y, BODY_BOTTOM - BODY_Y, displayedScroll(), contentHeight);
        if (editorFactory == null && dialog == null && (tab.category != null || !value(Field.SEARCH).isEmpty())) {
            rowText(ctx, "Left-click to toggle / Right-click to configure", BODY_X, H - 34, 22, MUTED, false, 1.2f);
        }
        ctx.disableScissor();
        if (dialog != null) drawDialog(ctx);
        if (System.nanoTime() < toastUntil && !toast.isEmpty()) {
            String shown = ellipsize(toast, W - 64, 1.3f, false);
            int tw = (int) Math.ceil(labelWidth(shown, 1.3f, false)) + 32;
            glass(ctx, (W - tw) / 2, H - 46, tw, 32, CONTROL_RADIUS, 0xF0000000 | accent() & 0xFFFFFF, 0x40FFFFFF);
            rowText(ctx, shown, (W - tw) / 2 + 16, H - 46, 32, WHITE, false, 1.3f);
        }
        LiquidGui.opacity(1);
    }

    private void drawSidebar(DrawContext ctx) {
        image(ctx, LOGO_BASE, (SIDE - 54) / 2, 22, 54, 54, 256, 256, accent());
        image(ctx, LOGO, (SIDE - 54) / 2, 22, 54, 54, 256, 256, accentForeground());
        surface(ctx, (SIDE - 54) / 2, 22, 54, 54, 13, 0, 0x38FFFFFF, 0);
        centeredRow(ctx, "bytephase", SIDE / 2.0f, 86, 24, logoTextColor(), true, 1.8f);
        int navTop = 132, navBottom = H - 95;
        int navHeight = Tab.values().length * 44 + 20;
        navScroll = MathHelper.clamp(navScroll, 0, Math.max(0, navHeight - (navBottom - navTop)));
        float shownScroll = motion("sidebar-scroll", (float) navScroll);
        ctx.enableScissor(20, navTop, SIDE - 20, navBottom);
        int selectedIndex = tab.ordinal();
        float selectedY = navTop + selectedIndex * 44 + (selectedIndex >= Tab.CONFIGS.ordinal() ? 20 : 0) - shownScroll;
        float pillY = motion("tab-pill", selectedY);
        glass(ctx, 20, pillY, SIDE - 40, 38, NAV_RADIUS, accent(), 0x60FFFFFF);
        float y = navTop - shownScroll;
        for (Tab entry : Tab.values()) {
            if (entry == Tab.CONFIGS) {
                surface(ctx, 20, y + 3, SIDE - 40, 1, 0, LINE, 0, 0);
                y += 20;
            }
            boolean selected = tab == entry;
            float hover = motion("nav-hover:" + entry.name(), hovered(ctx, 20, y, SIDE - 40, 38) ? 1 : 0);
            if (!selected && hover > 0.01f) surface(ctx, 20, y, SIDE - 40, 38, NAV_RADIUS, withAlpha(WHITE, 0.06f * hover), 0, 0);
            icon(ctx, entry.icon, 34, y + 9, 20, selected ? accentForeground() : color());
            rowText(ctx, entry.label, 66, y, 38, selected ? accentForeground() : color(), true, 1.5f);
            hit(Action.TAB, 20, y, SIDE - 40, 38, entry, 0);
            y += 44;
        }
        ctx.disableScissor();
        surface(ctx, 20, H - 83, SIDE - 40, 1, 0, LINE, 0, 0);
        glass(ctx, 20, H - 68, 46, 46, NAV_RADIUS, 0xFF353535, 0x50FFFFFF);
        icon(ctx, "player", 33, H - 55, 20, WHITE);
        String player = BytePhase.mc.player == null ? "player" : BytePhase.mc.player.getName().getString();
        rowText(ctx, ellipsize(player, SIDE - 98, 1.4f, true), 78, H - 67, 23, WHITE, true);
        rowText(ctx, BytePhase.version(), 78, H - 44, 20, MUTED, false, 1.1f);
    }

    private List<Module> visibleModules() {
        String query = value(Field.SEARCH).toLowerCase(Locale.ROOT).replace(" ", "");
        if (tab == cachedModuleTab && query.equals(cachedQuery)) return cachedModules;
        cachedModuleTab = tab;
        cachedQuery = query;
        if (query.isEmpty()) return cachedModules = catalog.getOrDefault(tab.category, List.of());
        return cachedModules = catalog.values().stream().flatMap(List::stream)
            .filter(module -> module.getName().toLowerCase(Locale.ROOT).replace(" ", "").contains(query))
            .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private void drawModules(DrawContext ctx) {
        List<Module> modules = visibleModules();
        int gap = 12, cardW = (BODY_W - gap) / 2, cardH = 48;
        contentHeight = Math.max(0, ((modules.size() + 1) / 2) * (cardH + gap) - gap);
        float scroll = (float) clampScroll();
        if (modules.isEmpty()) { empty(ctx, "No modules match your search.", BODY_Y + 12); return; }
        for (int i = 0; i < modules.size(); i++) {
            int x = BODY_X + i % 2 * (cardW + gap);
            float y = BODY_Y + i / 2 * (cardH + gap) - scroll;
            if (y + cardH <= BODY_Y || y >= BODY_BOTTOM) continue;
            Module module = modules.get(i);
            motionKey = "module:" + module.getName();
            float hover = motion(motionKey + ":hover", hovered(ctx, x, y, cardW, cardH) && dialog == null ? 1 : 0);
            float enabled = motion(motionKey + ":enabled", module.isEnabled() ? 1 : 0);
            if (enabled > 0.01f) LiquidGui.glow(ctx, x, y, cardW, cardH, CARD_RADIUS, 22, withAlpha(accent(), 0.20f * enabled));
            int border = Render2D.lerpColor(LINE, withAlpha(accent(), 0.7f), enabled);
            border = Render2D.lerpColor(border, 0x70FFFFFF, hover);
            if (keyboardCard == i) border = accent();
            glass(ctx, x, y, cardW, cardH, CARD_RADIUS, withAlpha(WHITE, 0.07f + 0.06f * hover), border);
            int foreground = Render2D.lerpColor(MUTED, WHITE, enabled);
            icon(ctx, categoryIcon(module.getCategory()), x + 14, y + 14, 20, foreground);
            String bind = Keybinds.keyCode(module) < 0 ? "" : ellipsize(Keybinds.getKeyName(module), 64, 1.1f, false);
            float bindW = labelWidth(bind, 1.1f, false);
            float switchX = x + cardW - 14 - 28;
            float bindX = switchX - 12 - bindW;
            float nameX = x + 46;
            float nameRight = bind.isEmpty() ? switchX - 12 : bindX - 12;
            rowText(ctx, ellipsize(module.getName(), nameRight - nameX, 1.4f, false), nameX, y, cardH, foreground, false);
            if (!bind.isEmpty()) rowText(ctx, bind, bindX, y, cardH, MUTED, false, 1.1f);
            toggle(ctx, switchX, y + 16, module.isEnabled(), true);
            hit(Action.MODULE, x, y, cardW, cardH, module, i);
        }
    }

    private void drawConfigs(DrawContext ctx) {
        contentHeight = 268 + Math.max(1, profiles.size()) * 70;
        float y = BODY_Y - (float) clampScroll();
        rowText(ctx, "Create config", BODY_X, y, 24, color(), true);
        field(ctx, Field.CONFIG_NAME, "Config name", BODY_X, y + 34, BODY_W - 142, 38);
        button(ctx, "Save current", Action.SAVE, BODY_X + BODY_W - 130, y + 34, 130, 38, null, 0, false);
        rowText(ctx, "Your configs", BODY_X, y + 86, 24, color(), true);
        if (!profileStorage) empty(ctx, "Profile storage unavailable. Reopen Configs to retry.", y + 120);
        else if (profiles.isEmpty()) empty(ctx, "No configs yet. Save the current settings above.", y + 120);
        for (int i = 0; i < profiles.size(); i++) {
            String name = profiles.get(i);
            float cy = y + 120 + i * 70;
            if (cy + 58 <= BODY_Y || cy >= BODY_BOTTOM) continue;
            glass(ctx, BODY_X, cy, BODY_W, 58, ROW_RADIUS, 0x10FFFFFF, name.equals(loadedProfile) ? accent() : LINE);
            rowText(ctx, ellipsize(name, BODY_W - 350, 1.4f, true), BODY_X + 14, cy + 8, 22, color(), true);
            rowText(ctx, name.equals(loadedProfile) ? "Loaded" : "Module settings and states", BODY_X + 14, cy + 30, 20, MUTED, false, 1.2f);
            int bx = BODY_X + BODY_W - 322;
            button(ctx, "Load", Action.LOAD, bx, cy + 13, 54, 32, name, 0, false);
            button(ctx, "Overwrite", Action.OVERWRITE, bx + 62, cy + 13, 94, 32, name, 0, true);
            button(ctx, "Copy", Action.COPY, bx + 164, cy + 13, 58, 32, name, 0, true);
            button(ctx, name.equals(confirmDelete) ? "Confirm" : "Delete", Action.DELETE, bx + 230, cy + 13, 78, 32, name, 0, true);
        }
        float iy = y + 138 + Math.max(1, profiles.size()) * 70;
        rowText(ctx, "Import a copied Bytephase config", BODY_X, iy, 24, color(), true);
        field(ctx, Field.IMPORT_JSON, "Paste config JSON (Ctrl+V)", BODY_X, iy + 34, BODY_W, 90);
        field(ctx, Field.IMPORT_NAME, "Name for imported config", BODY_X, iy + 136, BODY_W - 110, 38);
        button(ctx, "Import", Action.IMPORT, BODY_X + BODY_W - 98, iy + 136, 98, 38, null, 0, false);
        contentHeight = (int) (iy + 188 - y);
    }

    private void drawFriends(DrawContext ctx) {
        contentHeight = 64 + Math.max(1, friends.size()) * 70;
        float y = BODY_Y - (float) clampScroll();
        field(ctx, Field.FRIEND_NAME, "Add a friend by name", BODY_X, y, BODY_W - 94, 38);
        button(ctx, "Add", Action.FRIEND_ADD, BODY_X + BODY_W - 82, y, 82, 38, null, 0, false);
        if (friends.isEmpty()) empty(ctx, "No friends added yet.", y + 64);
        for (int i = 0; i < friends.size(); i++) {
            float cy = y + 64 + i * 70;
            if (cy + 58 <= BODY_Y || cy >= BODY_BOTTOM) continue;
            glass(ctx, BODY_X, cy, BODY_W, 58, ROW_RADIUS, 0x10FFFFFF, LINE);
            icon(ctx, "player", BODY_X + 14, cy + 19, 20, MUTED);
            rowText(ctx, ellipsize(friends.get(i), BODY_W - 180, 1.4f, false), BODY_X + 46, cy, 58, color(), false);
            button(ctx, "Remove", Action.FRIEND_REMOVE, BODY_X + BODY_W - 110, cy + 13, 96, 32, friends.get(i), 0, true);
        }
    }

    private void drawThemes(DrawContext ctx) {
        List<Setting<?>> settings = appearance == null ? List.of() : appearance.getSettings().stream().filter(ColorSetting.class::isInstance).toList();
        contentHeight = 76 + settings.stream().filter(Setting::isVisible).mapToInt(ClickGuiScreen::settingHeight).sum();
        float y = BODY_Y - (float) clampScroll();
        rowText(ctx, "Custom colors", BODY_X, y, 24, color(), true);
        rowText(ctx, "Click any color to choose freely, or paste a hex value.", BODY_X, y + 30, 24, MUTED, false, 1.2f);
        float sy = y + 76;
        for (Setting<?> setting : settings) if (setting.isVisible()) {
            drawSetting(ctx, setting, BODY_X, sy, BODY_W);
            sy += settingHeight(setting);
        }
    }

    private void drawSettings(DrawContext ctx) {
        List<Setting<?>> settings = appearance == null ? List.of() : appearance.getSettings().stream().filter(setting -> !(setting instanceof ColorSetting)).toList();
        contentHeight = 68 + settings.stream().filter(Setting::isVisible).mapToInt(ClickGuiScreen::settingHeight).sum();
        float y = BODY_Y - (float) clampScroll();
        rowText(ctx, "Interface and HUD", BODY_X, y, 24, color(), true);
        rowText(ctx, ellipsize(UiBackgroundImage.status(), BODY_W, 1.2f, false), BODY_X, y + 28, 24, MUTED, false, 1.2f);
        float sy = y + 68;
        for (Setting<?> setting : settings) if (setting.isVisible()) {
            drawSetting(ctx, setting, BODY_X, sy, BODY_W);
            sy += settingHeight(setting);
        }
    }

    private void drawEditor(DrawContext ctx) {
        if (dragging == null && System.nanoTime() > editorRefresh
                && editorSettings.stream().anyMatch(dev.bytephase.setting.PreviewSetting.class::isInstance)) {
            editorRefresh = System.nanoTime() + 250000000L;
            editorSettings = List.copyOf(editorFactory.get());
        }
        String query = value(Field.SEARCH).toLowerCase(Locale.ROOT);
        List<Setting<?>> settings = editorSettings.stream().filter(Setting::isVisible)
            .filter(setting -> setting.getName().toLowerCase(Locale.ROOT).contains(query)).toList();
        contentHeight = settings.stream().mapToInt(ClickGuiScreen::settingHeight).sum();
        float y = BODY_Y - (float) clampScroll();
        if (settings.isEmpty()) { empty(ctx, "No settings match your search.", BODY_Y + 12); return; }
        for (Setting<?> setting : settings) {
            if (y + settingHeight(setting) > BODY_Y && y < BODY_BOTTOM) drawSetting(ctx, setting, BODY_X, y, BODY_W);
            y += settingHeight(setting);
        }
    }

    private void drawDialog(DrawContext ctx) {
        hits.clear();
        focused = null;
        if (animatedDialog != dialog) {
            animatedDialog = dialog;
            animations.put("dialog-open:" + dialog.getName(), 0f);
            animations.put("dialog-scroll:" + dialog.getName(), 0f);
        }
        int settingsH = dialog.getSettings().stream().filter(Setting::isVisible).mapToInt(ClickGuiScreen::settingHeight).sum();
        dialogH = Math.min((int) (H * 0.86f), Math.max(220, settingsH + 186));
        dialogX = (W - DIALOG_W) / 2;
        dialogY = (H - dialogH) / 2;
        surface(ctx, 0, 0, W, H, PANEL_RADIUS, 0x4D080808, 0, 0);
        float progress = motion("dialog-open:" + dialog.getName(), 1);
        float zoom = 0.98f + 0.02f * progress;
        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(W / 2f, H / 2f);
        matrices.scale(zoom, zoom);
        matrices.translate(-W / 2f, -H / 2f);
        LiquidGui.opacity(openProgress * progress);
        try {
            LiquidGui.shadow(ctx, dialogX, dialogY + 12, DIALOG_W, dialogH, DIALOG_RADIUS, 30, 0x60000000);
            UiBackgroundImage.panel(ctx, dialogX, dialogY, DIALOG_W, dialogH, DIALOG_RADIUS, 208, 0x40FFFFFF, backdrop, appearance);
            rowText(ctx, ellipsize(dialog.getName(), DIALOG_W - 116, 1.6f, true), dialogX + 16, dialogY + 12, 34, WHITE, true, 1.6f);
            button(ctx, "Close", Action.CLOSE_DIALOG, dialogX + DIALOG_W - 90, dialogY + 12, 74, 32, null, 0, true);
            float y = dialogY + 56;
            surface(ctx, dialogX + 16, y, DIALOG_W - 32, 1, 0, LINE, 0, 0);
            rowText(ctx, "Enabled", dialogX + 18, y, 42, color(), false);
            motionKey = "dialog-enabled:" + dialog.getName();
            toggle(ctx, dialogX + DIALOG_W - 56, y + 10, dialog.isEnabled(), false);
            hit(Action.ENABLE, dialogX + 16, y, DIALOG_W - 32, 42, dialog, 0);
            rowText(ctx, "Keybind", dialogX + 18, y + 42, 42, color(), false);
            String bind = binding == dialog ? "Press a key…" : Keybinds.keyCode(dialog) < 0 ? "None" : Keybinds.getKeyName(dialog);
            button(ctx, bind, Action.BIND, dialogX + DIALOG_W - 172, y + 47, 156, 32, dialog, 0, true);
            int top = dialogY + 146, bottom = dialogY + dialogH - 54;
            dialogContentHeight = settingsH;
            dialogScroll = MathHelper.clamp(dialogScroll, 0, Math.max(0, settingsH - (bottom - top)));
            float shownScroll = motion("dialog-scroll:" + dialog.getName(), (float) dialogScroll);
            ctx.enableScissor(dialogX + 16, top, dialogX + DIALOG_W - 16, bottom);
            float sy = top - shownScroll;
            for (Setting<?> setting : dialog.getSettings()) if (setting.isVisible()) {
                drawSetting(ctx, setting, dialogX + 18, sy, DIALOG_W - 36);
                sy += settingHeight(setting);
            }
            ctx.disableScissor();
            drawScrollbar(ctx, dialogX + DIALOG_W - 9, top, bottom - top, shownScroll, settingsH);
            surface(ctx, dialogX + 16, dialogY + dialogH - 46, DIALOG_W - 32, 1, 0, LINE, 0, 0);
            button(ctx, "Reset module", Action.RESET, dialogX + 16, dialogY + dialogH - 38, 130, 30, dialog, 0, true);
            rowText(ctx, "Esc: close", dialogX + 164, dialogY + dialogH - 38, 30, MUTED, false, 1.2f);
        } finally {
            matrices.popMatrix();
            LiquidGui.opacity(openProgress);
        }
    }

    private void drawSetting(DrawContext ctx, Setting<?> setting, int x, float y, int w) {
        int h = settingHeight(setting);
        motionKey = "setting:" + (dialog == null ? tab.name() : dialog.getName()) + ":" + setting.getName();
        if (setting instanceof dev.bytephase.setting.PreviewSetting preview) {
            rowText(ctx, preview.getName(), x, y, 30, color(), true);
            glass(ctx, x, y + 34, w, 260, ROW_RADIUS, 0x60080808, LINE);
            boolean ready = false;
            preview.failed = failedPreviews.contains(preview.scene);
            if (!preview.failed) try {
                LiquidGui.Rect rect = LiquidGui.rect(ctx, x, y + 34, w, 260);
                float pw = rect.w() * LiquidGui.pixelScale(), ph = rect.h() * LiquidGui.pixelScale();
                float quality = Math.min(1, Math.min(960 / Math.max(1, pw), 640 / Math.max(1, ph)));
                int renderW = Math.max(1, Math.round(pw * quality)), renderH = Math.max(1, Math.round(ph * quality));
                long now = System.nanoTime();
                PreviewFrame previous = previewFrames.get(preview.scene);
                com.mojang.blaze3d.textures.GpuTextureView view;
                if (previous != null && previous.w == renderW && previous.h == renderH
                        && now - previous.time < 33333333L) view = previous.view;
                else {
                    float elapsed = frameSeconds == 0 ? 0 : previous == null ? frameSeconds
                        : Math.min(0.1f, (now - previous.time) / 1.0e9f);
                    view = preview.scene.nativeFrame(renderW, renderH, elapsed);
                    previewFrames.put(preview.scene, new PreviewFrame(view, now, renderW, renderH));
                }
                if (view != null) {
                    LiquidGui.picture(ctx, x, y + 34, w, 260, ROW_RADIUS, view);
                    ready = true;
                }
            } catch (RuntimeException | LinkageError error) {
                preview.failed = true;
                failedPreviews.add(preview.scene);
                BytePhase.LOGGER.warn("Native model preview failed", error);
            }
            if (!ready) centeredRow(ctx, preview.failed ? "Preview unavailable" : "Loading preview…",
                x + w / 2f, y + 34, 260, MUTED, false);
            rowText(ctx, "Drag to rotate · Scroll to zoom", x, y + 299, 22, MUTED, false, 1.2f);
            hit(Action.PREVIEW, x, y + 34, w, 260, preview, 0);
            return;
        }
        surface(ctx, x, y, w, 1, 0, LINE, 0, 0);
        int controlW = Math.min(180, w / 2), controlX = x + w - controlW;
        rowText(ctx, ellipsize(setting.getName(), w - controlW - 14, 1.4f, false), x, y, SETTING_ROW_HEIGHT, color(), false);
        if (setting instanceof BooleanSetting bool) {
            toggle(ctx, x + w - 38, y + 10, bool.getValue(), false);
            if (setting.getName().equals("StreamProof")) {
                String state = bool.getValue() ? dev.bytephase.render.capture.StreamProof.status() : "Window capture · Ctrl+Shift+F9 toggles";
                rowText(ctx, ellipsize(state, w, 1.2f, false), x, y + SETTING_ROW_HEIGHT, 22, MUTED, false, 1.2f);
            }
            hit(Action.BOOL, x, y, w, h, setting, 0);
        } else if (setting instanceof ModeSetting mode) {
            glass(ctx, controlX, y + 5, controlW, CONTROL_HEIGHT, 8, 0x20FFFFFF, LINE);
            rowText(ctx, ellipsize(mode.getValue(), controlW - 32, 1.3f, false), controlX + 10, y, SETTING_ROW_HEIGHT, color(), false, 1.3f);
            rowText(ctx, "›", x + w - 17, y, SETTING_ROW_HEIGHT, MUTED, false, 1.3f);
            hit(Action.MODE, controlX, y + 5, controlW, CONTROL_HEIGHT, setting, 0);
        } else if (setting instanceof ActionKeySetting key) {
            String shown = actionBinding == key ? "Press key or mouse…" : actionKeyName(key.getValue());
            button(ctx, shown, Action.ACTION_KEY, controlX, y + 5, controlW, CONTROL_HEIGHT, key, 0, true);
        } else if (setting instanceof dev.bytephase.setting.LabelSetting info) {
            rowText(ctx, ellipsize(info.getValue(), controlW, 1.3f, false), controlX,
                y, SETTING_ROW_HEIGHT, MUTED, false, 1.3f);
        } else if (setting instanceof dev.bytephase.setting.StringSetting text) {
            button(ctx, text.getValue().isBlank() ? "Edit…" : text.getValue(), Action.TEXT_SETTING, controlX, y + 5, controlW, CONTROL_HEIGHT, setting, 0, true);
        } else if (setting instanceof dev.bytephase.setting.ActionSetting action) {
            button(ctx, action.label(), Action.RUN_ACTION, controlX, y + 5, controlW, CONTROL_HEIGHT, setting, 0, true);
        } else if (setting instanceof NumberSetting number) {
            int trackW = controlW - 48;
            rowText(ctx, ellipsize(formatNumber(number.getValue()), 42, 1.2f, false), x + w - 42, y, SETTING_ROW_HEIGHT, MUTED, false, 1.2f);
            slider(ctx, controlX, y + SETTING_ROW_HEIGHT / 2f, trackW, (number.getValue() - number.getMin()) / Math.max(1e-9, number.getMax() - number.getMin()), accent());
            hit(Action.NUMBER, controlX, y + 5, trackW, CONTROL_HEIGHT, setting, 0);
        } else if (setting instanceof ColorSetting color) {
            String hex = color.alpha() == 255 ? String.format(Locale.ROOT, "#%06X", color.rgb())
                : String.format(Locale.ROOT, "#%08X", color.getValue());
            glass(ctx, controlX, y + 5, controlW, CONTROL_HEIGHT, 8, 0x20FFFFFF, LINE);
            surface(ctx, controlX + 8, y + 10, 22, 22, 6, 0xFF000000 | color.rgb(), 0x50FFFFFF, 0);
            rowText(ctx, hex, controlX + 38, y, SETTING_ROW_HEIGHT, color(), false, 1.2f);
            hit(Action.COLOR, controlX, y + 5, controlW, CONTROL_HEIGHT, color, 0);
        }
    }

    private static int settingHeight(Setting<?> setting) {
        if (setting instanceof dev.bytephase.setting.PreviewSetting) return 330;
        if (setting instanceof BooleanSetting && setting.getName().equals("StreamProof")) return 66;
        return SETTING_ROW_HEIGHT;
    }
    private void slider(DrawContext ctx, float x, float centerY, int w, double fraction, int color) {
        float fill = w * (float) MathHelper.clamp(fraction, 0, 1);
        surface(ctx, x, centerY - 2, w, 4, 2, 0x60080808, 0, 0);
        if (fill > 0) surface(ctx, x, centerY - 2, fill, 4, 2, color, 0, 0);
        surface(ctx, x + fill - 5, centerY - 5, 10, 10, 5, WHITE, 0x30FFFFFF, 0);
    }
    private void toggle(DrawContext ctx, float x, float y, boolean on, boolean small) {
        int w = small ? 28 : 38, h = small ? 16 : 22, knob = small ? 10 : 14, inset = small ? 2 : 3;
        float value = motion(motionKey + ":toggle", on ? 1 : 0);
        surface(ctx, x, y, w, h, h / 2f, Render2D.lerpColor(0xFF161616, accent(), value), LINE, 0);
        float knobX = x + inset + value * (w - knob - 2 * inset);
        surface(ctx, knobX, y + (h - knob) / 2f, knob, knob, knob / 2f, 0xFFF0F0F0, 0, 0);
    }
    private void drawScrollbar(DrawContext ctx, int x, int y, int h, double scroll, int content) {
        if (content <= h || h <= 0) return;
        surface(ctx, x, y, 3, h, 1.5f, 0x16FFFFFF, 0, 0);
        float thumb = Math.max(24, (float) h * h / content);
        float top = y + (h - thumb) * (float) MathHelper.clamp(scroll / Math.max(1, content - h), 0, 1);
        surface(ctx, x, top, 3, thumb, 1.5f, 0x80C0C0C0, 0, 0);
    }
    private double clampScroll() {
        if (editorFactory != null) {
            editorScroll = MathHelper.clamp(editorScroll, 0, Math.max(0, contentHeight - (BODY_BOTTOM - BODY_Y)));
            return motion("editor-scroll", (float) editorScroll);
        }
        double target = MathHelper.clamp(scrolls.getOrDefault(tab, 0.0), 0, Math.max(0, contentHeight - (BODY_BOTTOM - BODY_Y)));
        scrolls.put(tab, target);
        return motion("scroll:" + tab.name(), (float) target);
    }
    private float displayedScroll() {
        return animations.getOrDefault(editorFactory == null ? "scroll:" + tab.name() : "editor-scroll", 0f);
    }
    private void empty(DrawContext ctx, String message, float y) {
        glass(ctx, BODY_X, y, BODY_W, 80, ROW_RADIUS, 0x06FFFFFF, LINE);
        centeredRow(ctx, ellipsize(message, BODY_W - 40, 1.4f, false), BODY_X + BODY_W / 2f, y, 80, MUTED, false);
    }
    private void button(DrawContext ctx, String label, Action action, float x, float y, int w, int h, Object target, int data, boolean secondary) {
        float hover = motion("button:" + action + ":" + target + ":" + data, hovered(ctx, x, y, w, h) ? 1 : 0);
        boolean destructive = action == Action.DELETE || action == Action.RESET || action == Action.FRIEND_REMOVE;
        int bg = destructive ? Render2D.lerpColor(0x2EDADADA, 0x50DADADA, hover)
            : secondary ? Render2D.lerpColor(0x18FFFFFF, 0x30FFFFFF, hover) : accent();
        int fg = destructive ? 0xFFD5D5D5 : secondary ? WHITE : accentForeground();
        glass(ctx, x, y, w, h, CONTROL_RADIUS, bg, 0x28FFFFFF);
        centeredRow(ctx, ellipsize(label, w - 20, 1.3f, true), x + w / 2f, y, h, fg, true, 1.3f);
        hit(action, x, y, w, h, target, data);
    }
    private void field(DrawContext ctx, Field field, String placeholder, float x, float y, int w, int h) {
        boolean active = focused == field;
        glass(ctx, x, y, w, h, CONTROL_RADIUS, 0x10FFFFFF, active ? accent() : LINE);
        String value = value(field), display = value.isEmpty() ? placeholder : value;
        display = ellipsize(display, w - 24, 1.3f, false);
        if (active && selectedAll && !value.isEmpty()) surface(ctx, x + 6, y + 5, w - 12, h - 10, 4, 0x30FFFFFF, 0, 0);
        if (field == Field.IMPORT_JSON) {
            ctx.enableScissor((int) x + 10, (int) y + 8, (int) x + w - 10, (int) y + h - 8);
            String[] lines = (value.isEmpty() ? placeholder : value).split("\\R", 5);
            for (int i = 0; i < Math.min(3, lines.length); i++)
                rowText(ctx, ellipsize(lines[i], w - 24, 1.2f, false), x + 12, y + 8 + i * 18, 18,
                    value.isEmpty() ? MUTED : color(), false, 1.2f);
            if (!value.isEmpty()) rowText(ctx, value.length() + " characters", x + 12, y + h - 26, 18, MUTED, false, 1.1f);
            ctx.disableScissor();
        } else rowText(ctx, display, x + 12, y, h, value.isEmpty() ? MUTED : color(), false, 1.3f);
        if (field != Field.IMPORT_JSON && active && System.nanoTime() / 500000000L % 2 == 0) {
            String prefix = field == Field.IMPORT_JSON ? display : value.substring(0, Math.min(caret, value.length()));
            float cw = Math.min(w - 24, labelWidth(prefix, 1.3f, false));
            surface(ctx, x + 12 + cw, y + (h - 14) / 2f, 1, 14, 0, accent(), 0, 0);
        }
        hit(Action.FIELD, x, y, w, h, field, 0);
    }
    private void icon(DrawContext ctx, String name, float x, float y, int size, int tint) {
        image(ctx, Identifier.of("bytephase", "ui/" + name + ".png"), x, y, size, size, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, tint);
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
    private static void image(DrawContext ctx, Identifier texture, float x, float y, int width, int height, int textureWidth, int textureHeight, int tint) {
        LiquidGui.Rect rect = LiquidGui.rect(ctx, x, y, width, height);
        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.identity();
        matrices.translate(rect.x(), rect.y());
        matrices.scale(rect.w() / width, rect.h() / height);
        try {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0,
                width, height, textureWidth, textureHeight, textureWidth, textureHeight, tint);
        } finally { matrices.popMatrix(); }
    }
    private static void surface(DrawContext ctx, float x, float y, float w, float h, float radius, int bg, int border, int flags) {
        LiquidGui.surface(ctx, x, y, w, h, radius, bg, border, flags);
    }
    private static void glass(DrawContext ctx, float x, float y, float w, float h, float radius, int bg, int border) {
        surface(ctx, x, y, w, h, radius, bg, border, LiquidGui.GRADIENT | LiquidGui.HIGHLIGHT);
    }
    private int accent() { return appearance == null ? 0xFFC0C0C0 : appearance.accent(); }
    private int accentForeground() { return appearance == null ? WHITE : appearance.accentForeground(); }
    private int logoTextColor() {
        int accent = accent(), background = appearance == null ? Theme.PANEL : appearance.panelColor();
        int difference = Math.abs((accent >> 16 & 255) - (background >> 16 & 255))
            + Math.abs((accent >> 8 & 255) - (background >> 8 & 255))
            + Math.abs((accent & 255) - (background & 255));
        return difference < 120 ? accentForeground() : accent;
    }
    private int color() { return appearance == null ? WHITE : appearance.text(); }
    private static int withAlpha(int color, float alpha) { return Math.round(MathHelper.clamp(alpha, 0, 1) * 255) << 24 | color & 0xFFFFFF; }
    private Text label(String value, boolean bold) {
        String display = dev.bytephase.module.misc.NameProtect.filter(value);
        if (labels.size() > 4096) labels.clear();
        boolean weight = bold && (appearance == null || appearance.boldHeaders());
        return labels.computeIfAbsent((weight ? "1:" : "0:") + display,
            key -> Text.literal(display).styled(style -> style.withBold(weight)));
    }
    private float labelWidth(String value, float fontScale, boolean bold) {
        String display = dev.bytephase.module.misc.NameProtect.filter(value);
        if (widths.size() > 4096) widths.clear();
        boolean weight = bold && (appearance == null || appearance.boldHeaders());
        return widths.computeIfAbsent((weight ? "1:" : "0:") + display,
            key -> textRenderer.getWidth(label(display, bold))) * fontScale;
    }
    private String ellipsize(String value, float available, float fontScale, boolean bold) {
        value = dev.bytephase.module.misc.NameProtect.filter(value);
        if (value == null || available <= 0) return "";
        if (labelWidth(value, fontScale, bold) <= available) return value;
        String dots = "…";
        float remaining = available - labelWidth(dots, fontScale, bold);
        if (remaining <= 0) return "";
        String clipped = textRenderer.getTextHandler().trimToWidth(value, (int) (remaining / fontScale),
            ClientFonts.measuredStyle(bold && (appearance == null || appearance.boldHeaders())));
        while (!clipped.isEmpty() && labelWidth(clipped + dots, fontScale, bold) > available)
            clipped = clipped.substring(0, clipped.offsetByCodePoints(clipped.length(), -1));
        return clipped + dots;
    }
    private void rowText(DrawContext ctx, String value, float x, float rowY, float rowHeight, int color, boolean bold) {
        rowText(ctx, value, x, rowY, rowHeight, color, bold, 1.4f);
    }
    private void rowText(DrawContext ctx, String value, float x, float rowY, float rowHeight, int color, boolean bold, float textScale) {
        boolean semibold = bold && (appearance == null || appearance.boldHeaders());
        float textY = rowY + rowHeight / 2f - ClientFonts.capCenterOffset(semibold) * textScale;
        var point = LiquidGui.textOrigin(ctx, x, textY);
        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(point.x, point.y);
        matrices.scale(textScale, textScale);
        try { ctx.drawText(textRenderer, label(value, bold), 0, 0, color, appearance != null && appearance.textShadow()); }
        finally { matrices.popMatrix(); }
    }
    private void centeredRow(DrawContext ctx, String value, float centerX, float rowY, float rowHeight, int color, boolean bold) {
        centeredRow(ctx, value, centerX, rowY, rowHeight, color, bold, 1.4f);
    }
    private void centeredRow(DrawContext ctx, String value, float centerX, float rowY, float rowHeight, int color, boolean bold, float textScale) {
        rowText(ctx, value, centerX - labelWidth(value, textScale, bold) / 2f, rowY, rowHeight, color, bold, textScale);
    }
    private float motion(String key, float target) {
        float previous = animations.getOrDefault(key, target);
        float value = previous + (target - previous) * animationStep;
        if (Math.abs(value - target) < 0.01f) value = target;
        animations.put(key, value);
        return value;
    }
    private boolean hovered(DrawContext ctx, float x, float y, float w, float h) {
        LiquidGui.Rect rect = LiquidGui.rect(ctx, x, y, w, h);
        var clip = ctx.scissorStack.peekLast();
        float sx = originX + (float) mouseX * scale, sy = originY + (float) mouseY * scale;
        return rect.contains(sx, sy) && (clip == null || clip.contains((int) sx, (int) sy));
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
    private void hit(Action action, float x, float y, float w, float h, Object target, int data) {
        LiquidGui.Rect rect = LiquidGui.rect(drawingContext, x, y, w, h);
        var scissor = drawingContext.scissorStack.peekLast();
        LiquidGui.Rect clip = scissor == null ? null : new LiquidGui.Rect(
            (scissor.getLeft() - originX) / scale, (scissor.getTop() - originY) / scale,
            scissor.width() / scale, scissor.height() / scale);
        hits.add(new Hit(action, (rect.x() - originX) / scale, (rect.y() - originY) / scale,
            rect.w() / scale, rect.h() / scale, target, data, clip));
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
            if (hit.action != Action.FIELD) focused = null;
            switch (hit.action) {
                case TAB -> { if (button == 0) selectTab((Tab) hit.target); }
                case FIELD -> { if (button == 0) focus((Field) hit.target); }
                case MODULE -> {
                    if (button == 0) ((Module) hit.target).toggle();
                    if (button == 1) {
                        dev.bytephase.engine.categories.settings.ClientSounds.updateCardOpen();
                        dialog = (Module) hit.target; dialogScroll = 0; dragging = null;
                    }
                    keyboardCard = hit.data;
                }
                case CLOSE_DIALOG -> closeDialog();
                case ENABLE -> { if (button == 0) dialog.toggle(); }
                case BIND -> { if (button == 0) binding = dialog; else if (button == 1) { Keybinds.set(dialog, GLFW.GLFW_KEY_UNKNOWN); persist(); } }
                case ACTION_KEY -> { if (button == 0) actionBinding = (ActionKeySetting) hit.target; else if (button == 1) ((ActionKeySetting) hit.target).setValue(-1); }
                case BOOL -> { if (button == 0) ((BooleanSetting) hit.target).toggle(); }
                case TEXT_SETTING -> {
                    if (button == 0) {
                        var text = (dev.bytephase.setting.StringSetting)hit.target;
                        if (appearance != null && text == appearance.imagePathSetting())
                            client.setScreen(new TextSettingScreen(this, text, appearance::setBackgroundImage));
                        else client.setScreen(new TextSettingScreen(this, text));
                    }
                }
                case RUN_ACTION -> {
                    if (button == 0) {
                        try {
                            ((dev.bytephase.setting.ActionSetting) hit.target).run();
                            if (editorFactory != null && client.currentScreen == this)
                                editorSettings = List.copyOf(editorFactory.get());
                        } catch (RuntimeException error) {
                            BytePhase.LOGGER.warn("Native editor action failed", error);
                            toast("Could not apply that action. Check the selected values.");
                        }
                    }
                }
                case EDITOR_BACK -> backFromEditor();
                case PREVIEW -> { if (button == 0 || button == 1) dragging = hit; }
                case MODE -> {
                    if (button == 0 || button == 1) { ModeSetting mode = (ModeSetting) hit.target; String[] modes = mode.getModes();
                        mode.setValue(modes[Math.floorMod(mode.getIndex() + (button == 1 ? -1 : 1), modes.length)]); }
                }
                case NUMBER -> { if (button == 0) { dragging = hit; setSlider(hit, mx); } }
                case COLOR -> { if (button == 0) client.setScreen(new ColorPickerScreen(this, (ColorSetting)hit.target)); }
                case RESET -> { if (button == 0) resetModule(); }
                default -> { if (button == 0) perform(hit); }
            }
            if (isSetting(hit.action)) {
                persist();
                if (editorFactory != null && client.currentScreen == this
                        && hit.action != Action.NUMBER && hit.action != Action.COLOR)
                    editorSettings = List.copyOf(editorFactory.get());
                hits.clear();
            }
            return true;
        }
        focused = null;
        if (dialog != null && !inside(mx, my, dialogX, dialogY, DIALOG_W, dialogH)) closeDialog();
        return true;
    }
    private static boolean isSetting(Action action) { return action == Action.BOOL || action == Action.NUMBER || action == Action.MODE || action == Action.COLOR || action == Action.ACTION_KEY || action == Action.TEXT_SETTING || action == Action.RUN_ACTION; }
    private void selectTab(Tab value) {
        closePreviews();
        editorFactory = null; editorSettings = List.of(); editorTitle = null; editorParent = null; editorScroll = 0; editorOwner = null;
        dev.bytephase.engine.categories.settings.ClientSounds.updateCategoryClick();
        tab = value; savedTab = value; focused = null; keyboardCard = -1; fields.put(Field.SEARCH, "");
        dragging = null; confirmDelete = ""; confirmOverwrite = ""; hits.clear();
        if (tab == Tab.CONFIGS) readProfiles();
        if (tab == Tab.FRIENDS) friends = CombatIntegration.friends();
        persist();
    }
    private void closeDialog() { dialog = null; animatedDialog = null; binding = null; actionBinding = null; dragging = null; hits.clear(); }
    private void backFromEditor() {
        closePreviews();
        if (editorParent != null) client.setScreen(editorParent);
        else client.setScreen(new ClickGuiScreen());
    }
    private void closePreviews() {
        previewFrames.clear();
        failedPreviews.clear();
        for (Setting<?> setting : editorSettings)
            if (setting instanceof dev.bytephase.setting.PreviewSetting preview) try {
                preview.scene.nativeClose();
            } catch (RuntimeException | LinkageError error) {
                BytePhase.LOGGER.warn("Could not release native preview resources", error);
            }
    }
    @Override public void close() {
        if (editorFactory != null) backFromEditor();
        else super.close();
    }
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
        if (dragging != null) {
            if (dragging.target instanceof dev.bytephase.setting.PreviewSetting preview)
                { preview.scene.nativeDrag(dx / scale, dy / scale, click.buttonInfo().button()); previewFrames.remove(preview.scene); }
            else setSlider(dragging, (click.x() - originX) / scale);
            return true;
        }
        return true;
    }
    @Override public boolean mouseReleased(Click click) {
        if (dragging != null) {
            if (dragging.target instanceof dev.bytephase.setting.PreviewSetting preview) preview.scene.nativeDrag(0, 0, -1);
            else persist();
        }
        dragging = null;
        if (editorFactory != null) editorSettings = List.copyOf(editorFactory.get());
        return true;
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        double x = (mx - originX) / scale, y = (my - originY) / scale;
        for (Hit hit : hits) if (hit.action == Action.PREVIEW && hit.contains(x, y)) {
            var preview = (dev.bytephase.setting.PreviewSetting) hit.target;
            preview.scene.nativeZoom(vertical);
            previewFrames.remove(preview.scene);
            return true;
        }
        dragging = null;
        if (dialog != null) {
            if (inside(x, y, dialogX, dialogY, DIALOG_W, dialogH)) dialogScroll = Math.max(0, dialogScroll - vertical * 42);
        } else if (inside(x, y, 20, 132, SIDE - 40, H - 227)) {
            navScroll = Math.max(0, navScroll - vertical * 44);
        } else if (inside(x, y, BODY_X, BODY_Y, BODY_W, BODY_BOTTOM - BODY_Y)) {
            if (editorFactory != null) editorScroll = Math.max(0, editorScroll - vertical * 48);
            else scrolls.put(tab, Math.max(0, scrolls.getOrDefault(tab, 0.0) - vertical * 48));
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
        if (focused == Field.SEARCH) { scrolls.put(tab, 0.0); editorScroll = 0; keyboardCard = -1; }
        confirmDelete = ""; confirmOverwrite = ""; hits.clear();
    }

    @Override public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (actionBinding != null) { actionBinding.setValue(key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE ? -1 : key); actionBinding = null; persist(); return true; }
        if (binding != null) { Keybinds.set(binding, key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE ? GLFW.GLFW_KEY_UNKNOWN : key); binding = null; persist(); return true; }
        if (Keybinds.matchesClickGui(input)) { Keybinds.suppressNextClickGui(); close(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (dialog != null) closeDialog(); else if (focused != null) focused = null;
            else if (editorFactory != null) backFromEditor(); else close();
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
        if (editorFactory == null && dialog == null && (tab.category != null || !value(Field.SEARCH).isEmpty())) {
            List<Module> list = visibleModules();
            if (!list.isEmpty()) {
                int step = key == GLFW.GLFW_KEY_DOWN ? 2 : key == GLFW.GLFW_KEY_UP ? -2 : key == GLFW.GLFW_KEY_RIGHT ? 1 : key == GLFW.GLFW_KEY_LEFT ? -1 : 0;
                if (step != 0) { keyboardCard = MathHelper.clamp(keyboardCard < 0 ? 0 : keyboardCard + step, 0, list.size() - 1); scrolls.put(tab, Math.max(0, keyboardCard / 2 * 60.0 - 120)); return true; }
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
        // The cached scene is blurred only inside the panel's rounded mask.
    }
    @Override public void removed() {
        closePreviews();
        savedTab = tab; SAVED_SCROLL.clear(); SAVED_SCROLL.putAll(scrolls);
        if (BytePhase.config != null) BytePhase.config.save();
        super.removed();
    }
}
