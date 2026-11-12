package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.input.KeyOverride;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.MaceUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;

/**
 * Chains spear Lunge jabs using the attribute-swap trick.
 *
 * <p>The server refreshes your attack-speed attribute from your equipment only
 * once per tick. A jab sent in the same tick as a hotbar swap is therefore
 * judged against the item you were holding <em>before</em> the swap. So the
 * macro parks on the hotbar item with the shortest attack cooldown (an empty
 * slot or any non-weapon is 5 ticks), and each lunge is: select the spear,
 * jab, select the parked slot again, all within one client tick. The Lunge
 * enchantment still comes from the spear that was in hand for the jab, but
 * the cooldown that gates the next jab is the parked item's.</p>
 */
public final class AutoLunge extends Module {
    private final ModeSetting trigger = add(new ModeSetting("Trigger",
            new String[]{"AttackKey", "Always"}, "AttackKey"));
    private final BooleanSetting requireLunge = add(new BooleanSetting("RequireLunge", true));
    private final NumberSetting padding = add(new NumberSetting("Padding", 0.0, 0.0, 5.0, 1.0));
    private final NumberSetting minHunger = add(new NumberSetting("MinHunger", 6.0, 0.0, 20.0, 1.0));
    private final BooleanSetting restoreSlot = add(new BooleanSetting("RestoreSlot", true));

    /** The server needs a full tick to see the parked item before the first lunge. */
    private static final int PARK_TICKS = 2;

    private long tick;
    private long parkedAt = Long.MIN_VALUE / 2;
    private long lastLunge = Long.MIN_VALUE / 2;
    private int originSlot = -1;
    private boolean holdingKey;

    public AutoLunge() {
        super("AutoLunge", Category.COMBAT, "Attribute-swap spear lunges using your lowest-cooldown hotbar item");
    }

    @Override
    protected void onDisable() {
        release();
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.getNetworkHandler() == null || mc.currentScreen != null || mc.player.isDead()
                || mc.player.isSpectator()) {
            release();
            return;
        }
        tick++;
        boolean wanted = trigger.getValue().equals("Always")
                || KeyOverride.isPhysicallyPressed(mc.options.attackKey);
        int spear = wanted ? findSpear() : -1;
        if (spear < 0) {
            release();
            return;
        }
        int park = findParkSlot(spear);
        if (park < 0) {
            release();
            return;
        }
        // While the macro owns the attack key the vanilla click handling must
        // not also swing whatever is parked in hand.
        if (!holdingKey) {
            holdingKey = true;
        }
        KeyOverride.set(this, mc.options.attackKey, false);
        int selected = mc.player.getInventory().getSelectedSlot();
        if (originSlot < 0) {
            originSlot = selected;
        }
        if (selected != park) {
            MaceUtil.selectNow(mc, park);
            parkedAt = tick;
            return;
        }
        if (tick - parkedAt < PARK_TICKS) {
            return;
        }
        if (!canLunge(mc)) {
            return;
        }
        int interval = (int) Math.ceil(20.0 / attackSpeed(mc.player.getInventory().getStack(park)))
                + padding.asInt();
        if (tick - lastLunge < interval) {
            return;
        }
        lunge(spear, park);
        lastLunge = tick;
    }

    private boolean canLunge(net.minecraft.client.MinecraftClient mc) {
        var player = mc.player;
        return player.getHungerManager().getFoodLevel() >= minHunger.asInt()
                && !player.isTouchingWater() && !player.isGliding() && !player.hasVehicle();
    }

    /** Select the spear, jab, and select the parked slot again, all inside this tick. */
    private void lunge(int spearSlot, int parkSlot) {
        var mc = BytePhase.mc;
        ItemStack spear = mc.player.getInventory().getStack(spearSlot);
        PiercingWeaponComponent weapon = spear.get(DataComponentTypes.PIERCING_WEAPON);
        if (weapon == null) {
            return;
        }
        // attackWithPiercingWeapon syncs the selected slot, sends the STAB
        // action and applies the Lunge effect client-side for the held spear.
        mc.player.getInventory().setSelectedSlot(spearSlot);
        mc.interactionManager.attackWithPiercingWeapon(weapon);
        mc.player.swingHand(Hand.MAIN_HAND);
        MaceUtil.selectNow(mc, parkSlot);
    }

    private int findSpear() {
        var inventory = BytePhase.mc.player.getInventory();
        int best = -1;
        int bestLevel = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.get(DataComponentTypes.PIERCING_WEAPON) == null) {
                continue;
            }
            int level = MaceUtil.level(stack, Enchantments.LUNGE);
            if (requireLunge.getValue() && level <= 0) {
                continue;
            }
            if (level > bestLevel) {
                best = slot;
                bestLevel = level;
            }
        }
        return best;
    }

    /** The hotbar slot (other than the spear) whose item recovers its attack the fastest. */
    private int findParkSlot(int spearSlot) {
        var inventory = BytePhase.mc.player.getInventory();
        int selected = inventory.getSelectedSlot();
        int best = -1;
        double bestSpeed = -1.0;
        for (int slot = 0; slot < 9; slot++) {
            if (slot == spearSlot) {
                continue;
            }
            double speed = attackSpeed(inventory.getStack(slot));
            // Prefer the slot we are already on when speeds tie, to avoid a pointless swap.
            if (speed > bestSpeed + 1.0e-6 || Math.abs(speed - bestSpeed) <= 1.0e-6 && slot == selected) {
                best = slot;
                bestSpeed = speed;
            }
        }
        return best;
    }

    /** Attack speed with this item in the main hand: the base 4.0 plus its modifiers. */
    private static double attackSpeed(ItemStack stack) {
        double[] speed = {4.0};
        if (!stack.isEmpty()) {
            stack.applyAttributeModifiers(EquipmentSlot.MAINHAND,
                    (RegistryEntry<EntityAttribute> attribute, EntityAttributeModifier modifier) -> {
                        if (attribute.equals(EntityAttributes.ATTACK_SPEED)) {
                            speed[0] += modifier.value();
                        }
                    });
        }
        return Math.max(0.1, speed[0]);
    }

    private void release() {
        var mc = BytePhase.mc;
        if (holdingKey || originSlot >= 0) {
            KeyOverride.releaseAll(this);
            holdingKey = false;
        }
        if (originSlot >= 0 && mc != null && mc.player != null && restoreSlot.getValue()
                && mc.player.getInventory().getSelectedSlot() != originSlot && mc.getNetworkHandler() != null) {
            MaceUtil.selectNow(mc, originSlot);
        }
        originSlot = -1;
        parkedAt = Long.MIN_VALUE / 2;
    }
}
