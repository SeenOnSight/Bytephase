package dev.bytephase.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.bytephase.BytePhase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;

/** Shared placement and scaling for screen overlays. Positions follow the viewport size. */
public final class HudLayout {
    private static final Map<String, Element> ELEMENTS = new LinkedHashMap<>();
    private static Scope current;
    private static final int MAX_ELEMENTS = 256;

    public static final class Element {
        public final String key;
        public float baseX, baseY, width = 80, height = 20;
        public float offsetX, offsetY, scale = 1;
        public long lastSeen;
        private long lastMeasured;
        private int viewportWidth, viewportHeight;
        private boolean measured;
        private Element(String key) { this.key = key; }
        public String label() { return key.substring(key.indexOf(':') + 1); }
        public float x() { return clamp(baseX + offsetX * screenWidth(), 0, Math.max(0, screenWidth() - width * scale)); }
        public float y() { return clamp(baseY + offsetY * screenHeight(), 0, Math.max(0, screenHeight() - height * scale)); }
        public float right() { return x() + width * scale; }
        public float bottom() { return y() + height * scale; }
        public boolean contains(double x, double y) { return x >= x() && x <= right() && y >= y() && y <= bottom(); }
    }

    private static final class Scope {
        final Element element;
        final Scope previous;
        float anchorX, anchorY, dx, dy;
        final float scale;
        final boolean measure;
        boolean drew;
        boolean authoritative;
        boolean suppressCapture;
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        Scope(Element e, Scope previous) {
            element = e; this.previous = previous;
            anchorX = e.baseX; anchorY = e.baseY;
            dx = e.x() - anchorX; dy = e.y() - anchorY; scale = e.scale;
            measure = !e.measured || BytePhase.mc.currentScreen instanceof dev.bytephase.ui.HudEditorScreen
                || System.nanoTime() - e.lastMeasured >= 250_000_000L
                || e.viewportWidth != screenWidth() || e.viewportHeight != screenHeight();
        }
    }

    private HudLayout() { }
    private static int screenWidth() { return BytePhase.mc == null ? 1 : BytePhase.mc.getWindow().getScaledWidth(); }
    private static int screenHeight() { return BytePhase.mc == null ? 1 : BytePhase.mc.getWindow().getScaledHeight(); }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    public static boolean recording() { return current != null && !current.suppressCapture; }
    public static boolean measuring() { return recording() && current.measure; }
    public static void markDrawn() { if (recording()) current.drew = true; }

    public static void draw(String key, DrawContext context, Runnable action) {
        switch (key) {
            case "module:ArmorHUD" -> draw(key, context, 4, screenHeight() - 67, 76, 28, action);
            case "module:Effects" -> draw(key, context, 4, screenHeight() / 2f, 130, 30, action);
            case "module:TargetHUD" -> draw(key, context, (screenWidth() - 150) / 2f, (screenHeight() - 47) * 0.7f, 150, 47, action);
            case "module:Crosshair", "module:Hitmarker" -> draw(key, context, screenWidth() / 2f - 12, screenHeight() / 2f - 12, 24, 24, action);
            default -> draw(key, context, -1, -1, 0, 0, action);
        }
    }

    public static void draw(String key, DrawContext context, float x, float y, float w, float h, Runnable action) {
        draw(key, context, x, y, w, h, true, action);
    }
    public static void drawImported(String key, DrawContext context, Runnable action) {
        draw(key, context, -1, -1, 0, 0, false, action);
    }
    private static void draw(String key, DrawContext context, float x, float y, float w, float h, boolean transform, Runnable action) {
        if (BytePhase.mc == null || context == null || key == null
                || dev.bytephase.render.capture.StreamProof.isCleanFrame()) {
            action.run(); return;
        }
        Element element = ELEMENTS.get(key);
        if (element == null) {
            if (ELEMENTS.size() >= MAX_ELEMENTS) { action.run(); return; }
            element = new Element(key);
            ELEMENTS.put(key, element);
        }
        if (w > 0 && h > 0 && !element.measured) {
            element.baseX = x; element.baseY = y; element.width = w; element.height = h;
        }
        Scope scope = new Scope(element, current);
        current = scope;
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        if (transform) {
            matrices.translate(scope.anchorX + scope.dx, scope.anchorY + scope.dy);
            matrices.scale(scope.scale, scope.scale);
            matrices.translate(-scope.anchorX, -scope.anchorY);
        }
        try { action.run(); }
        finally {
            matrices.popMatrix();
            current = scope.previous;
            if (!scope.authoritative && !key.startsWith("vanilla:") && Float.isFinite(scope.minX) && scope.maxX > scope.minX && scope.maxY > scope.minY) {
                element.baseX = scope.minX; element.baseY = scope.minY;
                element.width = scope.maxX - scope.minX; element.height = scope.maxY - scope.minY;
                element.measured = true;
                element.lastMeasured = System.nanoTime();
                element.viewportWidth = screenWidth(); element.viewportHeight = screenHeight();
            } else if (w > 0 && h > 0 && (!element.measured || key.startsWith("vanilla:"))
                    && (!key.startsWith("vanilla:") || scope.drew)) {
                element.baseX = x; element.baseY = y; element.width = w; element.height = h;
            }
            if (scope.drew || !key.startsWith("vanilla:") && w > 0 && h > 0) element.lastSeen = System.nanoTime();
        }
    }

