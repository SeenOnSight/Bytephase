package dev.bytephase.pvp.module.impl.combat.melee;

import dev.bytephase.pvp.module.CombatMacroLock;
import dev.bytephase.pvp.module.impl.combat.AutoInvTotemModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;

@Environment(EnvType.CLIENT)
public final class SlotReturn {
    private static final int NONE = -1;
    private final Object owner;
    private LocalPlayer playerOwner;
    private int previous = -1;
    private int taken = -1;

    public SlotReturn(Object owner) {
        this.owner = owner;
    }

    public boolean take(LocalPlayer player, int slot) {
        return this.take(player, slot, player.getInventory().getSelectedSlot());
    }

    /** An action bind can capture its return slot before vanilla handles that key. */
    public boolean take(LocalPlayer player, int slot, int returnSlot) {
        if (!Inventory.isHotbarSlot(slot) || !Inventory.isHotbarSlot(returnSlot)) return false;
        if (this.holding() && this.playerOwner != player) this.finish(null, false);
        Inventory inventory = player.getInventory();
        if (inventory.getSelectedSlot() == slot && returnSlot == slot && !this.holding()) {
            return true;
        }

        if (Inventory.isHotbarSlot(slot) && !AutoInvTotemModule.wantsHotbar() && CombatMacroLock.tryAcquire(this.owner)) {
            if (!this.holding()) {
                this.previous = returnSlot;
            }

            this.taken = slot;
            this.playerOwner = player;
            inventory.setSelectedSlot(slot);
            return true;
        } else {
            return false;
        }
    }

    public boolean holding() {
        return this.taken != -1;
    }
    public boolean ownsSlot(LocalPlayer player) {
        return this.holding() && player != null && player == this.playerOwner
            && player.getInventory().getSelectedSlot() == this.taken;
    }

    public void tick(Minecraft mc, boolean back) {
        if (this.holding()) {
            LocalPlayer player = mc.player;
            if (player == null || player != this.playerOwner) {
                this.finish(null, false);
            } else if (AutoInvTotemModule.wantsHotbar()) {
                CombatMacroLock.release(this.owner);
            } else if (mc.screen == null && !MeleeAttacks.actedThisTick(player)) {
                this.finish(player, back);
            }
        }
    }

    public void drop(Minecraft mc, boolean back) {
        if (this.holding()) {
            this.finish(mc.player, back);
        }
    }

    private void finish(LocalPlayer player, boolean back) {
        if (back && player != null && player == this.playerOwner
                && player.getInventory().getSelectedSlot() == this.taken
                && Inventory.isHotbarSlot(this.previous) && this.previous != this.taken) {
            player.getInventory().setSelectedSlot(this.previous);
        }

        CombatMacroLock.release(this.owner);
        this.previous = -1;
        this.taken = -1;
        this.playerOwner = null;
    }
}
