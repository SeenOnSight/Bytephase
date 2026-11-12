package dev.bytephase.module.player;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Tops up hotbar stacks from the inventory while a container is closed.
 *
 * <p>Ported from Anubis' {@code AutoReplenishModule}. The source stack is picked
 * up, merged into the hotbar slot, and leftovers are put back, so a single
 * module tick never loses items if the server rejects a click.</p>
 */
public final class AutoReplenish extends Module {
    private final NumberSetting threshold = add(new NumberSetting("Threshold", 16.0, 1.0, 63.0, 1.0));
    private final NumberSetting delay = add(new NumberSetting("Delay", 1.0, 0.0, 10.0, 1.0));

    private int cooldown;
    private int cursor = -1;

    public AutoReplenish() {
        super("AutoReplenish", Category.PLAYER, "Refills low hotbar stacks");
    }

    @Override
    protected void onDisable() {
        cooldown = 0;
        cursor = -1;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            return;
        }
        if (mc.currentScreen != null || !(mc.player.currentScreenHandler instanceof PlayerScreenHandler handler)
                || !handler.getCursorStack().isEmpty()) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty() || stack.getCount() > threshold.asInt()
                    || stack.getCount() >= stack.getMaxCount()) {
                continue;
            }
            int source = findSource(stack);
            if (source < 0) {
                continue;
            }
            int syncId = mc.player.currentScreenHandler.syncId;
            mc.interactionManager.clickSlot(syncId, source, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, PlayerScreenHandler.HOTBAR_START + slot, 0,
                    SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, source, 0, SlotActionType.PICKUP, mc.player);
            cooldown = delay.asInt();
            return;
        }
    }

    private int findSource(ItemStack hotbar) {
        var mc = BytePhase.mc;
        int start = PlayerScreenHandler.INVENTORY_START;
        int end = PlayerScreenHandler.HOTBAR_START - 1;
        // Rotate through the inventory so one stack is not drained every pass.
        if (cursor < start || cursor > end) {
            cursor = start;
        }
        for (int i = 0; i <= end - start; i++) {
            int slot = start + (cursor - start + i) % (end - start + 1);
            ItemStack candidate = mc.player.getInventory().getStack(slot);
            // getStack uses inventory indices; main inventory is 9..35 there.
            if (candidate.isEmpty() || !ItemStack.areItemsAndComponentsEqual(candidate, hotbar)) {
                continue;
            }
            cursor = slot + 1;
            return slot;
        }
        return -1;
    }
}
