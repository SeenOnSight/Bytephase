package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.input.KeyOverride;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * Eats soup to heal on servers whose soup-PvP plugins restore health per bowl.
 *
 * <p>Soup is a normal food item locally, so eating it takes the full vanilla
 * use time. The module holds the use key for the whole bite through
 * {@link KeyOverride}, then restores the hotbar slot it borrowed. It triggers
 * on health rather than hunger, because on those servers the hunger bar is not
 * what the player cares about.</p>
 */
public final class AutoSoup extends Module {
    private final NumberSetting health = add(new NumberSetting("Health", 16.0, 1.0, 20.0, 0.5));
    private final NumberSetting delay = add(new NumberSetting("Delay", 2.0, 0.0, 20.0, 1.0));

    private int previousSlot = -1;
    private int soupSlot = -1;
    private int cooldown;
    private boolean eating;
    private ClientPlayerEntity owner;

    public AutoSoup() {
        super("AutoSoup", Category.COMBAT, "Eats soup to heal on soup PvP servers");
    }

    @Override
    protected void onEnable() {
        previousSlot = -1;
        soupSlot = -1;
        cooldown = 0;
        eating = false;
        owner = null;
    }

    @Override
    protected void onDisable() {
        stop();
        restoreSlot();
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            stop();
            restoreSlot();
            return;
        }
        if (owner != null && owner != mc.player) {
            // A respawn or reconnect replaces the local player object; never
            // restore a slot on the previous player's inventory.
            stop();
            restoreSlot();
        }
        if (mc.currentScreen != null || mc.player.isDead() || mc.player.isSpectator()) {
            stop();
            restoreSlot();
            return;
        }
        int selected = mc.player.getInventory().getSelectedSlot();
        if (soupSlot >= 0 && selected != soupSlot) {
            // The hotbar moved while we owned it; drop ownership so a later
            // restore cannot overwrite the player's newer selection.
            KeyOverride.releaseAll(this);
            eating = false;
            previousSlot = -1;
            soupSlot = -1;
            owner = null;
        }

        if (eating) {
            if (mc.player.isUsingItem() && isSoup(mc.player.getActiveItem())) {
                KeyOverride.set(this, mc.options.useKey, true);
                return;
            }
            // The bite finished (bowl consumed or use interrupted).
            finish();
            return;
        }

        if (mc.player.getHealth() > health.asFloat()) {
            restoreSlot();
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        int slot = findSoup();
        if (slot < 0) {
            restoreSlot();
            return;
        }
        if (slot != mc.player.getInventory().getSelectedSlot()) {
            if (previousSlot < 0) {
                previousSlot = mc.player.getInventory().getSelectedSlot();
                owner = mc.player;
            }
            soupSlot = slot;
            mc.player.getInventory().setSelectedSlot(slot);
        }
        var result = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        if (result.isAccepted() && mc.player.isUsingItem() && isSoup(mc.player.getActiveItem())) {
            owner = mc.player;
            soupSlot = slot;
            eating = true;
            KeyOverride.set(this, mc.options.useKey, true);
        } else {
            restoreSlot();
        }
    }

    private void finish() {
        KeyOverride.releaseAll(this);
        eating = false;
        cooldown = delay.asInt();
        restoreSlot();
    }

    private void stop() {
        var mc = BytePhase.mc;
        KeyOverride.releaseAll(this);
        if (eating && mc != null && mc.player != null && mc.player == owner
                && mc.interactionManager != null && mc.player.isUsingItem()
                && isSoup(mc.player.getActiveItem())) {
            // Releasing the use key is not enough: vanilla keeps the bite
            // going until the use action is stopped explicitly.
            mc.interactionManager.stopUsingItem(mc.player);
        }
        eating = false;
    }

    private void restoreSlot() {
        ClientPlayerEntity player = owner;
        if (player != null && previousSlot >= 0 && soupSlot >= 0
                && player.getInventory().getSelectedSlot() == soupSlot) {
            player.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
        soupSlot = -1;
        owner = null;
    }

    private int findSoup() {
        var mc = BytePhase.mc;
        for (int slot = 0; slot < 9; slot++) {
            if (isSoup(mc.player.getInventory().getStack(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean isSoup(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.isOf(Items.MUSHROOM_STEW)
                || stack.isOf(Items.RABBIT_STEW)
                || stack.isOf(Items.BEETROOT_SOUP)
                || stack.isOf(Items.SUSPICIOUS_STEW);
    }
}
