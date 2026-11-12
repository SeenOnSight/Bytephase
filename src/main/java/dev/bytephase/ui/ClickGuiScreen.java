package dev.bytephase.ui;

import dev.bytephase.BytePhase;
import dev.bytephase.input.Keybinds;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.Render2D;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.setting.Setting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/** Bytephase floating category panels connected to Bytephase's live modules/settings. */
public final class ClickGuiScreen extends Screen {
    private static final int PANEL_W = 140;
    private static final int HEADER_H = 18;
    private static final int ROW_H = 15;
    private static final int GAP = 0;
    private static final int PAD = 0;
    private static final String[] COLOR_CHANNEL_LABELS = {"R", "G", "B"};
    private static final Kind[] COLOR_CHANNEL_KINDS = {Kind.COLOR_RED, Kind.COLOR_GREEN, Kind.COLOR_BLUE};
    /** Space left under the last row of a panel. */
    private static final int BODY_PAD = 2;
    private static final int[] COLOR_CHANNEL_COLORS = {0xFFFF5555, 0xFF55FF55, 0xFF5555FF};
    private static final Identifier LOGO = Identifier.of("bytephase", "bytephase-logo.png");
    private static final Set<String> EXPANDED = new HashSet<>();
    private static final List<Panel> PANELS = new ArrayList<>();

    private static final class Panel {
        final Category category;
        final Text title;
        final Text boldTitle;
        float x;
        float y;
        boolean open = true;
        double scroll;

        Panel(Category category) {
            this.category = category;
            this.title = Text.literal(category.display);
            this.boldTitle = Text.literal(category.display).formatted(Formatting.BOLD);
        }
    }

    private enum Kind { PANEL, HEADER, MODULE, EXPAND, BIND, BOOL, NUMBER, MODE,
        COLOR_RED, COLOR_GREEN, COLOR_BLUE }
    private static final class Hit {
        Kind kind;
        Panel panel;
        Module module;
        Setting<?> setting;
        int x;
        int y;
        int w;
        int h;

        Hit(Kind kind, Panel panel, Module module, Setting<?> setting,
            int x, int y, int w, int h) {
            set(kind, panel, module, setting, x, y, w, h);
        }

