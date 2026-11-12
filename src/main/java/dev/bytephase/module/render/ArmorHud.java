package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.Render2D;
import dev.bytephase.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * Bottom-left armor readout.
 *
 * <p>Slots are 20px apart and every value is scaled to 0.7 so three-digit
 * durability numbers cannot run into the next slot. An optional bar under each
 * icon shows durability at a glance; the text colour and bar both go
 * green → amber → red as the piece wears down.</p>
 */
public final class ArmorHud extends Module {
    private static final int SLOT_SPACING = 20;
    private static final int ICON_SIZE = 16;
    private static final float TEXT_SCALE = 0.7f;
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private final BooleanSetting durability = add(new BooleanSetting("Durability", true));
    private final BooleanSetting bars = add(new BooleanSetting("Bars", true));
    private final BooleanSetting hideVanilla = add(new BooleanSetting("HideVanilla", true));

    public ArmorHud() {
        super("ArmorHUD", Category.RENDER, "Displays armor and durability");
    }

    public boolean hideVanilla() {
        return hideVanilla.getValue();
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) {
            return;
        }
        int baseX = 4;
        int baseY = mc.getWindow().getScaledHeight() - 58;
        for (int index = 0; index < SLOTS.length; index++) {
            ItemStack stack = mc.player.getEquippedStack(SLOTS[index]);
            if (stack.isEmpty()) {
                continue;
            }
            int x = baseX + index * SLOT_SPACING;
            context.drawItem(stack, x, baseY);
            if (!durability.getValue() || stack.getMaxDamage() <= 0) {
                continue;
            }
            int max = stack.getMaxDamage();
            int remaining = Math.max(0, max - stack.getDamage());
            float fraction = Math.max(0.0f, Math.min(1.0f, remaining / (float) max));
            int rgb = fraction > 0.5f ? 0x55FF55 : fraction > 0.25f ? 0xFFC857 : 0xFF5555;
            String text = remaining >= 1000 ? (remaining / 100) / 10.0f + "k" : String.valueOf(remaining);
            int textWidth = Math.round(mc.textRenderer.getWidth(text) * TEXT_SCALE);
            int textX = x + (ICON_SIZE - textWidth) / 2;
            Render2D.scaledText(context, mc.textRenderer, text, textX, baseY - 9,
                    TEXT_SCALE, 0xFF000000 | rgb);
            if (bars.getValue()) {
                int barY = baseY + ICON_SIZE + 1;
                context.fill(x, barY, x + ICON_SIZE, barY + 2, 0x90000000);
                int filled = Math.round(ICON_SIZE * fraction);
                if (filled > 0) {
                    context.fill(x, barY, x + filled, barY + 2, 0xFF000000 | rgb);
                }
            }
        }
    }
}
