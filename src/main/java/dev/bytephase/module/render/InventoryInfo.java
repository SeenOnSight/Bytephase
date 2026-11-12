package dev.bytephase.module.render;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Adds shulker contents and stored enchantments to item tooltips.
 *
 * <p>Both live in data components, so the summary is built from the component
 * data on the stack rather than from the item type: a modded storage item that
 * uses {@code CONTAINER} gets the same listing, and an empty shulker adds no
 * lines at all. Lines are capped so a full box does not push the rest of the
 * tooltip off screen.</p>
 */
public final class InventoryInfo extends Module {
    private final BooleanSetting shulkers = add(new BooleanSetting("Shulkers", true));
    private final BooleanSetting books = add(new BooleanSetting("Books", true));
    private final NumberSetting maxLines = add(new NumberSetting("MaxLines", 12.0, 1.0, 32.0, 1.0));

    public InventoryInfo() {
        super("InventoryInfo", Category.RENDER, "Lists shulker contents and stored enchantments on tooltips");
    }

    /** Appends this module's tooltip lines. Public so the regression test can collect them. */
    public void append(ItemStack stack, Consumer<Text> lines) {
        if (!isEnabled() || stack == null || stack.isEmpty() || lines == null) {
            return;
        }
        int budget = maxLines.asInt();
        if (shulkers.getValue()) {
            budget = appendContents(stack, lines, budget);
        }
        if (books.getValue()) {
            appendStoredEnchantments(stack, lines, budget);
        }
    }

    private int appendContents(ItemStack stack, Consumer<Text> lines, int budget) {
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container == null) {
            return budget;
        }
        List<ItemStack> items = new ArrayList<>();
        int total = 0;
        for (ItemStack inner : container.iterateNonEmpty()) {
            items.add(inner);
            total += inner.getCount();
        }
        if (items.isEmpty()) {
            return budget;
        }
        lines.accept(Text.literal("Contents: " + total + " item" + (total == 1 ? "" : "s"))
                .formatted(Formatting.GRAY));
        budget--;
        for (ItemStack inner : items) {
            if (budget <= 0) {
                lines.accept(Text.literal("...").formatted(Formatting.DARK_GRAY));
                return 0;
            }
            lines.accept(Text.literal(" " + inner.getCount() + "x ").formatted(Formatting.GRAY)
                    .append(inner.getName().copy().formatted(Formatting.WHITE)));
            budget--;
        }
        return budget;
    }

    private void appendStoredEnchantments(ItemStack stack, Consumer<Text> lines, int budget) {
        ItemEnchantmentsComponent stored = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty() || budget <= 0) {
            return;
        }
        for (var entry : stored.getEnchantmentEntries()) {
            if (budget <= 0) {
                lines.accept(Text.literal("...").formatted(Formatting.DARK_GRAY));
                return;
            }
            lines.accept(Enchantment.getName(entry.getKey(), entry.getIntValue())
                    .copy().formatted(Formatting.BLUE));
            budget--;
        }
    }
}
