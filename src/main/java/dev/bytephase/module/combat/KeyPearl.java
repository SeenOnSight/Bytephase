package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public final class KeyPearl extends Module {
    private int previousSlot = -1;
    private int pearlSlot = -1;
    private boolean throwQueued;
    private ClientPlayerEntity queuedPlayer;

    public KeyPearl() {
        super("KeyPearl", Category.COMBAT, "Quickly throws an ender pearl from your hotbar");
    }

    @Override
    protected void onEnable() {
        previousSlot = -1;
        pearlSlot = -1;
        throwQueued = false;
        queuedPlayer = null;
    }

    @Override
    protected void onDisable() {
        if (throwQueued && queuedPlayer != null && previousSlot >= 0
                && queuedPlayer.getInventory().getSelectedSlot() == pearlSlot) {
            queuedPlayer.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
        pearlSlot = -1;
        throwQueued = false;
        queuedPlayer = null;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (throwQueued && (mc == null || mc.player != queuedPlayer)) {
            setEnabled(false);
            return;
        }
        if (mc == null || mc.player == null || mc.interactionManager == null || mc.currentScreen != null) {
            return;
        }
        if (!throwQueued) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() instanceof EnderPearlItem) {
                    previousSlot = mc.player.getInventory().getSelectedSlot();
                    pearlSlot = i;
                    queuedPlayer = mc.player;
                    mc.player.getInventory().setSelectedSlot(i);
                    throwQueued = true;
                    return;
                }
            }
            setEnabled(false);
            return;
        }
        // The player may have changed slots while the throw was queued. Do
        // not consume whatever is now in the main hand, and do not overwrite
        // that manual selection when cleaning up.
        if (mc.player.getInventory().getSelectedSlot() != pearlSlot
                || !(mc.player.getMainHandStack().getItem() instanceof EnderPearlItem)) {
            setEnabled(false);
            return;
        }
        var result = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        if (previousSlot >= 0 && previousSlot < 9
                && mc.player.getInventory().getSelectedSlot() == pearlSlot) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        setEnabled(false);
    }
}
