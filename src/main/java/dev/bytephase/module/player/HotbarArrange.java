package dev.bytephase.module.player;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.module.combat.AutoTotem;
import dev.bytephase.module.combat.ElytraMace;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.SilentRotation;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Assigns item roles to hotbar slots using one vanilla inventory swap at a time.
 * Matching stacks stay in place; only the main inventory supplies missing roles.
 */
public final class HotbarArrange extends Module {
    private static final int HOTBAR_SIZE = 9;
    private static final int QUIET_TICKS = 2;
    private static final String[] ROLES = {
            "Keep", "Sword", "Pickaxe", "Axe", "Totem", "Pearl", "Crystal",
            "Obsidian", "Food", "Bow", "Mace", "WindCharge"
    };

    private final ModeSetting[] slots = new ModeSetting[HOTBAR_SIZE];
    private final NumberSetting delay;

    private ClientPlayerEntity activePlayer;
    private int cooldown;
    private int quietTicks;
    private int nextSlot;
    private boolean swapping;

    public HotbarArrange() {
        super("HotbarArrange", Category.PLAYER, "Arranges hotbar slots by item role");
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            slots[slot] = add(new ModeSetting("Slot" + (slot + 1), ROLES, "Keep"));
        }
        delay = add(new NumberSetting("Delay", 2.0, 0.0, 20.0, 1.0));
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        reset();
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null) {
            reset();
            return;
        }
        if (activePlayer != mc.player) {
            reset();
            activePlayer = mc.player;
        }
        if (!mc.player.isAlive() || mc.player.isSpectator() || mc.currentScreen != null
                || !(mc.player.currentScreenHandler instanceof PlayerScreenHandler handler)
                || handler != mc.player.playerScreenHandler || !handler.getCursorStack().isEmpty()) {
            quietTicks = QUIET_TICKS;
            return;
        }
        if (mc.player.isUsingItem() || mc.options.attackKey.isPressed() || mc.options.useKey.isPressed()
                || mc.interactionManager.isBreakingBlock() || mc.player.hurtTime > 0
                || ElytraMace.isBusy() || SilentRotation.isActive()
                || enabled("Blink") || enabled("FakeLag")) {
            quietTicks = QUIET_TICKS;
            return;
        }
        if (quietTicks > 0) {
            quietTicks--;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        PlayerInventory inventory = mc.player.getInventory();
        if (replenishPending(inventory) || totemPending(inventory)) {
            quietTicks = QUIET_TICKS;
            return;
        }
        int selected = inventory.getSelectedSlot();
        for (int offset = 0; offset < HOTBAR_SIZE; offset++) {
            int target = (nextSlot + offset) % HOTBAR_SIZE;
            String role = slots[target].getValue();
            // Leave both the selected index and the item currently held alone.
            if (target == selected || role.equals("Keep") || matches(inventory.getStack(target), role)) {
                continue;
            }
            int source = findSource(inventory, role);
            if (source < 0) {
                continue;
            }
            nextSlot = (target + 1) % HOTBAR_SIZE;
            cooldown = delay.asInt();
            swapping = true;
            try {
                // Main inventory indices 9..35 are also player screen slot IDs.
                // SWAP's button is the destination hotbar index, never offhand 40.
                mc.interactionManager.clickSlot(handler.syncId, source, target, SlotActionType.SWAP, mc.player);
            } finally {
                swapping = false;
            }
            return;
        }
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        var mc = BytePhase.mc;
        if (!swapping && mc != null && mc.isOnThread()
                && (packet instanceof ClickSlotC2SPacket
                || packet instanceof UpdateSelectedSlotC2SPacket
                || packet instanceof PlayerInteractEntityC2SPacket
                || packet instanceof PlayerInteractBlockC2SPacket
                || packet instanceof PlayerInteractItemC2SPacket
                || packet instanceof PlayerActionC2SPacket)) {
            // Inventory managers and combat actions get a short quiet period.
            quietTicks = QUIET_TICKS;
        }
        return false;
    }

    @Override
    public void onServerCorrection() {
        quietTicks = Math.max(QUIET_TICKS, delay.asInt());
    }

    private int findSource(PlayerInventory inventory, String role) {
        int best = -1;
        int bestCount = 0;
        // Never borrow from another hotbar role, a Keep slot, armor, or offhand.
        for (int source = PlayerScreenHandler.INVENTORY_START; source < PlayerScreenHandler.INVENTORY_END;
                source++) {
            ItemStack candidate = inventory.getStack(source);
            if (matches(candidate, role) && candidate.getCount() > bestCount) {
                best = source;
                bestCount = candidate.getCount();
            }
        }
        return best;
    }

    private boolean replenishPending(PlayerInventory inventory) {
        AutoReplenish replenish = BytePhase.modules == null ? null : BytePhase.modules.get(AutoReplenish.class);
        if (replenish == null || !replenish.isEnabled()) {
            return false;
        }
        int threshold = 16;
        for (var setting : replenish.getSettings()) {
            if (setting instanceof NumberSetting number && setting.getName().equals("Threshold")) {
                threshold = number.asInt();
                break;
            }
        }
        // Yield even during AutoReplenish's cooldown, irrespective of module tick order.
        for (int hotbar = 0; hotbar < HOTBAR_SIZE; hotbar++) {
            ItemStack stack = inventory.getStack(hotbar);
            if (stack.isEmpty() || stack.getCount() > threshold || stack.getCount() >= stack.getMaxCount()) {
                continue;
            }
            for (int source = PlayerScreenHandler.INVENTORY_START; source < PlayerScreenHandler.INVENTORY_END;
                    source++) {
                ItemStack candidate = inventory.getStack(source);
                if (!candidate.isEmpty() && ItemStack.areItemsAndComponentsEqual(candidate, stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean totemPending(PlayerInventory inventory) {
        AutoTotem totem = BytePhase.modules == null ? null : BytePhase.modules.get(AutoTotem.class);
        return totem != null && totem.shouldManageOffhand()
                && !activePlayer.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)
                && findSource(inventory, "Totem") >= 0;
    }

    private static boolean matches(ItemStack stack, String role) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (role) {
            case "Sword" -> stack.isIn(ItemTags.SWORDS);
            case "Pickaxe" -> stack.isIn(ItemTags.PICKAXES);
            case "Axe" -> stack.isIn(ItemTags.AXES);
            case "Totem" -> stack.isOf(Items.TOTEM_OF_UNDYING);
            case "Pearl" -> stack.isOf(Items.ENDER_PEARL);
            case "Crystal" -> stack.isOf(Items.END_CRYSTAL);
            case "Obsidian" -> stack.isOf(Items.OBSIDIAN);
            case "Food" -> stack.get(DataComponentTypes.FOOD) != null;
            case "Bow" -> stack.isOf(Items.BOW);
            case "Mace" -> stack.isOf(Items.MACE);
            case "WindCharge" -> stack.isOf(Items.WIND_CHARGE);
            default -> false;
        };
    }

    private static boolean enabled(String name) {
        Module module = BytePhase.modules == null ? null : BytePhase.modules.getByName(name);
        return module != null && module.isEnabled();
    }

    private void reset() {
        activePlayer = null;
        cooldown = 0;
        quietTicks = 0;
        nextSlot = 0;
        swapping = false;
    }
}
