package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Keeps a totem in the offhand.
 *
 * <p>Refined against Anubis' {@code AutoTotemModule}: refills whenever the
 * offhand is empty (or only below the health threshold), can use hotbar totems,
 * waits for the server slot update using the measured latency instead of
 * blindly clicking, and always swaps with a single offhand click.</p>
 */
public final class AutoTotem extends Module {
    private static final int OFFHAND_BUTTON = 40;

    private final ModeSetting mode = add(new ModeSetting("Mode", new String[]{"Always", "Health"}, "Always"));
    private final NumberSetting health = add(new NumberSetting("Health", 12.0, 1.0, 20.0, 1.0));
    private final NumberSetting delay = add(new NumberSetting("Delay", 0.0, 0.0, 10.0, 1.0));
    private final BooleanSetting useHotbar = add(new BooleanSetting("UseHotbar", false));

    private int cooldown;
    private int waitTicks;

    public AutoTotem() {
        super("AutoTotem", Category.COMBAT, "Keeps a totem in your offhand");
    }

    /** Whether this module currently owns the offhand slot. */
    public boolean shouldManageOffhand() {
        if (!isEnabled()) {
            return false;
        }
        return mode.getValue().equals("Always")
                || BytePhase.mc != null && BytePhase.mc.player != null
                && BytePhase.mc.player.getHealth() <= health.getValue();
    }

    @Override
    protected void onEnable() {
        cooldown = 0;
        waitTicks = 0;
    }

    @Override
    protected void onDisable() {
        cooldown = 0;
        waitTicks = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }
        ItemStack offhand = mc.player.getOffHandStack();
        if (offhand.isOf(Items.TOTEM_OF_UNDYING)) {
            waitTicks = 0;
            return;
        }
        if (mode.getValue().equals("Health") && mc.player.getHealth() > health.getValue()) {
            return;
        }
        if (waitTicks > 0) {
            if (--waitTicks == 0) {
                cooldown = delay.asInt();
            }
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (!(mc.player.currentScreenHandler instanceof PlayerScreenHandler handler)) {
            return;
        }
        int source = findSource();
        if (source < 0) {
            return;
        }
        mc.interactionManager.clickSlot(handler.syncId, menuSlot(source), OFFHAND_BUTTON,
                SlotActionType.SWAP, mc.player);
        waitTicks = Math.max(4, latencyTicks());
    }

    /** Inventory index of the closest totem, preferring the last source row. */
    private int findSource() {
        var mc = BytePhase.mc;
        int start = useHotbar.getValue() ? 0 : 9;
        int best = -1;
        for (int i = start; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isOf(Items.TOTEM_OF_UNDYING)) {
                continue;
            }
            if (best < 0 || Math.abs((i % 9) - 4) < Math.abs((best % 9) - 4)) {
                best = i;
            }
        }
        return best;
    }

    private int latencyTicks() {
        var mc = BytePhase.mc;
        if (mc.player == null || mc.getNetworkHandler() == null) {
            return 6;
        }
        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        int latency = entry == null ? 0 : entry.getLatency();
        return Math.max(3, Math.min(20, latency / 50));
    }

    private static int menuSlot(int inventoryIndex) {
        return inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
    }
}
