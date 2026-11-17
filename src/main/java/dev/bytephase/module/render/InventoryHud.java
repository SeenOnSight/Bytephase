package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;

/** A live, read-only inventory grid that does not open or interact with containers. */
public final class InventoryHud extends Module {
    private final BooleanSetting hotbar = add(new BooleanSetting("IncludeHotbar", false));
    private final BooleanSetting counts = add(new BooleanSetting("ItemCounts", true));
    private final BooleanSetting empty = add(new BooleanSetting("ShowEmptySlots", true));
    private final BooleanSetting title = add(new BooleanSetting("Title", true));
    private final ModeSetting layout = add(new ModeSetting("Layout", new String[]{"Horizontal", "Vertical"}, "Horizontal"));
    private final NumberSetting opacity = add(new NumberSetting("BackgroundOpacity", 65, 0, 100, 1));
    public InventoryHud() { super("InventoryHUD", Category.RENDER, "Shows your inventory contents while you play"); }
    @Override public void onRender2D(DrawContext context, float delta) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) return;
        int columns = layout.getValue().equals("Vertical") ? 3 : 9;
        int count = hotbar.getValue() ? 36 : 27, rows = count / columns;
        int width = columns * 18 + 8, height = rows * 18 + 8 + (title.getValue() ? 12 : 0);
        int x = mc.getWindow().getScaledWidth() - width - 8;
        int y = Math.max(8, Math.min(mc.getWindow().getScaledHeight() - height - 8, mc.getWindow().getScaledHeight() / 2 + 20));
        context.fill(x, y, x + width, y + height, (Math.round(opacity.asFloat() * 2.55f) << 24) | 0x101020);
        if (title.getValue()) context.drawText(mc.textRenderer, "Inventory", x + 4, y + 3, 0xFFF0F1FF, false);
        int top = y + 4 + (title.getValue() ? 12 : 0);
        for (int index = 0; index < count; index++) {
            int slot = index < 27 ? index + 9 : index - 27;
            var stack = mc.player.getInventory().getStack(slot);
            int sx = x + 4 + index % columns * 18, sy = top + index / columns * 18;
            if (empty.getValue() || !stack.isEmpty()) context.fill(sx, sy, sx + 16, sy + 16, 0x302E3040);
            if (stack.isEmpty()) continue;
            context.drawItem(stack, sx, sy);
            if (counts.getValue()) context.drawStackOverlay(mc.textRenderer, stack, sx, sy);
        }
    }
}
