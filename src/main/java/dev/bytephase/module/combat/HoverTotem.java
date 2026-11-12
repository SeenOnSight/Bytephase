package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.mixin.HandledScreenAccessor;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Swaps the hovered totem out of any open container into the offhand (or a
 * hotbar slot), so totems can be pulled from chests and shulkers without
 * dragging them manually.
 */
public final class HoverTotem extends Module {
    private final BooleanSetting hotbarRefill = add(new BooleanSetting("HotbarRefill", false));
    private final NumberSetting hotbarSlot = add(new NumberSetting("HotbarSlot", 1.0, 1.0, 9.0, 1.0));

    private int cooldown;

    public HoverTotem() {
        super("HoverTotem", Category.COMBAT, "Pulls a hovered totem from containers");
    }

    @Override
    protected void onDisable() {
        cooldown = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
            return;
        }
        ScreenHandler handler = screen.getScreenHandler();
        if (!handler.getCursorStack().isEmpty()) {
            return;
        }
        Slot hovered = ((HandledScreenAccessor) screen).bytephase$focusedSlot();
        if (hovered == null || !hovered.getStack().isOf(Items.TOTEM_OF_UNDYING)) {
            return;
        }
        if (hovered.inventory == mc.player.getInventory()
                && hovered.id == net.minecraft.screen.PlayerScreenHandler.OFFHAND_ID) {
            return;
        }

        int button;
        if (!mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            button = 40;
        } else if (hotbarRefill.getValue()) {
            int hotbar = hotbarSlot.asInt() - 1;
            if (mc.player.getInventory().getStack(hotbar).isOf(Items.TOTEM_OF_UNDYING)) {
                return;
            }
            button = hotbar;
        } else {
            return;
        }

        mc.interactionManager.clickSlot(handler.syncId, hovered.id, button,
                SlotActionType.SWAP, mc.player);
        cooldown = 4;
    }
}
