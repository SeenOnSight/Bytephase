package dev.bytephase.pvp.module.impl.combat.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import dev.bytephase.pvp.easyplace.inventory.InventoryCalm;
import dev.bytephase.pvp.module.impl.combat.AutoInvTotemModule;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
final class BotKit {
    private static final int CLICK_GAP = 4;
    private int lastClick = -4;

    boolean needed(LocalPlayer player, boolean anchors) {
        return next(player, wants(anchors)) != null;
    }

    boolean refill(Minecraft mc, LocalPlayer player, boolean anchors) {
        if (mc.gameMode != null
            && mc.screen == null
            && player.containerMenu == player.inventoryMenu
            && player.inventoryMenu.getCarried().isEmpty()
            && !AutoInvTotemModule.busy()
            && !player.isUsingItem()) {
            int[] move = next(player, wants(anchors));
            if (move == null) {
                return false;
            } else {
                int tick = player.tickCount;
                if (InventoryCalm.settle(mc, player) && (tick < this.lastClick || tick - this.lastClick >= 4)) {
                    mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, move[0], move[1], ClickType.SWAP, player);
                    player.connection.send(new ServerboundContainerClosePacket(player.inventoryMenu.containerId));
                    MeleeAttacks.noteAction(player);
                    this.lastClick = tick;
                    return true;
                } else {
                    return true;
                }
            }
        } else {
            return false;
        }
    }

    private static List<BotKit.Want> wants(boolean anchors) {
        List<BotKit.Want> wants = new ArrayList<>();
        wants.add(new BotKit.Want(stack -> stack.is(Items.END_CRYSTAL), 16, 32));
        wants.add(new BotKit.Want(stack -> stack.is(Items.OBSIDIAN), 8, 16));
        wants.add(new BotKit.Want(MeleeAttacks::isMeleeWeapon, 1, 1));
        wants.add(new BotKit.Want(stack -> stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.GOLDEN_APPLE), 1, 1));
        wants.add(new BotKit.Want(stack -> stack.is(Items.ENDER_PEARL), 1, 1));
        wants.add(new BotKit.Want(stack -> stack.is(ItemTags.PICKAXES), 1, 1));
        wants.add(new BotKit.Want(stack -> stack.is(Items.EXPERIENCE_BOTTLE), 1, 1));
        if (anchors) {
            wants.add(new BotKit.Want(stack -> stack.is(Items.RESPAWN_ANCHOR), 2, 4));
            wants.add(new BotKit.Want(stack -> stack.is(Items.GLOWSTONE), 2, 4));
        }

        return wants;
    }

    private static int[] next(LocalPlayer player, List<BotKit.Want> wants) {
        Inventory inventory = player.getInventory();

        for (BotKit.Want want : wants) {
            int inHotbar = 0;
            int smallest = -1;

            for (int i = 0; i < 9; i++) {
                ItemStack stack = inventory.getItem(i);
                if (want.item().test(stack)) {
                    inHotbar += stack.getCount();
                    if (smallest < 0 || stack.getCount() < inventory.getItem(smallest).getCount()) {
                        smallest = i;
                    }
                }
            }

            if (inHotbar < want.low()) {
                int source = -1;

                for (int i = 9; i < 36; i++) {
                    ItemStack stack = inventory.getItem(i);
                    if (want.item().test(stack) && (source < 0 || stack.getCount() > inventory.getItem(source).getCount())) {
                        source = i;
                    }
                }

                if (source >= 0) {
                    int bigger = inventory.getItem(source).getCount();
                    if (smallest >= 0 && bigger > inHotbar && (inHotbar < want.topUp() || bigger >= inHotbar + 8)) {
                        return new int[]{source, smallest};
                    }

                    if (smallest < 0) {
                        int free = freeSlot(inventory, wants);
                        if (free >= 0) {
                            return new int[]{source, free};
                        }
                    }
                }
            }
        }

        return null;
    }

    private static int freeSlot(Inventory inventory, List<BotKit.Want> wants) {
        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i).isEmpty()) {
                return i;
            }
        }

        for (int i = 8; i >= 0; i--) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(Items.TOTEM_OF_UNDYING)) {
                boolean used = false;

                for (BotKit.Want want : wants) {
                    used |= want.item().test(stack);
                }

                if (!used) {
                    return i;
                }
            }
        }

        return -1;
    }

    @Environment(EnvType.CLIENT)
    private record Want(Predicate<ItemStack> item, int low, int topUp) {
    }
}
