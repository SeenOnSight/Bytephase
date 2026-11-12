package dev.bytephase.module.player;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.module.combat.AutoTotem;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Keeps a chosen item in the offhand. Totem mode only swaps when health is at
 * or below the configured threshold so it does not fight manual offhand use.
 */
public final class Offhand extends Module {
    private static final int OFFHAND_SWAP_BUTTON = 40;

    private final ModeSetting item = add(new ModeSetting("Item",
            new String[]{"Totem", "GoldenApple", "Crystal", "Shield"}, "Totem"));
    private final NumberSetting health = add(new NumberSetting("Health", 10.0, 0.0, 36.0, 1.0));
    private final NumberSetting delay = add(new NumberSetting("Delay", 2, 0, 20, 1));

    private int cooldown;

    public Offhand() {
        super("Offhand", Category.PLAYER, "Keeps a totem or item in your offhand");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.interactionManager == null
                || mc.currentScreen != null) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (!(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) {
            return;
        }

        // AutoTotem takes precedence only when its selected mode currently
        // requires a totem. Otherwise this module can manage the chosen item.
        AutoTotem autoTotem = BytePhase.modules == null ? null : BytePhase.modules.get(AutoTotem.class);
        if (autoTotem != null && autoTotem.shouldManageOffhand()) {
            return;
        }

        ItemStack offhand = mc.player.getOffHandStack();
        if (matches(offhand)) {
            return;
        }
        if (item.getValue().equals("Totem") && mc.player.getHealth() > health.getValue()) {
            return;
        }

        int slot = findItem();
        if (slot == -1) {
            return;
        }

        int screenSlot = slot >= 9 ? slot : slot + 36;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, screenSlot,
                OFFHAND_SWAP_BUTTON, SlotActionType.SWAP, mc.player);
        cooldown = Math.max(0, delay.asInt());
    }

    private boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (item.getValue()) {
            case "Totem" -> stack.isOf(Items.TOTEM_OF_UNDYING);
            case "GoldenApple" -> stack.isOf(Items.ENCHANTED_GOLDEN_APPLE) || stack.isOf(Items.GOLDEN_APPLE);
            case "Crystal" -> stack.isOf(Items.END_CRYSTAL);
            case "Shield" -> stack.isOf(Items.SHIELD);
            default -> false;
        };
    }

    private int findItem() {
        var inventory = BytePhase.mc.player.getInventory();
        for (int i = 0; i < inventory.getMainStacks().size(); i++) {
            if (matches(inventory.getStack(i))) {
                return i;
            }
        }
        return -1;
    }
}