    public static dev.bytephase.engine.categories.visuals.ShowHUD.HudScale engineScale(String name, float x, float y, float width, float height) {
        Scope scope = current;
        if (name == null || width <= 0 || height <= 0 || BytePhase.mc == null
                || dev.bytephase.render.capture.StreamProof.isCleanFrame()) return null;
        float logicalWidth = dev.bytephase.engine.render.BytephaseRoundedUtils.scaledWidth;
        float ratio = logicalWidth > 0 ? screenWidth() / logicalWidth : 1;
        String key = switch (name) {
            case "spotify" -> "module:SpotifyHUD";
            case "totems" -> "module:TotemCounter";
            default -> "native:" + name;
        };
        Element e = ELEMENTS.get(key);
        if (e == null) {
            if (ELEMENTS.size() >= MAX_ELEMENTS) return null;
            e = new Element(key); ELEMENTS.put(key, e);
        }
        e.baseX = x * ratio; e.baseY = y * ratio; e.width = width * ratio; e.height = height * ratio;
        e.measured = true; e.lastMeasured = System.nanoTime(); e.lastSeen = e.lastMeasured;
        if (scope != null) { scope.authoritative = true; scope.suppressCapture = true; }
        return dev.bytephase.engine.categories.visuals.ShowHUD.HudScale.nativeLayout(
            x, y, (e.x() - e.baseX) / ratio, (e.y() - e.baseY) / ratio, e.scale);
    }

    /** Collect bounds after the overlay's own text/item transforms have been applied. */
    public static void record(DrawContext context, float x, float y, float width, float height) {
        Scope scope = current;
        if (scope == null || scope.suppressCapture || width <= 0 || height <= 0) return;
        scope.drew = true;
        if (!scope.measure) return;
        var m = context.getMatrices();
        for (int corner = 0; corner < 4; corner++) {
            float px = x + ((corner & 1) == 0 ? 0 : width);
            float py = y + ((corner & 2) == 0 ? 0 : height);
            float sx = (m.m00() * px + m.m10() * py + m.m20() - scope.anchorX - scope.dx) / scope.scale + scope.anchorX;
            float sy = (m.m01() * px + m.m11() * py + m.m21() - scope.anchorY - scope.dy) / scope.scale + scope.anchorY;
            scope.minX = Math.min(scope.minX, sx); scope.minY = Math.min(scope.minY, sy);
            scope.maxX = Math.max(scope.maxX, sx); scope.maxY = Math.max(scope.maxY, sy);
        }
    }

    public static List<Element> visible() {
        long cutoff = System.nanoTime() - 500_000_000L;
        return ELEMENTS.values().stream().filter(e -> e.lastSeen >= cutoff).toList();
    }

    public static void move(Element e, float x, float y) {
        e.offsetX = (clamp(x, 0, Math.max(0, screenWidth() - e.width * e.scale)) - e.baseX) / Math.max(1, screenWidth());
        e.offsetY = (clamp(y, 0, Math.max(0, screenHeight() - e.height * e.scale)) - e.baseY) / Math.max(1, screenHeight());
        changed();
    }

    public static void resize(Element e, float scale) {
        float x = e.x(), y = e.y();
        e.scale = clamp(scale, 0.25f, 3);
        move(e, x, y);
    }

    public static void reset(Element e) { e.offsetX = 0; e.offsetY = 0; e.scale = 1; changed(); }
    public static void resetAll() { ELEMENTS.values().forEach(e -> { e.offsetX = 0; e.offsetY = 0; e.scale = 1; }); changed(); }
    private static void changed() { if (BytePhase.config != null) BytePhase.config.requestSave(); }

    public static JsonObject save() {
        JsonObject values = new JsonObject();
        for (Element e : ELEMENTS.values()) {
            if (e.offsetX == 0 && e.offsetY == 0 && e.scale == 1) continue;
            JsonObject value = new JsonObject();
            value.addProperty("x", e.offsetX); value.addProperty("y", e.offsetY); value.addProperty("scale", e.scale);
            values.add(e.key, value);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 1); root.add("elements", values);
        return root;
    }

    public static void load(JsonElement json) {
        if (json == null || !json.isJsonObject()) throw new IllegalArgumentException("Invalid HUD layout");
        JsonObject root = json.getAsJsonObject();
        if (!root.has("version") || root.get("version").getAsInt() != 1
                || !root.has("elements") || !root.get("elements").isJsonObject()) throw new IllegalArgumentException("Invalid HUD layout");
        JsonObject values = root.getAsJsonObject("elements");
        if (values.size() > MAX_ELEMENTS) throw new IllegalArgumentException("Too many HUD positions");
        Map<String, float[]> parsed = new LinkedHashMap<>();
        for (var item : values.entrySet()) {
            if (item.getKey().length() > 128 || !item.getValue().isJsonObject()) throw new IllegalArgumentException("Invalid HUD position");
            JsonObject value = item.getValue().getAsJsonObject();
            float x = value.get("x").getAsFloat(), y = value.get("y").getAsFloat(), scale = value.get("scale").getAsFloat();
            if (!Float.isFinite(x) || !Float.isFinite(y) || Math.abs(x) > 2 || Math.abs(y) > 2
                    || !Float.isFinite(scale) || scale < 0.25 || scale > 3) throw new IllegalArgumentException("Invalid HUD size");
            parsed.put(item.getKey(), new float[]{x, y, scale});
        }
        ELEMENTS.values().forEach(e -> { e.offsetX = 0; e.offsetY = 0; e.scale = 1; });
        parsed.forEach((key, value) -> {
            Element e = ELEMENTS.computeIfAbsent(key, Element::new);
            e.offsetX = value[0]; e.offsetY = value[1]; e.scale = value[2];
        });
    }
}
