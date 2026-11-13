package dev.bytephase.pvp.module.impl.combat.totem;

import dev.bytephase.pvp.module.impl.combat.crystal.CrystalDamage;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public final class DoubleHand {
    private static final float DANGER_HEALTH = 6.0F;
    private static final double DANGER_RANGE = 6.0;
    private static final int SAFE_TICKS = 10;
    private boolean holding;
    private boolean rearmed = true;
    private int holdSlot = -1;
    private int returnSlot = -1;
    private int safeTicks;

    public boolean holding() {
        return this.holding;
    }

    public void tick(Minecraft mc, LocalPlayer player, boolean on) {
        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        boolean mainTotem = player.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
        boolean danger = on && inDanger(mc.level, player);
        this.safeTicks = danger ? 0 : Math.min(this.safeTicks + 1, 10);
        if (this.safeTicks == 10) {
            this.holding = false;
            this.rearmed = true;
        }

        if (this.holding && selected != this.holdSlot && !mainTotem) {
            this.holding = false;
            this.rearmed = false;
            this.returnSlot = -1;
        }

        boolean clean = mc.screen == null && !player.isUsingItem() && !MeleeAttacks.actedThisTick(player);
        if (this.holding) {
            if (mainTotem || !clean) {
                return;
            }

            int next = hotbarTotem(inventory);
            if (next != -1) {
                this.holdSlot = next;
                inventory.setSelectedSlot(next);
                return;
            }

            this.holding = false;
        } else if (danger && this.rearmed) {
            if (mainTotem) {
                this.holding = true;
                if (selected != this.holdSlot) {
                    this.returnSlot = -1;
                }

                this.holdSlot = selected;
                return;
            }

            int slot = hotbarTotem(inventory);
            if (slot != -1 && clean && player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                this.holding = true;
                this.holdSlot = slot;
                this.returnSlot = selected;
                inventory.setSelectedSlot(slot);
                return;
            }
        }

        if (this.returnSlot != -1 && clean) {
            this.putBack(inventory);
        }
    }

    public void putBack(Inventory inventory) {
        if (this.returnSlot != -1) {
            if (inventory.getSelectedSlot() == this.holdSlot) {
                inventory.setSelectedSlot(this.returnSlot);
            }

            this.returnSlot = -1;
        }
    }

    public void reset() {
        this.holding = false;
        this.rearmed = true;
        this.holdSlot = -1;
        this.returnSlot = -1;
        this.safeTicks = 0;
    }

    private static boolean inDanger(ClientLevel level, LocalPlayer player) {
        float health = CrystalDamage.health(player);
        if (health <= 6.0F) {
            return true;
        }

        for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, player.getBoundingBox().inflate(6.0))) {
            if (player.distanceTo(crystal) <= 6.0 && CrystalDamage.estimate(crystal.position(), player) >= health) {
                return true;
            }
        }

        return false;
    }

    private static int hotbarTotem(Inventory inventory) {
        int selected = inventory.getSelectedSlot();

        for (int i = 0; i < 9; i++) {
            if (i != selected && inventory.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }

        return inventory.getItem(selected).is(Items.TOTEM_OF_UNDYING) ? selected : -1;
    }
}
