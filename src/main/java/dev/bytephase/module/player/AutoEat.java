package dev.bytephase.module.player;

import dev.bytephase.BytePhase;
import dev.bytephase.input.KeyOverride;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public final class AutoEat extends Module {
    private final NumberSetting hunger = add(new NumberSetting("MinHunger", 14, 1, 19, 1));
    private final BooleanSetting avoidHarmful = add(new BooleanSetting("AvoidHarmful", true));
    private final BooleanSetting saveGoldenApples = add(new BooleanSetting("SaveGoldenApples", true));
    private int previousSlot = -1;
    private int foodSlot = -1;
    private ClientPlayerEntity capturedPlayer;
    private boolean eating;

    public AutoEat() {
        super("AutoEat", Category.PLAYER, "Eats food when hunger gets low");
    }

    @Override
    protected void onDisable() {
        stop();
        restoreSelectedSlot();
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            KeyOverride.releaseAll(this);
            eating = false;
            restoreSelectedSlot();
            return;
        }
        if (capturedPlayer != null && capturedPlayer != mc.player) {
            // A respawn or reconnect replaces the local player object. Restore
            // only the inventory whose slot AutoEat changed, then start fresh.
            KeyOverride.releaseAll(this);
            eating = false;
            restoreSelectedSlot();
        }
        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (foodSlot >= 0 && selectedSlot != foodSlot) {
            // The user or another module changed the hotbar while AutoEat was
            // using its food slot. Drop ownership so a later restore cannot
            // overwrite that newer selection.
            KeyOverride.releaseAll(this);
            eating = false;
            previousSlot = -1;
            foodSlot = -1;
            capturedPlayer = null;
        }
        if (mc.currentScreen != null) {
            stop();
            restoreSelectedSlot();
            return;
        }
        if (mc.player.getHungerManager().getFoodLevel() > hunger.asInt()) {
            stop();
            restoreSelectedSlot();
            return;
        }
        if (mc.player.isUsingItem()) {
            if (eating && (mc.player.getActiveHand() != Hand.MAIN_HAND
                    || mc.player.getActiveItem().get(DataComponentTypes.FOOD) == null)) {
                KeyOverride.releaseAll(this);
                eating = false;
            }
            return;
        }
        // Food use has completed or was interrupted; do not keep holding use
        // while searching for the next item.
        KeyOverride.releaseAll(this);
        eating = false;
        int slot = findFood();
        if (slot < 0) {
            stop();
            restoreSelectedSlot();
            return;
        }
        if (slot != mc.player.getInventory().getSelectedSlot()) {
            if (previousSlot < 0) {
                previousSlot = mc.player.getInventory().getSelectedSlot();
                capturedPlayer = mc.player;
            }
            foodSlot = slot;
            mc.player.getInventory().setSelectedSlot(slot);
        }
        var result = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        if (result.isAccepted() && mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.MAIN_HAND
                && mc.player.getActiveItem().get(DataComponentTypes.FOOD) != null) {
            capturedPlayer = mc.player;
            foodSlot = slot;
            eating = true;
            // Vanilla stops using an item on the next input tick unless use is
            // held. Claim it only after starting our own food use.
            KeyOverride.set(this, mc.options.useKey, true);
        }
    }

    private void restoreSelectedSlot() {
        ClientPlayerEntity player = capturedPlayer;
        if (player != null && previousSlot >= 0
                && player.getInventory().getSelectedSlot() == foodSlot) {
            player.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
        foodSlot = -1;
        capturedPlayer = null;
    }

    private int findFood() {
        var mc = BytePhase.mc;
        int best = -1;
        float bestFood = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            FoodComponent food = stack.get(DataComponentTypes.FOOD);
            if (food == null || !allowed(stack)) {
                continue;
            }
            float value = food.nutrition() + food.saturation();
            if (value > bestFood) {
                bestFood = value;
                best = i;
            }
        }
        return best;
    }

    /**
     * Foods that do more harm than good when eaten automatically: chorus
     * fruit teleports you, rotten flesh, spider eyes, pufferfish, poisonous
     * potatoes and raw chicken apply bad effects, and golden apples are
     * normally kept for healing rather than hunger.
     */
    private boolean allowed(ItemStack stack) {
        if (avoidHarmful.getValue() && (stack.isOf(Items.CHORUS_FRUIT) || stack.isOf(Items.ROTTEN_FLESH)
                || stack.isOf(Items.SPIDER_EYE) || stack.isOf(Items.PUFFERFISH)
                || stack.isOf(Items.POISONOUS_POTATO) || stack.isOf(Items.CHICKEN)
                || stack.isOf(Items.SUSPICIOUS_STEW))) {
            return false;
        }
        return !(saveGoldenApples.getValue()
                && (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)));
    }

    private void stop() {
        var mc = BytePhase.mc;
        KeyOverride.releaseAll(this);
        if (eating && mc != null && mc.player == capturedPlayer && mc.player != null
                && mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.MAIN_HAND
                && mc.interactionManager != null
                && mc.player.getActiveItem().get(DataComponentTypes.FOOD) != null) {
            mc.interactionManager.stopUsingItem(mc.player);
        }
        eating = false;
    }
}
