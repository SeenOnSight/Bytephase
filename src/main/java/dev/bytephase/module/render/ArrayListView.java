package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.ColorSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;

public final class ArrayListView extends Module {
    private final ColorSetting color = add(new ColorSetting("Color", 0xFF6466E9));
    private List<Entry> cachedEntries = List.of();
    private List<Module> cachedEnabled;
    private final java.util.Map<Module, String> cachedText = new java.util.IdentityHashMap<>();

    private record Entry(Module module, String name, int width) { }

    public ArrayListView() {
        super("ArrayList", Category.RENDER, "Shows enabled modules (top right)");
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        MinecraftClient mc = BytePhase.mc;
        if (mc == null) {
            return;
        }
        List<Module> enabled = BytePhase.modules.enabled();
        boolean changed = enabled != cachedEnabled;
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
            cachedText.clear();
            for (Module module : enabled) {
                String name = module.getHudText();
                cachedText.put(module, name);
                entries.add(new Entry(module, name, mc.textRenderer.getWidth(name)));
            }
            entries.sort(Comparator.comparingInt(Entry::width).reversed());
            cachedEntries = List.copyOf(entries);
            cachedEnabled = enabled;
        }
        int y = 2;
        for (Entry entry : cachedEntries) {
            String name = entry.name();
            int width = entry.width();
            int x = mc.getWindow().getScaledWidth() - width - 4;
            context.fill(x - 2, y - 1, x + width + 2, y + 9, 0x90000000);
            context.drawTextWithShadow(mc.textRenderer, name, x, y, color.getValue());
            y += 10;
        }
    }
}
