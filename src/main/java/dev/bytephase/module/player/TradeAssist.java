package dev.bytephase.module.player;

import dev.bytephase.BytePhase;
import dev.bytephase.mixin.MerchantScreenAccessor;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SetCursorItemS2CPacket;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.village.MerchantInventory;
import net.minecraft.village.TradeOffer;

/**
 * A manually started batch for the offer selected in the open merchant screen.
 * Successful vanilla predicted clicks need not receive an acknowledgement:
 * without explicit inventory evidence, this stops instead of attempting again.
 */
public final class TradeAssist extends Module {
    private static final int SLOT_COUNT = 39;
    private static final int HOTBAR_START = 30;
    private static final int TIMEOUT_TICKS = 100;

    private final NumberSetting count = add(new NumberSetting("Count", 8, 1, 64, 1));
    private final NumberSetting delay = add(new NumberSetting("TickDelay", 4, 1, 40, 1));
    // Zero disables the cap; both emerald inputs count toward the per-trade cost.
    private final NumberSetting maxCost = add(new NumberSetting("MaxEmeraldCost", 0, 0, 128, 1));

    private ClientPlayerEntity player;
    private ClientWorld world;
    private MerchantScreen screen;
    private MerchantScreenHandler handler;
    private TradeOffer offer;
    private TradeOffer originalOffer;
    private int selectedIndex;
    private int target;
    private int tickDelay;
    private int emeraldLimit;
    private int verified;
    private int expectedUses;
    private int cooldown;
    private int waitingTicks;
    private Phase phase;
    private String stopReason;
    private volatile boolean ownAction;
    private volatile boolean interrupted;
    private volatile Pending pending;

    public TradeAssist() {
        super("TradeAssist", Category.PLAYER, "Manually batches the selected merchant offer");
    }

