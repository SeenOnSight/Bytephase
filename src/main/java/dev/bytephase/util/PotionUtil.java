package dev.bytephase.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/** Shared helpers for the throwable-healing-potion modules. */
public final class PotionUtil {
    private PotionUtil() {
    }

    /** Whether the stack is a splash potion that restores health. */
    public static boolean isHealingSplash(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(Items.SPLASH_POTION)) {
            return false;
        }
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) {
            return false;
        }
        for (StatusEffectInstance effect : contents.getEffects()) {
            var type = effect.getEffectType();
            if (type.equals(StatusEffects.INSTANT_HEALTH)
                    || type.equals(StatusEffects.REGENERATION)) {
                return true;
            }
        }
        return false;
    }

    /** First hotbar slot holding a healing splash potion, or -1. */
    public static int findHealingSlot(net.minecraft.client.MinecraftClient mc) {
        if (mc == null || mc.player == null) {
            return -1;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (isHealingSplash(mc.player.getInventory().getStack(slot))) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Throws the held potion straight down so it lands on the player. When
     * {@code rotate} is set, a look packet pointing at the ground is sent first
     * (the server builds the potion from the last rotation it received) and the
     * camera pitch is restored afterwards.
     */
    public static boolean throwDownward(net.minecraft.client.MinecraftClient mc, boolean rotate) {
        var player = mc == null ? null : mc.player;
        if (player == null) {
            return false;
        }
        if (!rotate) {
            return useMainHand(mc);
        }
        float previousPitch = player.getPitch();
        player.setPitch(90.0f);
        try {
            if (player.networkHandler != null) {
                player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(
                        player.getYaw(), 90.0f, player.isOnGround(), false));
            }
            return useMainHand(mc);
        } finally {
            player.setPitch(previousPitch);
        }
    }

    /** Throws the currently held item, swinging only if the server accepted it. */
    public static boolean useMainHand(net.minecraft.client.MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            return false;
        }
        var result = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        return result.isAccepted();
    }
}
