package dev.bytephase.module.player;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.ModeSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * One-shot armour swap: enabling swaps the chestplate for an elytra (or back)
 * and then disables the module again.
 */
public final class ChestSwap extends Module {
    private final ModeSetting chestplate = add(new ModeSetting("Chestplate",
            new String[]{"PreferNetherite", "PreferDiamond", "Netherite", "Diamond"}, "PreferNetherite"));

    private boolean swapping;

    public ChestSwap() {
        super("ChestSwap", Category.PLAYER, "Swaps between a chestplate and an elytra");
    }

    @Override
    public void onEnable() {
        if (swapping) {
            return;
        }
        swapping = true;
        try {
            swap();
        } finally {
            swapping = false;
            setEnabled(false);
        }
    }

    private void swap() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.interactionManager == null
                || mc.currentScreen != null) {
            return;
        }
        if (!(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) {
            return;
        }

        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        boolean wearingElytra = chest.contains(DataComponentTypes.GLIDER);
        boolean wearingChestplate = chest.contains(DataComponentTypes.EQUIPPABLE)
                && chest.get(DataComponentTypes.EQUIPPABLE).slot() == EquipmentSlot.CHEST;

        if (wearingElytra) {
            equipChestplate();
        } else if (wearingChestplate) {
            equipElytra();
        } else if (!equipChestplate()) {
            equipElytra();
        }
    }

    private boolean equipChestplate() {
        var inventory = BytePhase.mc.player.getInventory();
        int netherite = -1;
        int diamond = -1;
        for (int i = 0; i < inventory.getMainStacks().size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isOf(Items.NETHERITE_CHESTPLATE)) {
                netherite = i;
            } else if (stack.isOf(Items.DIAMOND_CHESTPLATE)) {
                diamond = i;
            }
        }

        int chosen = switch (chestplate.getValue()) {
            case "Netherite" -> netherite;
            case "Diamond" -> diamond;
            case "PreferDiamond" -> diamond != -1 ? diamond : netherite;
            default -> netherite != -1 ? netherite : diamond;
        };
        if (chosen == -1) {
            return false;
        }
        equip(chosen);
        return true;
    }

    private void equipElytra() {
        var inventory = BytePhase.mc.player.getInventory();
        for (int i = 0; i < inventory.getMainStacks().size(); i++) {
            if (inventory.getStack(i).contains(DataComponentTypes.GLIDER)) {
                equip(i);
                return;
            }
        }
    }

    private void equip(int inventorySlot) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            return;
        }
        if (!(mc.player.currentScreenHandler instanceof PlayerScreenHandler handler)
                || !handler.getCursorStack().isEmpty()) {
            return;
        }
        int screenSlot = inventorySlot >= PlayerScreenHandler.INVENTORY_START
                ? inventorySlot
                : inventorySlot + PlayerScreenHandler.HOTBAR_START;
        int chestSlot = PlayerScreenHandler.EQUIPMENT_START + 1;

        // PICKUP is the only valid way to move an item into an armour slot
        // through a screen handler. SWAP buttons only address the hotbar or
        // the offhand; button 38 does not exist.
        mc.interactionManager.clickSlot(handler.syncId, screenSlot, 0,
                SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(handler.syncId, chestSlot, 0,
                SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(handler.syncId, screenSlot, 0,
                SlotActionType.PICKUP, mc.player);
    }
}
