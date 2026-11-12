package dev.bytephase.util;

import dev.bytephase.BytePhase;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

/** Shared helpers for the mace, its enchantments and hotbar slot swaps. */
public final class MaceUtil {
    /** A smash attack needs a fall of more than this many blocks. */
    public static final double SMASH_FALL_DISTANCE = 1.5;

    private MaceUtil() {
    }

    public static boolean isMace(ItemStack stack) {
        return stack != null && stack.isOf(Items.MACE);
    }

    /** Level of {@code key} on {@code stack}, or 0. */
    public static int level(ItemStack stack, RegistryKey<Enchantment> key) {
        var world = BytePhase.mc == null ? null : BytePhase.mc.world;
        if (world == null || stack == null || stack.isEmpty()) {
            return 0;
        }
        return world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT)
                .getEntry(key.getValue())
                .map(entry -> EnchantmentHelper.getLevel(entry, stack))
                .orElse(0);
    }

    /** Whether an attack right now would be a smash attack (falling, not gliding). */
    public static boolean canSmash(PlayerEntity player) {
        return player.fallDistance > SMASH_FALL_DISTANCE && !player.isGliding() && !player.isOnGround();
    }

    /**
     * Hotbar slot holding the mace with the highest level of {@code key}
     * (at least 1), or -1. Ties prefer the currently selected slot.
     */
    public static int findHotbarMace(PlayerEntity player, RegistryKey<Enchantment> key) {
        int best = -1;
        int bestLevel = 0;
        int selected = player.getInventory().getSelectedSlot();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!isMace(stack)) {
                continue;
            }
            int level = level(stack, key);
            if (level > bestLevel || level == bestLevel && level > 0 && slot == selected) {
                best = slot;
                bestLevel = level;
            }
        }
        return best;
    }

    /** Tells the server our look direction immediately, ahead of the next movement packet. */
    public static void sendLook(net.minecraft.client.MinecraftClient mc, float yaw, float pitch) {
        if (mc == null || mc.player == null || mc.getNetworkHandler() == null) {
            return;
        }
        mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(
                yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
    }

    /**
     * Uses {@code item} right now: from the offhand if it is there, otherwise
     * from the hotbar, swapping to it and (optionally) back within this tick.
     *
     * @return whether the item was found and used
     */
    public static boolean useItem(net.minecraft.client.MinecraftClient mc, net.minecraft.item.Item item,
                                  boolean restoreSlot) {
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            return false;
        }
        var player = mc.player;
        if (player.getOffHandStack().isOf(item)) {
            mc.interactionManager.interactItem(player, net.minecraft.util.Hand.OFF_HAND);
            player.swingHand(net.minecraft.util.Hand.OFF_HAND);
            return true;
        }
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(item)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            return false;
        }
        int previous = player.getInventory().getSelectedSlot();
        selectNow(mc, slot);
        mc.interactionManager.interactItem(player, net.minecraft.util.Hand.MAIN_HAND);
        player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        if (restoreSlot) {
            selectNow(mc, previous);
        }
        return true;
    }

    /** Whether {@code item} is in the offhand or anywhere in the hotbar. */
    public static boolean hasItem(net.minecraft.client.MinecraftClient mc, net.minecraft.item.Item item) {
        if (mc == null || mc.player == null) {
            return false;
        }
        if (mc.player.getOffHandStack().isOf(item)) {
            return true;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) {
                return true;
            }
        }
        return false;
    }

    /** Best mace to swing next: Wind Burst first, then Density, then any. */
    public static int findBestMace(PlayerEntity player) {
        int slot = findHotbarMace(player, net.minecraft.enchantment.Enchantments.WIND_BURST);
        if (slot < 0) {
            slot = findHotbarMace(player, net.minecraft.enchantment.Enchantments.DENSITY);
        }
        for (int i = 0; slot < 0 && i < 9; i++) {
            if (isMace(player.getInventory().getStack(i))) {
                slot = i;
            }
        }
        return slot;
    }

    /**
     * Selects a hotbar slot locally and tells the server immediately. The
     * interaction manager normally syncs the slot at the start of the next
     * tick; actions that depend on the server seeing the swap within the same
     * tick (the Lunge swap, mid-tick weapon swaps) must not wait for that.
     */
    public static void selectNow(net.minecraft.client.MinecraftClient mc, int slot) {
        if (mc == null || mc.player == null || slot < 0 || slot > 8) {
            return;
        }
        mc.player.getInventory().setSelectedSlot(slot);
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(
                    new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));
        }
    }
}
