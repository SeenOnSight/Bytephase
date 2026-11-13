package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.ui.RenderGroups;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;

public final class ArrayListView extends Module {
    private final ColorSetting color = add(new ColorSetting("Color", 0xFF6466E9));
    private final BooleanSetting compactRender = add(new BooleanSetting("CompactRender", true));
    private final NumberSetting maxRows = add(new NumberSetting("MaxRows", 12, 3, 40, 1));
    private List<Entry> cachedEntries = List.of();
    private List<Module> cachedEnabled;
    private final java.util.Map<Module, String> cachedText = new java.util.IdentityHashMap<>();
    private boolean cachedCompact;
    private net.minecraft.client.font.TextRenderer cachedRenderer;
    private String cachedFont = "";

    private record Entry(String name, int width) { }

    public ArrayListView() {
        super("ArrayList", Category.RENDER, "Shows enabled modules (top right)");
    }

    @Override
    public void onTick() {
        refreshEntries();
    }

    private void refreshEntries() {
        MinecraftClient mc = BytePhase.mc;
        if (mc == null) {
            return;
        }
        List<Module> enabled = BytePhase.modules.enabled();
        boolean changed = enabled != cachedEnabled || cachedCompact != compactRender.getValue()
            || cachedRenderer != mc.textRenderer
            || !cachedFont.equals(dev.bytephase.render.ClientFonts.hudName());
        if (!changed) {
            for (Module module : enabled) {
                String text = module.getHudText();
                if (!text.equals(cachedText.get(module))) {
                    changed = true;
                    break;
                }
            }
        }
        if (changed) {
            List<Entry> entries = new ArrayList<>(enabled.size());
            java.util.Map<String, Integer> groups = new java.util.LinkedHashMap<>();
            cachedText.clear();
            for (Module module : enabled) {
                String name = module.getHudText();
                cachedText.put(module, name);
                if (module == this) continue;
                if (name.isEmpty()) continue;
                if (compactRender.getValue() && module.getCategory() == Category.RENDER) {
                    groups.merge(RenderGroups.of(module), 1, Integer::sum);
                } else {
                    entries.add(new Entry(name, mc.textRenderer.getWidth(name)));
                }
            }
            groups.forEach((group, count) -> {
                String name = group + " [" + count + "]";
                entries.add(new Entry(name, mc.textRenderer.getWidth(name)));
            });
            entries.sort(Comparator.comparingInt(Entry::width).reversed());
            cachedEntries = List.copyOf(entries);
            cachedEnabled = enabled;
            cachedCompact = compactRender.getValue();
            cachedRenderer = mc.textRenderer;
            cachedFont = dev.bytephase.render.ClientFonts.hudName();
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        MinecraftClient mc = BytePhase.mc;
        if (mc == null) return;
        if (cachedEnabled != BytePhase.modules.enabled() || cachedCompact != compactRender.getValue()
                || cachedRenderer != mc.textRenderer
                || !cachedFont.equals(dev.bytephase.render.ClientFonts.hudName())) {
            refreshEntries();
        }
        int y = 2;
        int limit = Math.min(maxRows.asInt(), Math.max(1, (mc.getWindow().getScaledHeight() - 8) / 10));
        int visible = Math.min(cachedEntries.size(), limit);
        boolean overflow = cachedEntries.size() > visible;
        if (overflow) visible = Math.max(0, visible - 1);
        for (int i = 0; i < visible; i++) {
            Entry entry = cachedEntries.get(i);
            String name = entry.name();
            int width = entry.width();
            int x = mc.getWindow().getScaledWidth() - width - 4;
            context.fill(x - 2, y - 1, x + width + 2, y + 9, 0x90000000);
            context.drawTextWithShadow(mc.textRenderer, name, x, y, color.getValue());
            y += 10;
        }
        if (overflow) {
            String text = "+ " + (cachedEntries.size() - visible) + " more";
            int x = mc.getWindow().getScaledWidth() - mc.textRenderer.getWidth(text) - 4;
            context.drawTextWithShadow(mc.textRenderer, text, x, y, color.getValue());
        }
    }
}
