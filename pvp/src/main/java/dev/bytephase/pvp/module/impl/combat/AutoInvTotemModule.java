package dev.bytephase.pvp.module.impl.combat;

import dev.bytephase.pvp.mixin.AccessorElytraFlyMultiPlayerGameMode;
import dev.bytephase.pvp.mixin.AccessorLocalPlayer;
import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.EventPriority;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.CombatMacroLock;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.ModuleRegistry;
import dev.bytephase.pvp.module.impl.combat.totem.DoubleHand;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import dev.bytephase.pvp.module.setting.ModeSetting;
import dev.bytephase.pvp.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public final class AutoInvTotemModule extends ClientModule {
    private static final String INVENTORY = "Inventory";
    private static final String HOTBAR_SWAP = "Hotbar Swap";
    private static final int MIN_CONFIRM_TICKS = 10;
    private static final int MAX_CONFIRM_TICKS = 40;
    private static final int PASS_COOLDOWN_TICKS = 4;
    private static final int GIVE_UP_COOLDOWN_TICKS = 10;
    private static final int MAX_PASS_SWAPS = 2;
    private static final int MAX_RESELECTS = 2;
    private static final int MAX_STEPS_PER_TICK = 3;
    private static final int MAX_UNSAFE_TICKS = 10;
    private static AutoInvTotemModule instance;
    private final ModeSetting mode = this.addSetting("mode", new ModeSetting("Mode", "Inventory", "Inventory", "Hotbar Swap"));
    private final BooleanSetting hotbarRefill = this.addSetting("hotbar-refill", new BooleanSetting("Hotbar Refill", false));
    private final NumberSetting hotbarSlot = this.addSetting("hotbar-slot", new NumberSetting("Hotbar Slot", 1.0F, 1.0F, 9.0F).step(1.0F));
    private final NumberSetting reactionDelay = this.addSetting("reaction-delay", new NumberSetting("Reaction Delay", 0.0F, 0.0F, 10.0F).step(1.0F));
    private final NumberSetting swapDelay = this.addSetting("swap-delay", new NumberSetting("Swap Delay", 1.0F, 0.0F, 10.0F).step(1.0F));
    private final NumberSetting closeDelay = this.addSetting("close-delay", new NumberSetting("Close Delay", 0.0F, 0.0F, 10.0F).step(1.0F));
    private final NumberSetting switchBackDelay = this.addSetting("switch-back-delay", new NumberSetting("Switch Back Delay", 2.0F, 0.0F, 10.0F).step(1.0F));
    private final BooleanSetting doubleHand = this.addSetting("double-hand", new BooleanSetting("Double Hand", false));
    private final DoubleHand totemHand = new DoubleHand();
    private AutoInvTotemModule.Step step = AutoInvTotemModule.Step.IDLE;
    private int tick;
    private int readyAt;
    private int reactionAt = -1;
    private int idleUntil;
    private int confirmDeadline = -1;
    private int clickedAt = -1;
    private int swappedAt = -1;
    private boolean openedInventory;
    private int passSwaps;
    private int unsafeTicks;
    private int totemSlot = -1;
    private int previousSlot = -1;
    private int reselects;

    public AutoInvTotemModule() {
        super(
            "autoinvtotem",
            "Auto Inventory Totem",
            "Refills the offhand totem after a pop through the inventory or a hotbar swap; Double Hand also keeps one in the main hand while in danger.",
            ModuleCategory.COMBAT
        );
        instance = this;
        this.hotbarSlot.visibleWhen(this.hotbarRefill::get);
        this.switchBackDelay.visibleWhen(() -> this.mode.is("Hotbar Swap"));
    }

    public static boolean wantsHotbar() {
        AutoInvTotemModule module = instance;
        return module != null && module.isEnabled()
            ? module.step == AutoInvTotemModule.Step.HOTBAR_SWAP || module.step == AutoInvTotemModule.Step.HOTBAR_RETURN || module.totemHand.holding()
            : AutoTotemModule.wantsHotbar();
    }

    public static boolean busy() {
        AutoInvTotemModule module = instance;
        if (module != null && module.isEnabled()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && !player.isSpectator() && !player.hasInfiniteMaterials()) {
                Inventory inventory = player.getInventory();
                return module.step != AutoInvTotemModule.Step.IDLE
                    || module.totemHand.holding()
                    || module.awaitingServer(player)
                    || module.clickedAt == module.tick
                    || player.getOffhandItem().isEmpty() && (findStoredTotem(inventory) != -1 || module.hotbarSource(inventory) != -1);
            } else {
                return false;
            }
        } else {
            return AutoTotemModule.busy();
        }
    }

    public static boolean clickSafe(LocalPlayer player) {
        AccessorLocalPlayer sent = (AccessorLocalPlayer)player;
        Input input = sent.pvp$lastSentInput();
        return !sent.pvp$wasSprinting() && !input.forward() && !input.backward() && !input.left() && !input.right() && !input.jump() && !input.shift();
    }

    @Override
    protected void onEnable() {
        AutoTotemModule other = ModuleRegistry.get(AutoTotemModule.class);
        if (other != null) {
            other.setEnabled(false);
        }

        this.idleUntil = 0;
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) {
            Inventory inventory = player.getInventory();
            if (this.openedInventory && mc.screen instanceof InventoryScreen) {
                player.closeContainer();
            }

            if ((this.step == AutoInvTotemModule.Step.HOTBAR_SWAP || this.step == AutoInvTotemModule.Step.HOTBAR_RETURN) && mc.screen == null) {
                this.restoreSlot(inventory);
            }

            this.totemHand.putBack(inventory);
        }

        this.reset();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTick(TickEvent event) {
        this.tick++;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null
            && mc.level != null
            && mc.gameMode != null
            && mc.getConnection() != null
            && !player.isDeadOrDying()
            && !player.isSpectator()
            && !player.hasInfiniteMaterials()) {
            if (this.step == AutoInvTotemModule.Step.IDLE) {
                this.tryStart(mc, player);
            }

            if (this.step == AutoInvTotemModule.Step.IDLE) {
                this.totemHand.tick(mc, player, this.doubleHand.get());
            }

            for (int i = 0; i < 3 && this.step != AutoInvTotemModule.Step.IDLE && this.tick >= this.readyAt; i++) {
                switch (this.step) {
                    case INVENTORY_SWAP:
                        this.inventorySwap(mc, player);
                        break;
                    case INVENTORY_CLOSE:
                        this.closeInventory(mc, player);
                        break;
                    case HOTBAR_SWAP:
                        this.hotbarSwap(mc, player);
                        break;
                    case HOTBAR_RETURN:
                        this.hotbarReturn(mc, player);
                }
            }
        } else {
            if (player != null && this.openedInventory && mc.screen instanceof InventoryScreen) {
                player.closeContainer();
            }

            this.reset();
        }
    }

    private void tryStart(Minecraft mc, LocalPlayer player) {
        Inventory inventory = player.getInventory();
        if (!player.getOffhandItem().isEmpty()) {
            this.confirmDeadline = -1;
        }

        boolean offhandJob = player.getOffhandItem().isEmpty()
            && !this.awaitingServer(player)
            && (findStoredTotem(inventory) != -1 || this.hotbarSource(inventory) != -1);
        if (!offhandJob && !this.refillDue(mc, player)) {
            this.reactionAt = -1;
        } else {
            if (this.reactionAt < 0) {
                this.reactionAt = this.tick + this.reactionDelay.getInt();
            }

            if (this.tick >= this.reactionAt && this.tick >= this.idleUntil) {
                if (mc.screen instanceof AbstractContainerScreen) {
                    this.startPass(false);
                } else if (mc.screen == null) {
                    boolean canOpen = !mc.gameMode.isServerControlledInventory() && !sneakForced();
                    if (!offhandJob || this.hotbarSource(inventory) == -1 || !this.mode.is("Hotbar Swap") && canOpen) {
                        if (canOpen) {
                            mc.setScreen(new InventoryScreen(player));
                            this.startPass(true);
                        } else {
                            this.idleUntil = this.tick + 10;
                        }
                    } else {
                        this.startHotbarSwap(inventory);
                    }
                }
            }
        }
    }

    private boolean refillDue(Minecraft mc, LocalPlayer player) {
        Inventory inventory = player.getInventory();
        return !player.getOffhandItem().isEmpty() && this.refillWanted(inventory) && findStoredTotem(inventory) != -1
            ? mc.screen instanceof AbstractContainerScreen
                || mc.screen == null && clickSafe(player) && !this.totemHand.holding() && !CombatMacroLock.heldByOther(this)
            : false;
    }

    private boolean refillWanted(Inventory inventory) {
        return this.hotbarRefill.get() && !inventory.getItem(this.hotbarSlot.getInt() - 1).is(Items.TOTEM_OF_UNDYING);
    }

    private static boolean sneakForced() {
        var player = Minecraft.getInstance().player;
        return player != null && player.input.keyPresses.shift();
    }

    private void startPass(boolean opened) {
        this.openedInventory = opened;
        this.passSwaps = 0;
        this.unsafeTicks = 0;
        this.schedule(AutoInvTotemModule.Step.INVENTORY_SWAP, opened ? Math.max(1, this.swapDelay.getInt()) : this.swapDelay.getInt());
    }

    private void inventorySwap(Minecraft mc, LocalPlayer player) {
        if (mc.screen instanceof AbstractContainerScreen<?> screen && (!this.openedInventory || mc.screen instanceof InventoryScreen)) {
            AbstractContainerMenu menu = screen.getMenu();
            if (clickSafe(player) && menu.getCarried().isEmpty()) {
                int source = this.passSource(player);
                if (source != -1 && this.passSwaps < 2) {
                    int button = player.getOffhandItem().isEmpty() ? 40 : this.hotbarSlot.getInt() - 1;
                    menu.findSlot(player.getInventory(), source)
                        .ifPresent(index -> mc.gameMode.handleInventoryMouseClick(menu.containerId, index, button, ClickType.SWAP, player));
                    this.clickedAt = this.tick;
                    this.passSwaps++;
                    if (this.passSource(player) != -1 && this.passSwaps < 2) {
                        this.schedule(AutoInvTotemModule.Step.INVENTORY_SWAP, Math.max(1, this.swapDelay.getInt()));
                    } else {
                        this.schedule(AutoInvTotemModule.Step.INVENTORY_CLOSE, this.closeDelay.getInt());
                    }
                } else {
                    this.schedule(AutoInvTotemModule.Step.INVENTORY_CLOSE, this.closeDelay.getInt());
                }
            } else {
                if (++this.unsafeTicks > 10) {
                    this.schedule(AutoInvTotemModule.Step.INVENTORY_CLOSE, 0);
                } else {
                    this.readyAt = this.tick + 1;
                }
            }
        } else {
            this.finishPass();
        }
    }

    private void closeInventory(Minecraft mc, LocalPlayer player) {
        if (this.passSource(player) != -1 && this.passSwaps < 2 && this.unsafeTicks <= 10 && mc.screen instanceof AbstractContainerScreen) {
            this.schedule(AutoInvTotemModule.Step.INVENTORY_SWAP, Math.max(1, this.swapDelay.getInt()));
        } else {
            if (this.openedInventory && mc.screen instanceof InventoryScreen) {
                if (!clickSafe(player) && ++this.unsafeTicks <= 10) {
                    this.readyAt = this.tick + 1;
                    return;
                }

                player.closeContainer();
            }

            this.finishPass();
        }
    }

    private void finishPass() {
        this.idleUntil = this.tick + 4;
        this.endSequence();
    }

    private int passSource(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        int stored = findStoredTotem(inventory);
        if (player.getOffhandItem().isEmpty()) {
            return stored != -1 ? stored : this.hotbarSource(inventory);
        } else {
            return this.refillWanted(inventory) ? stored : -1;
        }
    }

    private void startHotbarSwap(Inventory inventory) {
        this.totemSlot = this.hotbarSource(inventory);
        this.previousSlot = inventory.getSelectedSlot();
        this.reselects = 0;
        inventory.setSelectedSlot(this.totemSlot);
        this.schedule(AutoInvTotemModule.Step.HOTBAR_SWAP, this.swapDelay.getInt());
    }

    private void hotbarSwap(Minecraft mc, LocalPlayer player) {
        if (mc.screen != null) {
            this.readyAt = this.tick + 1;
        } else if (!player.getOffhandItem().isEmpty()) {
            this.schedule(AutoInvTotemModule.Step.HOTBAR_RETURN, 0);
        } else {
            Inventory inventory = player.getInventory();
            if (inventory.getSelectedSlot() == this.totemSlot && player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                ClientPacketListener connection = mc.getConnection();
                ((AccessorElytraFlyMultiPlayerGameMode)mc.gameMode).pvp$elytraFlySyncSelected();

                while (mc.options.keySwapOffhand.consumeClick()) {
                }

                connection.send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                this.confirmDeadline = this.tick + confirmWindow(connection, player);
                this.swappedAt = this.tick;
                this.schedule(AutoInvTotemModule.Step.HOTBAR_RETURN, this.switchBackDelay.getInt());
            } else {
                int slot = this.hotbarSource(inventory);
                if (slot != -1 && this.reselects++ < 2) {
                    this.totemSlot = slot;
                    inventory.setSelectedSlot(slot);
                    this.schedule(AutoInvTotemModule.Step.HOTBAR_SWAP, this.swapDelay.getInt());
                } else {
                    this.idleUntil = this.tick + 10;
                    this.schedule(AutoInvTotemModule.Step.HOTBAR_RETURN, 0);
                }
            }
        }
    }

    private void hotbarReturn(Minecraft mc, LocalPlayer player) {
        if (mc.screen == null && (this.swappedAt != this.tick || !player.isUsingItem() || player.getUsedItemHand() != InteractionHand.MAIN_HAND)) {
            this.restoreSlot(player.getInventory());
            this.endSequence();
        } else {
            this.readyAt = this.tick + 1;
        }
    }

    private void restoreSlot(Inventory inventory) {
        if (Inventory.isHotbarSlot(this.previousSlot) && this.previousSlot != this.totemSlot && inventory.getSelectedSlot() == this.totemSlot) {
            inventory.setSelectedSlot(this.previousSlot);
        }
    }

    private int hotbarSource(Inventory inventory) {
        int selected = inventory.getSelectedSlot();
        int reserved = this.hotbarRefill.get() ? this.hotbarSlot.getInt() - 1 : -1;
        int fallback = -1;

        for (int i = 0; i < 9; i++) {
            if (i != selected && inventory.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                if (i != reserved) {
                    return i;
                }

                fallback = i;
            }
        }

        if (fallback != -1) {
            return fallback;
        } else {
            return !this.totemHand.holding() && inventory.getItem(selected).is(Items.TOTEM_OF_UNDYING) ? selected : -1;
        }
    }

    private static int findStoredTotem(Inventory inventory) {
        for (int i = 9; i < 36; i++) {
            if (inventory.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }

        return -1;
    }

    private static int confirmWindow(ClientPacketListener connection, LocalPlayer player) {
        PlayerInfo info = connection.getPlayerInfo(player.getUUID());
        int latencyMs = info != null ? info.getLatency() : 0;
        return Mth.clamp(latencyMs / 25 + 4, 10, 40);
    }

    private boolean awaitingServer(LocalPlayer player) {
        return this.confirmDeadline >= 0 && this.tick < this.confirmDeadline && !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    private void schedule(AutoInvTotemModule.Step next, int ticks) {
        this.step = next;
        this.readyAt = this.tick + ticks;
    }

    private void endSequence() {
        this.step = AutoInvTotemModule.Step.IDLE;
        this.reactionAt = -1;
        this.openedInventory = false;
        this.passSwaps = 0;
        this.unsafeTicks = 0;
        this.totemSlot = -1;
        this.previousSlot = -1;
        this.reselects = 0;
    }

    private void reset() {
        this.endSequence();
        this.confirmDeadline = -1;
        this.totemHand.reset();
    }

    @Environment(EnvType.CLIENT)
    private enum Step {
        IDLE,
        INVENTORY_SWAP,
        INVENTORY_CLOSE,
        HOTBAR_SWAP,
        HOTBAR_RETURN;
    }
}
