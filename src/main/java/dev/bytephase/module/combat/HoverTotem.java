package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.mixin.HandledScreenAccessor;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public final class HoverTotem extends Module {
    private final BooleanSetting refill = add(new BooleanSetting("HotbarRefill", false));
    private final NumberSetting hotbarSlot = add(new NumberSetting("HotbarSlot", 1, 1, 9, 1));
    private int cooldown;

    public HoverTotem() {
        super("HoverTotem", Category.COMBAT, "Moves a hovered totem into offhand or a chosen hotbar slot");
    }

    @Override
    protected void onDisable() { cooldown = 0; }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (cooldown > 0) { cooldown--; return; }
        if (mc.player == null || mc.interactionManager == null || !mc.isWindowFocused()
                || !mc.player.isAlive() || mc.player.isSpectator()
                || !(mc.currentScreen instanceof HandledScreen<?> screen)) return;
        AutoTotem automatic = BytePhase.modules.get(AutoTotem.class);
        if (automatic != null && automatic.isEnabled()) return;
        var handler = screen.getScreenHandler();
        if (handler != mc.player.currentScreenHandler || !handler.canUse(mc.player)
                || !handler.getCursorStack().isEmpty()) return;
        var hovered = ((HandledScreenAccessor) screen).bytephase$focusedSlot();
        if (hovered == null || !hovered.isEnabled() || !hovered.canTakeItems(mc.player)
                || !hovered.getStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        int destination = mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)
                ? (refill.getValue() ? hotbarSlot.asInt() - 1 : -1) : 40;
        if (destination < 0 || hovered.inventory == mc.player.getInventory()
                && hovered.getIndex() == destination) return;
        var replaced = mc.player.getInventory().getStack(destination);
        if (replaced.isOf(Items.TOTEM_OF_UNDYING)
                || !replaced.isEmpty() && (!hovered.canInsert(replaced)
                        || replaced.getCount() > hovered.getMaxItemCount(replaced))) return;
        mc.interactionManager.clickSlot(handler.syncId, hovered.id, destination, SlotActionType.SWAP, mc.player);
        cooldown = 4;
    }
}
