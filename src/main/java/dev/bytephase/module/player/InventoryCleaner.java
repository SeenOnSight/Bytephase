package dev.bytephase.module.player;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public final class InventoryCleaner extends Module {
    private final BooleanSetting dirt = add(new BooleanSetting("Dirt", false));
    private final BooleanSetting cobble = add(new BooleanSetting("Cobble", false));
    private final BooleanSetting gravel = add(new BooleanSetting("Gravel", false));
    private final BooleanSetting seeds = add(new BooleanSetting("Seeds", false));
    private final BooleanSetting flesh = add(new BooleanSetting("RottenFlesh", false));
    private final BooleanSetting protectHotbar = add(new BooleanSetting("ProtectHotbar", true));
    private final NumberSetting delay = add(new NumberSetting("DelayTicks", 4, 2, 40, 1));
    private int cooldown;

    public InventoryCleaner() {
        super("InventoryCleaner", Category.PLAYER, "Drops selected junk stacks from your inventory");
    }

    @Override
    protected void onDisable() { cooldown = 0; }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc.player == null || mc.interactionManager == null || mc.currentScreen != null
                || !mc.isWindowFocused() || !mc.player.isAlive() || mc.player.isSpectator()
                || mc.player.currentScreenHandler != mc.player.playerScreenHandler
                || !mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        if (cooldown > 0) { cooldown--; return; }
        for (int slot = protectHotbar.getValue() ? 9 : 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty() || stack.contains(DataComponentTypes.CUSTOM_NAME)
                    || stack.hasEnchantments() || !junk(stack)) continue;
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                    slot < 9 ? slot + 36 : slot, 1, SlotActionType.THROW, mc.player);
            cooldown = delay.asInt();
            return;
        }
    }

    private boolean junk(ItemStack stack) {
        return dirt.getValue() && (stack.isOf(Items.DIRT) || stack.isOf(Items.COARSE_DIRT))
                || cobble.getValue() && stack.isOf(Items.COBBLESTONE)
                || gravel.getValue() && (stack.isOf(Items.GRAVEL) || stack.isOf(Items.FLINT))
                || seeds.getValue() && (stack.isOf(Items.WHEAT_SEEDS) || stack.isOf(Items.BEETROOT_SEEDS)
                        || stack.isOf(Items.MELON_SEEDS) || stack.isOf(Items.PUMPKIN_SEEDS))
                || flesh.getValue() && stack.isOf(Items.ROTTEN_FLESH);
    }
}
