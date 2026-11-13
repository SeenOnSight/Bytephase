package dev.bytephase.pvp.module.impl.combat;

import dev.bytephase.pvp.event.EventPriority;
import dev.bytephase.pvp.event.Stage;
import dev.bytephase.pvp.event.impl.KeyInputEvent;
import dev.bytephase.pvp.event.impl.MouseInputEvent;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.input.ModuleKeybinds;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.CombatMacroLock;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.impl.combat.crystal.Crystals;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.impl.combat.melee.SlotReturn;
import dev.bytephase.pvp.module.setting.KeySetting;
import dev.bytephase.pvp.util.pvp.InputControl;
import dev.bytephase.pvp.util.pvp.Rotation;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult.Success;
import net.minecraft.world.InteractionResult.SwingSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public final class KeyThrowModule extends ClientModule {
    private static final int BUFFER_TICKS = 10;
    private final KeySetting pearlKey = this.addSetting("pearl-key", new KeySetting("Pearl Key", -1));
    private final KeySetting windChargeKey = this.addSetting("wind-charge-key", new KeySetting("Wind Charge Key", -1));
    private final SlotReturn slots = new SlotReturn(this);
    private Item pending;
    private int pendingTicks;

    public KeyThrowModule() {
        super("key-throw", "Key Throw", "Throws a pearl or a wind charge with a key and swaps back.", ModuleCategory.COMBAT);
    }

    @Override
    protected void onEnable() {
        this.pending = null;
        this.on(KeyInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onKey);
        this.on(MouseInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onMouse);
        this.on(TickEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onTick);
    }

    @Override
    protected void onDisable() {
        this.pending = null;
        this.slots.drop(Minecraft.getInstance(), true);
    }

    private void onKey(KeyInputEvent event) {
        if (event.action == 1) {
            this.onPress(event.key);
        }
    }

    private void onMouse(MouseInputEvent event) {
        if (event.action == 1) {
            this.onPress(1000 + event.button);
        }
    }

    private void onPress(int pressed) {
        if (ModuleKeybinds.isBindable(pressed)) {
            Item item = pressed == this.pearlKey.value() ? Items.ENDER_PEARL : (pressed == this.windChargeKey.value() ? Items.WIND_CHARGE : null);
            Minecraft mc = Minecraft.getInstance();
            if (item != null && mc.screen == null && mc.player != null) {
                this.pending = item;
                this.pendingTicks = 0;
            }
        }
    }

    private void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        this.slots.tick(mc, true);
        if (mc.player == null) {
            this.pending = null;
        } else if (this.pending != null) {
            this.tryThrow(mc, mc.player);
        }
    }

    private void tryThrow(Minecraft mc, LocalPlayer player) {
        if (++this.pendingTicks > 10) {
            this.pending = null;
        } else if (MeleeAttacks.canAct(mc) && MeleeAttacks.handsFree(mc, player) && !MeleeAttacks.actedThisTick(player)) {
            if (!CombatMacroLock.heldByOther(this) && !AutoInvTotemModule.wantsHotbar()) {
                Inventory inventory = player.getInventory();
                boolean offhand = player.getOffhandItem().is(this.pending);
                int slot = player.getMainHandItem().is(this.pending) ? inventory.getSelectedSlot() : Crystals.hotbarSlotOf(inventory, this.pending);
                if (!offhand && slot < 0) {
                    this.pending = null;
                } else {
                    ItemStack stack = offhand ? player.getOffhandItem() : inventory.getItem(slot);
                    if (!player.getCooldowns().isOnCooldown(stack)) {
                        boolean windCharge = this.pending == Items.WIND_CHARGE;
                        if (!ServerRotation.pending() && (!windCharge || ServerRotation.canRotate(player))) {
                            if (offhand || this.slots.take(player, slot)) {
                                this.pending = null;
                                if (windCharge) {
                                    ServerRotation.request(new Rotation(player.getYRot(), 90.0F));
                                    if (player.onGround()) {
                                        InputControl.jump();
                                    }
                                }

                                InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                                if (mc.gameMode.useItem(player, hand) instanceof Success success) {
                                    if (success.swingSource() == SwingSource.CLIENT) {
                                        player.swing(hand);
                                    }

                                    mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
                                }

                                MeleeAttacks.claimTick(mc);
                            }
                        }
                    }
                }
            }
        }
    }
}
