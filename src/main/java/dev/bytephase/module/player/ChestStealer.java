package dev.bytephase.module.player;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public final class ChestStealer extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", 2.0, 0.0, 20.0, 1.0));
    private int cooldown;
    private int nextSlot;
    private ScreenHandler activeHandler;
    // Quick-move does nothing once the inventory is full. Count clicks that
    // changed nothing so the module goes idle instead of spamming the server.
    private int lastItemCount = -1;
    private int stalledClicks;
    private boolean gaveUp;

    public ChestStealer() {
        super("ChestStealer", Category.PLAYER, "Quick-moves chest contents into your inventory");
    }

    @Override
    protected void onDisable() {
        cooldown = 0;
        nextSlot = 0;
        activeHandler = null;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            resetScan();
            return;
        }
        ScreenHandler handler;
        if (mc.currentScreen instanceof GenericContainerScreen chest) {
            handler = chest.getScreenHandler();
        } else if (mc.currentScreen instanceof ShulkerBoxScreen shulker) {
            handler = shulker.getScreenHandler();
        } else {
            resetScan();
            return;
        }
        if (handler != activeHandler) {
            activeHandler = handler;
            nextSlot = 0;
            lastItemCount = -1;
            stalledClicks = 0;
            gaveUp = false;
        }
        if (gaveUp) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (!handler.getCursorStack().isEmpty()) {
            return;
        }
        int containerSlots = handler.slots.size() - 36;
        if (containerSlots <= 0) {
            return;
        }
        nextSlot = Math.floorMod(nextSlot, containerSlots);
        int itemCount = 0;
        for (int i = 0; i < containerSlots; i++) {
            if (!handler.getSlot(i).getStack().isEmpty()) {
                itemCount++;
            }
        }
        stalledClicks = itemCount == lastItemCount ? stalledClicks + 1 : 0;
        lastItemCount = itemCount;
        if (stalledClicks > containerSlots + 6) {
            gaveUp = true;
            return;
        }
        for (int offset = 0; offset < containerSlots; offset++) {
            int slot = (nextSlot + offset) % containerSlots;
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            // Advance before clicking so a full-inventory/unmovable stack
            // cannot monopolize every retry and starve later container slots.
            nextSlot = (slot + 1) % containerSlots;
            mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
            cooldown = delay.asInt();
            return;
        }
        nextSlot = 0;
    }

    private void resetScan() {
        nextSlot = 0;
        activeHandler = null;
        lastItemCount = -1;
        stalledClicks = 0;
        gaveUp = false;
    }
}