    @Override
    protected void onEnable() {
        reset();
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || !(mc.currentScreen instanceof MerchantScreen merchant)) {
            stop("Open a villager trade screen, select an offer, then press the TradeAssist bind.");
            return;
        }
        player = mc.player;
        world = mc.world;
        screen = merchant;
        handler = merchant.getScreenHandler();
        target = count.asInt();
        tickDelay = delay.asInt();
        emeraldLimit = maxCost.asInt();
        if (!validSession() || !handler.getCursorStack().isEmpty()) {
            stop("Use a normal merchant screen with an empty cursor.");
            return;
        }
        selectedIndex = ((MerchantScreenAccessor) screen).bytephase$getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= handler.getRecipes().size()) {
            stop("Select an available offer first.");
            return;
        }
        offer = handler.getRecipes().get(selectedIndex);
        originalOffer = offer.copy();
        expectedUses = offer.getUses();
        phase = Phase.SELECT;
    }

    @Override
    protected void onDisable() {
        if (handler != null || stopReason != null) {
            String message = stopReason == null ? "Stopped manually." : stopReason;
            if (offer != null) {
                message += " " + verified + "/" + target + " trades verified.";
                if (phase == Phase.WAIT_TRADE) {
                    message += " Last transfer is unverified; check your inventory before restarting.";
                }
                message += " Remaining inputs stay in vanilla trade slots.";
            }
            var mc = BytePhase.mc;
            if (mc != null && mc.player != null) {
                mc.player.sendMessage(Text.literal("[BytePhase] TradeAssist: " + message), false);
            }
        }
        // No cleanup clicks: a pending transfer or a new screen makes automatic
        // input returns ambiguous. Vanilla retains the slots for the user.
        reset();
    }

    @Override
    public void onTick() {
        if (!validSession()) {
            stop("Merchant screen, player, or world changed.");
            return;
        }
        if (interrupted || !handler.getCursorStack().isEmpty()) {
            stop("Another inventory interaction or server correction interrupted the batch.");
            return;
        }
        if (((MerchantScreenAccessor) screen).bytephase$getSelectedIndex() != selectedIndex
                || selectedIndex >= handler.getRecipes().size()
                || handler.getRecipes().get(selectedIndex) != offer
                || !sameOffer() || offer.getUses() != expectedUses) {
            stop("Selected offer changed.");
            return;
        }

        if (phase == Phase.WAIT_FILL || phase == Phase.WAIT_TRADE) {
            checkPending();
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (offer.isDisabled()) {
            stop("Offer exhausted.");
            return;
        }
        int cost = emeraldCost(offer.getDisplayedFirstBuyItem())
                + emeraldCost(offer.getDisplayedSecondBuyItem());
        if (emeraldLimit > 0 && cost > emeraldLimit) {
            stop("Offer exceeds MaxEmeraldCost.");
            return;
        }
        if (phase == Phase.SELECT) {
            selectAndFill();
            return;
        }

        ItemStack output = handler.getSlot(2).getStack().copy();
        if (!offer.matchesBuyItems(handler.getSlot(0).getStack(), handler.getSlot(1).getStack())
                || !ItemStack.areEqual(output, originalOffer.getSellItem())
                || ((MerchantInventory) handler.getSlot(0).inventory).getTradeOffer() != offer) {
            // Refills only happen after the preceding trade was verified.
            selectAndFill();
            return;
        }
        int destination = emptyHotbarSlot();
        if (destination < 0 || output.getCount() > handler.getSlot(destination).getMaxItemCount(output)) {
            stop("Leave an empty hotbar slot for one complete trade result.");
            return;
        }
        if (!handler.getSlot(2).canTakeItems(player)) {
            stop("Trade result cannot be taken.");
            return;
        }
        phase = Phase.WAIT_TRADE;
        expectedUses++;
        // A vanilla number-key transfer takes one result. QUICK_MOVE can trade
        // repeatedly within one click when vanilla autofill leaves many inputs.
        perform(() -> BytePhase.mc.interactionManager.clickSlot(
                handler.syncId, 2, destination - HOTBAR_START, SlotActionType.SWAP, player));
        if (!isEnabled()) {
            return;
        }
        Pending action = pending;
        if (action == null || action.expected == null
                || !action.required.get(destination) || !action.required.get(0)
                || !ItemStack.areEqual(action.expected.get(destination), output)
                || offer.getUses() != expectedUses || !handler.getCursorStack().isEmpty()) {
            stop("Single-result transfer did not match the expected trade.");
        }
    }

    private void selectAndFill() {
        if (emptyHotbarSlot() < 0) {
            stop("Leave an empty hotbar slot for one complete trade result.");
            return;
        }
        phase = Phase.WAIT_FILL;
        perform(() -> {
            // Same select/fill/packet order as vanilla MerchantScreen.
            handler.setRecipeIndex(selectedIndex);
            handler.switchTo(selectedIndex);
            player.networkHandler.sendPacket(new SelectMerchantTradeC2SPacket(selectedIndex));
        });
        if (!isEnabled()) {
            return;
        }
        if (!offer.matchesBuyItems(handler.getSlot(0).getStack(), handler.getSlot(1).getStack())
                || !ItemStack.areEqual(handler.getSlot(2).getStack(), originalOffer.getSellItem())
                || ((MerchantInventory) handler.getSlot(0).inventory).getTradeOffer() != offer) {
            stop("Missing matching inputs, or vanilla could not safely refill the trade.");
        }
    }

    private void perform(Runnable action) {
        List<ItemStack> before = snapshot();
        Pending next = new Pending(handler.syncId, handler.getRevision());
        pending = next;
        waitingTicks = 0;
        ownAction = true;
        try {
            action.run();
        } finally {
            ownAction = false;
        }
        // A send hook may disable this module synchronously. Do not inspect a
        // handler that onDisable has already released.
        if (!isEnabled() || pending != next || handler == null) {
            return;
        }
        next.expected = snapshot();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!ItemStack.areEqual(before.get(slot), next.expected.get(slot))) {
                next.required.set(slot);
            }
        }
    }

    private void checkPending() {
        Pending action = pending;
        if (action == null || action.overflow) {
            stop("Inventory confirmation was interrupted.");
            return;
        }
        Receipt update;
        while ((update = action.receipts.poll()) != null) {
            if (update.slot < 0) {
                if (!update.cursor.isEmpty() || update.stacks.size() != SLOT_COUNT) {
                    stop("Server inventory differs from the expected transfer.");
                    return;
                }
                for (int slot = 0; slot < SLOT_COUNT; slot++) {
                    if (!confirmSlot(action, slot, update.stacks.get(slot))) {
                        return;
                    }
                }
            } else if (!confirmSlot(action, update.slot, update.stacks.getFirst())) {
                return;
            }
            action.lastRevision = update.revision;
        }
        BitSet missing = (BitSet) action.required.clone();
        missing.andNot(action.confirmed);
        // An unchanged re-selection needs no acknowledgement: selection precedes
        // the first take on the ordered connection. Every changed fill and every
        // trade requires actual server slot data, never just local prediction.
        boolean unchangedFill = phase == Phase.WAIT_FILL && action.required.isEmpty();
        boolean applied = newerRevision(handler.getRevision(), action.baseRevision)
                && (handler.getRevision() == action.lastRevision
                || newerRevision(handler.getRevision(), action.lastRevision));
        if (++waitingTicks >= Math.max(2, tickDelay) && missing.isEmpty()
                && (unchangedFill || applied) && sameSnapshot(action.expected)) {
            boolean traded = phase == Phase.WAIT_TRADE;
            pending = null;
            phase = Phase.READY;
            cooldown = tickDelay;
            if (traded && ++verified >= target) {
                stop("Batch complete.");
            }
            return;
        }
        if (waitingTicks >= TIMEOUT_TICKS) {
            stop("Timed out waiting for server inventory confirmation.");
        }
    }

    private boolean confirmSlot(Pending action, int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT || !ItemStack.areEqual(action.expected.get(slot), stack)) {
            stop("Server inventory differs from the expected transfer.");
            return false;
        }
        action.confirmed.set(slot);
        return true;
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        Pending action = pending;
        if (action == null) {
            return false;
        }
        // The receive hook runs before vanilla, on Netty. Copy evidence into a
        // bounded queue; only onTick reads game state and checks applied revisions.
        Receipt receipt = null;
        if (packet instanceof InventoryS2CPacket inventory && inventory.syncId() == action.syncId
                && newerRevision(inventory.revision(), action.baseRevision)) {
            if (inventory.contents().size() != SLOT_COUNT) {
                action.overflow = true;
                return false;
            }
            receipt = new Receipt(inventory.revision(), -1,
                    inventory.contents().stream().map(ItemStack::copy).toList(), inventory.cursorStack().copy());
        } else if (packet instanceof ScreenHandlerSlotUpdateS2CPacket slot
                && slot.getSyncId() == action.syncId
                && newerRevision(slot.getRevision(), action.baseRevision)) {
            receipt = new Receipt(slot.getRevision(), slot.getSlot(),
                    List.of(slot.getStack().copy()), ItemStack.EMPTY);
        } else if (packet instanceof SetCursorItemS2CPacket cursor && !cursor.contents().isEmpty()) {
            interrupted = true;
        }
        if (receipt != null && !action.receipts.offer(receipt)) {
            action.overflow = true;
        }
        return false;
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (!ownAction && (packet instanceof ClickSlotC2SPacket
                || packet instanceof SelectMerchantTradeC2SPacket
                || packet instanceof CloseHandledScreenC2SPacket)) {
            interrupted = true;
        }
        return false;
    }

    @Override
    public void onServerCorrection() {
        interrupted = true;
    }

    private boolean validSession() {
        var mc = BytePhase.mc;
        return mc != null && mc.player == player && mc.world == world && player != null
                && player.isAlive() && !player.isSpectator() && mc.interactionManager != null
                && mc.currentScreen == screen && handler != null && player.currentScreenHandler == handler
                && handler.slots.size() == SLOT_COUNT && handler.canUse(player)
                && handler.getSlot(0).inventory instanceof MerchantInventory;
    }

    private boolean sameOffer() {
        return offer.getFirstBuyItem().equals(originalOffer.getFirstBuyItem())
                && offer.getSecondBuyItem().equals(originalOffer.getSecondBuyItem())
                && ItemStack.areEqual(offer.getDisplayedFirstBuyItem(), originalOffer.getDisplayedFirstBuyItem())
                && ItemStack.areEqual(offer.getDisplayedSecondBuyItem(), originalOffer.getDisplayedSecondBuyItem())
                && ItemStack.areEqual(offer.getSellItem(), originalOffer.getSellItem())
                && offer.getMaxUses() == originalOffer.getMaxUses()
                && offer.getSpecialPrice() == originalOffer.getSpecialPrice()
                && offer.getDemandBonus() == originalOffer.getDemandBonus();
    }

    private int emptyHotbarSlot() {
        for (int slot = HOTBAR_START; slot < SLOT_COUNT; slot++) {
            if (handler.getSlot(slot).getStack().isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private List<ItemStack> snapshot() {
        return handler.slots.stream().map(slot -> slot.getStack().copy()).toList();
    }

    private boolean sameSnapshot(List<ItemStack> expected) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!ItemStack.areEqual(handler.getSlot(slot).getStack(), expected.get(slot))) {
                return false;
            }
        }
        return handler.getCursorStack().isEmpty();
    }

    private static int emeraldCost(ItemStack stack) {
        return stack.isOf(Items.EMERALD) ? stack.getCount() : 0;
    }

    private static boolean newerRevision(int revision, int baseline) {
        int distance = (revision - baseline) & 0x7fff;
        return distance > 0 && distance < 0x4000;
    }

    private void stop(String reason) {
        stopReason = reason;
        setEnabled(false);
    }

    private void reset() {
        pending = null;
        player = null;
        world = null;
        screen = null;
        handler = null;
        offer = null;
        originalOffer = null;
        phase = null;
        stopReason = null;
        ownAction = false;
        interrupted = false;
        verified = 0;
        cooldown = 0;
        waitingTicks = 0;
    }

    private enum Phase { SELECT, WAIT_FILL, READY, WAIT_TRADE }

    private record Receipt(int revision, int slot, List<ItemStack> stacks, ItemStack cursor) { }

    private static final class Pending {
        final int syncId;
        final int baseRevision;
        final ArrayBlockingQueue<Receipt> receipts = new ArrayBlockingQueue<>(64);
        final BitSet required = new BitSet(SLOT_COUNT);
        final BitSet confirmed = new BitSet(SLOT_COUNT);
        List<ItemStack> expected;
        int lastRevision;
        volatile boolean overflow;

        Pending(int syncId, int baseRevision) {
            this.syncId = syncId;
            this.baseRevision = baseRevision;
            this.lastRevision = baseRevision;
        }
    }
}