        void set(Kind kind, Panel panel, Module module, Setting<?> setting,
                 int x, int y, int w, int h) {
            this.kind = kind;
            this.panel = panel;
            this.module = module;
            this.setting = setting;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final Map<Category, List<Module>> cachedModules = new EnumMap<>(Category.class);
    private final Set<String> expanded = new HashSet<>(EXPANDED);
    private Module binding;
    private Panel draggingPanel;
    private float dragOffsetX;
    private float dragOffsetY;
    private NumberSetting draggingNumber;
    private Hit draggingNumberHit;
    private ColorSetting draggingColor;
    private Hit draggingColorHit;
    private int draggingColorShift = -1;
    private int panelWidth = PANEL_W;
    private int panelY;
    private int panelHeight;
    private int hitCount;
    /** Topmost panel under the cursor this frame; only it may show row hover. */
    private Panel hoverPanel;
    private int clipTop;
    private int clipBottom;
    private String search = "";
    private boolean searchFocused;
    private final Map<Category, List<Module>> filteredModules = new EnumMap<>(Category.class);
    private final Map<Category, Integer> contentHeights = new EnumMap<>(Category.class);
    private final dev.bytephase.module.render.UiSettings appearance;

    public ClickGuiScreen() {
        super(Text.literal("Bytephase"));
        initPanels();
        appearance = BytePhase.modules == null ? null : BytePhase.modules.get(dev.bytephase.module.render.UiSettings.class);
        if (BytePhase.modules != null) {
            for (Category category : Category.values()) {
                List<Module> list = new ArrayList<>(BytePhase.modules.byCategory(category));
                list.sort(Comparator.comparing(module -> module.getName().toLowerCase(Locale.ROOT)));
                cachedModules.put(category, List.copyOf(list));
            }
        }
    }

    private void initPanels() {
        if (!PANELS.isEmpty()) return;
        for (Category category : Category.values()) PANELS.add(new Panel(category));
    }

    /** Stores user layout separately from module settings in bytephase.json. */
    public static JsonObject saveLayout() {
        initStaticPanels();
        JsonObject layout = new JsonObject();
        JsonArray panels = new JsonArray();
        for (Panel panel : PANELS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("category", panel.category.name());
            entry.addProperty("x", panel.x);
            entry.addProperty("y", panel.y);
            entry.addProperty("open", panel.open);
            entry.addProperty("scroll", panel.scroll);
            panels.add(entry);
        }
        layout.add("panels", panels);
        JsonArray expanded = new JsonArray();
        for (String name : EXPANDED) expanded.add(name);
        layout.add("expanded", expanded);
        return layout;
    }

    /** Loads saved panel layout before a screen instance copies EXPANDED. */
    public static void loadLayout(JsonElement element) {
        if (element == null || !element.isJsonObject()) return;
        initStaticPanels();
        JsonObject layout = element.getAsJsonObject();
        if (layout.has("panels") && layout.get("panels").isJsonArray()) {
            for (JsonElement panelElement : layout.getAsJsonArray("panels")) {
                if (!panelElement.isJsonObject()) continue;
                JsonObject entry = panelElement.getAsJsonObject();
                if (!entry.has("category")) continue;
                Category category;
                try {
                    category = Category.valueOf(entry.get("category").getAsString());
                } catch (RuntimeException ignored) {
                    continue;
                }
                Panel panel = panelFor(category);
                if (panel == null) continue;
                panel.x = finiteFloat(entry, "x", panel.x);
                panel.y = finiteFloat(entry, "y", panel.y);
                panel.scroll = finiteDouble(entry, "scroll", panel.scroll);
                if (entry.has("open") && entry.get("open").isJsonPrimitive()) {
                    try { panel.open = entry.get("open").getAsBoolean(); }
                    catch (RuntimeException ignored) { }
                }
            }
        }
        EXPANDED.clear();
        if (layout.has("expanded") && layout.get("expanded").isJsonArray()) {
            for (JsonElement name : layout.getAsJsonArray("expanded")) {
                if (name.isJsonPrimitive() && name.getAsJsonPrimitive().isString()) {
                    EXPANDED.add(name.getAsString());
                }
            }
        }
    }

    private static void initStaticPanels() {
        if (!PANELS.isEmpty()) return;
        for (Category category : Category.values()) PANELS.add(new Panel(category));
    }

    private static Panel panelFor(Category category) {
        for (Panel panel : PANELS) if (panel.category == category) return panel;
        return null;
    }

    private static float finiteFloat(JsonObject object, String key, float fallback) {
        double value = finiteDouble(object, key, fallback);
        return (float) Math.max(-100000.0, Math.min(100000.0, value));
    }

    private static double finiteDouble(JsonObject object, String key, double fallback) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return fallback;
        try {
            double value = object.get(key).getAsDouble();
            return Double.isFinite(value) ? value : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    @Override
    protected void init() {
        positionPanels();
        hitCount = 0;
    }

    private void positionPanels() {
        int n = Category.values().length;
        int gap = 6;
        panelWidth = Math.max(92, Math.min(PANEL_W, (width - 24 - (n - 1) * gap) / n));
        int columns = Math.max(1, Math.min(n, (width - 16 + gap) / (panelWidth + gap)));
        int total = columns * panelWidth + (columns - 1) * gap;
        int startX = Math.max(0, (width - total) / 2);
        panelY = Math.min(40, Math.max(12, height / 8));
        int rows = (n + columns - 1) / columns;
        panelHeight = Math.max(HEADER_H + 20, (height - panelY - 28 - (rows - 1) * gap) / rows);
        if (PANELS.size() != n) {
            PANELS.clear();
            for (Category category : Category.values()) PANELS.add(new Panel(category));
        }
        for (int i = 0; i < PANELS.size(); i++) {
            Panel panel = PANELS.get(i);
            if (panel.x == 0 && panel.y == 0) {
                panel.x = startX + (i % columns) * (panelWidth + gap);
                panel.y = panelY + (i / columns) * (panelHeight + gap);
            }
            panel.x = MathHelper.clamp(panel.x, 0, Math.max(0, width - panelWidth));
            panel.y = MathHelper.clamp(panel.y, 0, Math.max(0, height - HEADER_H));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        hitCount = 0;
        contentHeights.clear();
        hoverPanel = null;
        for (int i = PANELS.size() - 1; i >= 0; i--) {
            Panel candidate = PANELS.get(i);
            int cx = Math.round(candidate.x), cy = Math.round(candidate.y);
            int ch = candidate.open ? Math.min(panelHeight, HEADER_H + contentHeight(candidate) + BODY_PAD) : HEADER_H;
            if (inside(mouseX, mouseY, cx, cy, panelWidth, ch)) {
                hoverPanel = candidate;
                break;
            }
        }
        context.fill(0, 0, width, height, 0x50000000);
        if (panelY >= 40) {
            int logoWidth = 120;
            int logoHeight = 40;
            context.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO,
                    (width - logoWidth) / 2, panelY - logoHeight, 0.0f, 0.0f,
                    logoWidth, logoHeight, 3040, 1024, 3040, 1024, 0xFFFFFFFF);
        }

        for (Panel panel : PANELS) drawPanel(context, panel, mouseX, mouseY);
        int searchX = searchX(), searchY = searchY();
        context.fill(searchX, searchY, searchX + 160, searchY + 20, Theme.PANEL);
        context.drawText(textRenderer, search.isEmpty() ? "Search modules..." : textRenderer.trimToWidth(search, 148),
                searchX + 6, searchY + 6, searchFocused ? Theme.accent() : Theme.MUTED, textShadow());
        if (binding != null) {
            String message = "Press a key for " + binding.getName() + " (Esc clears)";
            int w = textRenderer.getWidth(message) + 18;
            int x = (width - w) / 2;
            int y = height - 34;
            context.fill(x, y, x + w, y + 22, Theme.PANEL);
            context.drawText(textRenderer, message, x + 9, y + 7, Theme.accent(), textShadow());
        }
    }

    private void drawPanel(DrawContext ctx, Panel panel, int mouseX, int mouseY) {
        int x = Math.round(panel.x), y = Math.round(panel.y);
        int width = panelWidth;
        int bodyY = y + HEADER_H;
        int contentHeight = panel.open ? contentHeight(panel) : 0;
        int panelH = panel.open ? Math.min(panelHeight, HEADER_H + contentHeight + BODY_PAD) : HEADER_H;
        int bodyH = panelH - HEADER_H;
        // A single flat veil per panel: no rounding, borders, shadows or
        // icons, so the whole GUI costs a handful of fills.
        ctx.fill(x, y, x + width, y + panelH, Theme.PANEL_BG);
        addHit(Kind.PANEL, panel, null, null, x, y, width, panelH);

        ctx.drawText(textRenderer, appearance == null || appearance.boldHeaders() ? panel.boldTitle : panel.title,
                x + 4, y + (HEADER_H - 8) / 2, Theme.WHITE, textShadow());
        addHit(Kind.HEADER, panel, null, null, x, y, width, HEADER_H);
        if (!panel.open) return;

        List<Module> modules = modules(panel.category);
        int maxScroll = Math.max(0, contentHeight - (bodyH - 4));
        panel.scroll = MathHelper.clamp(panel.scroll, 0, maxScroll);

        clipTop = bodyY;
        clipBottom = y + panelH - 2;
        ctx.enableScissor(x + 1, bodyY, x + width - 1, y + panelH - 2);
        int cursorY = bodyY - (int) panel.scroll;
        for (Module module : modules) {
            int moduleHeight = moduleHeight(module);
            if (cursorY + moduleHeight >= bodyY && cursorY < y + panelH) {
                drawModule(ctx, panel, module, x + PAD, cursorY, width - PAD * 2, mouseX, mouseY);
            }
            cursorY += moduleHeight + GAP;
        }
        ctx.disableScissor();
        if (maxScroll > 0) {
            int trackX = x + width - 4;
            int trackY = bodyY + 4;
            int trackH = bodyH - 10;
            int thumbH = Math.max(14, trackH * trackH / Math.max(1, contentHeight));
            int thumbY = trackY + (int) ((trackH - thumbH) * (panel.scroll / maxScroll));
            ctx.fill(trackX, trackY, trackX + 2, trackY + trackH, 0x22FFFFFF);
            ctx.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x88FFFFFF);
        }
    }

    private void drawModule(DrawContext ctx, Panel panel, Module module, int x, int y, int w,
                            int mouseX, int mouseY) {
        boolean isExpanded = expanded.contains(module.getName());
        boolean hovered = hoverPanel == panel && mouseY >= clipTop && mouseY < clipBottom
                && inside(mouseX, mouseY, x, y, w, ROW_H);
        boolean enabled = module.isEnabled();
        int totalH = moduleHeight(module);
        if (isExpanded) {
            ctx.fill(x, y + ROW_H, x + w, y + totalH, Theme.SETTINGS_BG);
        }
        if (enabled) {
            ctx.fill(x, y, x + w, y + ROW_H, 0x90000000 | (Theme.accent() & 0xFFFFFF));
        }
        if (hovered) {
            ctx.fill(x, y, x + w, y + ROW_H, Theme.ROW_HOVER);
        }
        int textY = y + (ROW_H - 8) / 2;
        String name = textRenderer.trimToWidth(module.getName(), Math.max(12, w - 18));
        ctx.drawText(textRenderer, name, x + 4, textY, enabled ? Theme.WHITE : Theme.text(), textShadow());
        addHit(Kind.MODULE, panel, module, null, x, y, w, ROW_H);
        // Plain "+" / "-" marker; it is also the click target that expands
        // the module's settings (added after MODULE so it wins the hit test).
        String marker = isExpanded ? "-" : "+";
        ctx.drawText(textRenderer, marker, x + w - 4 - textRenderer.getWidth(marker), textY, Theme.text(), textShadow());
        addHit(Kind.EXPAND, panel, module, null, x + w - 16, y, 16, ROW_H);

        if (!isExpanded) return;
        int sx = x + 8;
        int sw = w - 16;
        int sy = y + ROW_H + 3;
        ctx.fill(x + 7, sy - 2, x + w - 7, sy - 1, Theme.DIVIDER);
        drawBind(ctx, panel, module, sx, sy, sw);
        sy += 20;
        for (Setting<?> setting : module.getSettings()) {
            int h = settingHeight(setting);
            drawSetting(ctx, panel, module, setting, sx, sy, sw, h);
            sy += h;
        }
    }

    private void drawBind(DrawContext ctx, Panel panel, Module module, int x, int y, int w) {
        ctx.drawText(textRenderer, "Bind", x, y + 5, Theme.MUTED, textShadow());
        String key = binding == module ? "press key" : Keybinds.getKeyName(module);
        int keyWidth = textRenderer.getWidth(key);
        ctx.drawText(textRenderer, key, x + w - keyWidth, y + 5,
                binding == module ? Theme.accent() : Theme.text(), textShadow());
        addHit(Kind.BIND, panel, module, null, x, y, w, 18);
    }

    private void drawSetting(DrawContext ctx, Panel panel, Module module, Setting<?> setting,
                             int x, int y, int w, int h) {
        ctx.drawText(textRenderer, textRenderer.trimToWidth(setting.getName(), w - 40), x, y + 6,
                Theme.text(), textShadow());
        if (setting instanceof BooleanSetting bool) {
            boolean value = bool.getValue();
            // Flat checkbox: dark square, filled with the accent when on.
            ctx.fill(x + w - 11, y + 5, x + w - 2, y + 14, Theme.OFF);
            if (value) {
                ctx.fill(x + w - 10, y + 6, x + w - 3, y + 13, Theme.accent());
            }
            addHit(Kind.BOOL, panel, module, setting, x, y, w, h);
        } else if (setting instanceof NumberSetting number) {
            String value = number.isFloat() ? formatNumber(number.getValue())
                    : String.valueOf(number.asInt());
            int valueW = textRenderer.getWidth(value);
            ctx.drawText(textRenderer, value, x + w - valueW, y + 6, Theme.accent(), textShadow());
            int trackY = y + h - 5;
            int trackW = w;
            ctx.fill(x, trackY, x + trackW, trackY + 2, Theme.OFF);
            double range = number.getMax() - number.getMin();
            double fraction = range <= 0 ? 0 : MathHelper.clamp((number.getValue() - number.getMin()) / range, 0, 1);
            int fill = (int) Math.round(trackW * fraction);
            if (fill > 0) ctx.fill(x, trackY, x + fill, trackY + 2, Theme.accent());
            addHit(Kind.NUMBER, panel, module, setting, x, y, w, h);
        } else if (setting instanceof ModeSetting mode) {
            String value = mode.getValue();
            String shown = textRenderer.trimToWidth(value, Math.max(10, w / 2));
            ctx.drawText(textRenderer, shown, x + w - textRenderer.getWidth(shown), y + 6,
                    Theme.accent(), textShadow());
            addHit(Kind.MODE, panel, module, setting, x, y, w, h);
        } else if (setting instanceof ColorSetting color) {
            int swatchX = x + w - 16;
            ctx.fill(swatchX - 1, y + 3, swatchX + 13, y + 17, 0x99FFFFFF);
            ctx.fill(swatchX, y + 4, swatchX + 12, y + 16, 0xFF000000 | color.rgb());
            int red = color.red(), green = color.green(), blue = color.blue();
            for (int i = 0; i < 3; i++) {
                int cy = y + 21 + i * 9;
                ctx.drawText(textRenderer, COLOR_CHANNEL_LABELS[i], x, cy,
                        Theme.MUTED, textShadow());
                int barX = x + 10;
                int barW = w - 10;
                ctx.fill(barX, cy + 4, barX + barW, cy + 7, Theme.OFF);
                int channel = i == 0 ? red : i == 1 ? green : blue;
                int fillW = Math.round(barW * channel / 255.0f);
                if (fillW > 0) ctx.fill(barX, cy + 4, barX + fillW, cy + 7, COLOR_CHANNEL_COLORS[i]);
                ctx.fill(barX + fillW - 1, cy + 2, barX + fillW + 2, cy + 9, 0xFFD7D7E2);
                addHit(COLOR_CHANNEL_KINDS[i], panel, module, setting, barX, cy + 1, barW, 9);
            }
        }
    }

    /** Avoid Formatter setup and locale work for every visible slider each frame. */
    private static String formatNumber(double value) {
        boolean negative = value < 0.0;
        double absolute = Math.abs(value);
        long whole = (long) absolute;
        int fraction = (int) Math.round((absolute - whole) * 100.0);
        if (fraction == 100) {
            whole++;
            fraction = 0;
        }
        return (negative ? "-" : "") + whole + "." + (fraction < 10 ? "0" : "") + fraction;
    }

    private void addHit(Kind kind, Panel panel, Module module, Setting<?> setting,
                        int x, int y, int w, int h) {
        if (hitCount == hits.size()) {
            hits.add(new Hit(kind, panel, module, setting, x, y, w, h));
        } else {
            hits.get(hitCount).set(kind, panel, module, setting, x, y, w, h);
        }
        hitCount++;
    }

    private int moduleHeight(Module module) {
        if (!expanded.contains(module.getName())) return ROW_H;
        int height = ROW_H + 21;
        for (Setting<?> setting : module.getSettings()) height += settingHeight(setting);
        return height + 3;
    }

    private int panelHeight(Panel panel) {
        return Math.min(panelHeight, HEADER_H + contentHeight(panel) + BODY_PAD);
    }

    private int contentHeight(Panel panel) {
        Integer cached = contentHeights.get(panel.category);
        if (cached != null) return cached;
        int contentHeight = 4;
        for (Module module : modules(panel.category)) {
            contentHeight += moduleHeight(module) + GAP;
        }
        contentHeights.put(panel.category, contentHeight);
        return contentHeight;
    }

    private boolean textShadow() {
        return appearance != null && appearance.textShadow();
    }

    private static int settingHeight(Setting<?> setting) {
        if (setting instanceof NumberSetting) return 26;
        if (setting instanceof ColorSetting) return 50;
        return 20;
    }

    private List<Module> modules(Category category) {
        return search.isEmpty() ? cachedModules.getOrDefault(category, List.of())
                : filteredModules.getOrDefault(category, List.of());
    }

    private int searchX() { return Math.max(0, (width - 160) / 2); }
    private int searchY() { return Math.max(0, height - 24); }

    private void updateSearch(String value) {
        search = value;
        contentHeights.clear();
        filteredModules.clear();
        String needle = search.toLowerCase(Locale.ROOT);
        for (Category category : Category.values()) {
            filteredModules.put(category, cachedModules.getOrDefault(category, List.of()).stream()
                    .filter(module -> module.getName().toLowerCase(Locale.ROOT).contains(needle)).toList());
        }
        // New result lists should start at the top rather than inheriting an
        // unrelated panel's previous scroll offset.
        for (Panel panel : PANELS) panel.scroll = 0;
        draggingNumber = null;
        draggingNumberHit = null;
        draggingColor = null;
        draggingColorHit = null;
        draggingColorShift = -1;
        hitCount = 0;
    }


    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int button = click.buttonInfo().button();
        double mx = click.x(), my = click.y();
        if (binding != null) return true;
        if (button == 0 && inside(mx, my, searchX(), searchY(), 160, 20)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;
        for (int i = hitCount - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(mx, my)) continue;
            if (hit.kind != Kind.PANEL && hit.kind != Kind.HEADER
                    && (!hit.panel.open || !inside(mx, my, Math.round(hit.panel.x) + 1,
                    Math.round(hit.panel.y) + HEADER_H, panelWidth - 2,
                    panelHeight(hit.panel) - HEADER_H - 2))) continue;
            if (PANELS.get(PANELS.size() - 1) != hit.panel) {
                PANELS.remove(hit.panel);
                PANELS.add(hit.panel);
            }
            switch (hit.kind) {
                case PANEL -> { return true; }
                case HEADER -> {
                    if (button == 0) {
                        draggingPanel = hit.panel;
                        dragOffsetX = (float) (mx - hit.panel.x);
                        dragOffsetY = (float) (my - hit.panel.y);
                    } else if (button == 1) {
                        hit.panel.open = !hit.panel.open;
                        if (BytePhase.config != null) BytePhase.config.requestSave();
                    }
                }
                case MODULE -> { if (button == 0) hit.module.toggle(); }
                case EXPAND -> {
                    if (button == 0) {
                        if (!expanded.remove(hit.module.getName())) expanded.add(hit.module.getName());
                        contentHeights.clear();
                        EXPANDED.clear();
                        EXPANDED.addAll(expanded);
                    }
                }
                case BIND -> { if (button == 0) binding = hit.module; }
                case BOOL -> { if (button == 0) ((BooleanSetting) hit.setting).toggle(); }
                case NUMBER -> {
                    if (button == 0) {
                        draggingNumber = (NumberSetting) hit.setting;
                        // Render reuses and rewrites hit objects every frame. Keep
                        // the original slider geometry and setting for the drag.
                        draggingNumberHit = new Hit(hit.kind, hit.panel, hit.module, hit.setting,
                                hit.x, hit.y, hit.w, hit.h);
                        setNumberFromMouse(hit, mx);
                    }
                }
                case MODE -> {
                    if (button == 0 || button == 1) {
                        ModeSetting mode = (ModeSetting) hit.setting;
                        String[] modes = mode.getModes();
                        int offset = button == 1 ? -1 : 1;
                        mode.setValue(modes[Math.floorMod(mode.getIndex() + offset, modes.length)]);
                    }
                }
                case COLOR_RED, COLOR_GREEN, COLOR_BLUE -> {
                    if (button == 0) {
                        draggingColor = (ColorSetting) hit.setting;
                        draggingColorHit = new Hit(hit.kind, hit.panel, hit.module, hit.setting,
                                hit.x, hit.y, hit.w, hit.h);
                        draggingColorShift = hit.kind == Kind.COLOR_RED ? 16
                                : hit.kind == Kind.COLOR_GREEN ? 8 : 0;
                        setColorFromMouse(hit, mx, draggingColorShift);
                    }
                }
            }
            // Module.setEnabled already persists toggle changes. Avoid a
            // second synchronous disk write for the same click.
            if (hit.kind != Kind.PANEL && hit.kind != Kind.HEADER
                    && hit.kind != Kind.MODULE && BytePhase.config != null) {
                BytePhase.config.requestSave();
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (draggingPanel != null) {
            draggingPanel.x = MathHelper.clamp((float) (click.x() - dragOffsetX),
                    0, Math.max(0, width - panelWidth));
            draggingPanel.y = MathHelper.clamp((float) (click.y() - dragOffsetY),
                    0, Math.max(0, height - HEADER_H));
            return true;
        }
        if (draggingNumber != null && draggingNumberHit != null) {
            setNumberFromMouse(draggingNumberHit, click.x());
            return true;
        }
        if (draggingColor != null && draggingColorHit != null && draggingColorShift >= 0) {
            setColorFromMouse(draggingColorHit, click.x(), draggingColorShift);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if ((draggingNumber != null || draggingColor != null || draggingPanel != null)
                && BytePhase.config != null) {
            BytePhase.config.requestSave();
        }
        draggingPanel = null;
        draggingNumber = null;
        draggingNumberHit = null;
        draggingColor = null;
        draggingColorHit = null;
        draggingColorShift = -1;
        return super.mouseReleased(click);
    }

    private static void setNumberFromMouse(Hit hit, double mouseX) {
        NumberSetting number = (NumberSetting) hit.setting;
        double fraction = MathHelper.clamp((mouseX - hit.x) / Math.max(1.0, hit.w), 0.0, 1.0);
        double step = Math.max(1.0e-4, number.getStep());
        double raw = number.getMin() + fraction * (number.getMax() - number.getMin());
        double snapped = number.getMin() + Math.round((raw - number.getMin()) / step) * step;
        number.setValue(MathHelper.clamp(snapped, number.getMin(), number.getMax()));
    }

    private static void setColorFromMouse(Hit hit, double mouseX, int shift) {
        ColorSetting color = (ColorSetting) hit.setting;
        double fraction = MathHelper.clamp((mouseX - hit.x) / Math.max(1.0, hit.w), 0.0, 1.0);
        color.setChannel(shift, (int) Math.round(fraction * 255.0));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        // PANELS is ordered bottom to top because a click promotes the active
        // panel to the end. Scroll the topmost overlapping panel first too.
        for (int index = PANELS.size() - 1; index >= 0; index--) {
            Panel panel = PANELS.get(index);
            int x = Math.round(panel.x), y = Math.round(panel.y);
            if (inside(mouseX, mouseY, x, y, panelWidth, panelHeight(panel)) && panel.open) {
                panel.scroll = Math.max(0.0, panel.scroll - vertical * 26.0);
                // Persist the scroll position when the GUI closes. Saving the
                // complete module configuration on every wheel event causes
                // synchronous JSON serialization and disk I/O while scrolling.
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (binding != null) {
            Keybinds.set(binding, input.key() == GLFW.GLFW_KEY_ESCAPE
                    ? GLFW.GLFW_KEY_UNKNOWN : input.key());
            binding = null;
            if (BytePhase.config != null) BytePhase.config.requestSave();
            return true;
        }
        if (searchFocused) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
            } else if (input.key() == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                updateSearch((input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0
                        ? "" : search.substring(0, search.offsetByCodePoints(search.length(), -1)));
            } else if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
            }
            return true;
        }
        if (Keybinds.matchesClickGui(input)) {
            Keybinds.suppressNextClickGui();
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (searchFocused && input.isValidChar() && search.length() < 64) {
            updateSearch(search + input.asString());
            return true;
        }
        return searchFocused || super.charTyped(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // The GUI supplies its own dim layer.
    }

    @Override
    public void removed() {
        EXPANDED.clear();
        EXPANDED.addAll(expanded);
        if (BytePhase.config != null) BytePhase.config.save();
        super.removed();
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
