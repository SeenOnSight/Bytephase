package dev.bytephase.module.combat;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;

/**
 * Switches to the strongest sword or axe in the hotbar right before an attack.
 *
 * <p>Damage is read from the item's attack-damage attribute, so enchantments
 * that are not part of that attribute (such as Sharpness) are ignored.</p>
 */
public final class AutoWeapon extends Module {
    private final ModeSetting weapon = add(new ModeSetting("Weapon", new String[]{"Sword", "Axe"}, "Sword"));
    private final NumberSetting threshold = add(new NumberSetting("Threshold", 4.0, 0.0, 20.0, 0.5));
    private final BooleanSetting antiBreak = add(new BooleanSetting("AntiBreak", true));

    public AutoWeapon() {
        super("AutoWeapon", Category.COMBAT, "Picks the best sword or axe in your hotbar");
    }

    @Override
    public void onBeforeAttack(PlayerEntity self, net.minecraft.entity.Entity target) {
        if (!(target instanceof LivingEntity)) {
            return;
        }
        var inventory = self.getInventory();
        int bestSword = -1;
        int bestAxe = -1;
        double swordDamage = 0.0;
        double axeDamage = 0.0;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (skip(stack)) {
                continue;
            }
            double damage = damage(stack);
            if (stack.isIn(ItemTags.SWORDS) && damage > swordDamage) {
                swordDamage = damage;
                bestSword = i;
            } else if (stack.isIn(ItemTags.AXES) && damage > axeDamage) {
                axeDamage = damage;
                bestAxe = i;
            }
        }

        boolean preferSword = weapon.getValue().equals("Sword");
        int preferredSlot = preferSword ? bestSword : bestAxe;
        int otherSlot = preferSword ? bestAxe : bestSword;
        double preferredDamage = preferSword ? swordDamage : axeDamage;
        double otherDamage = preferSword ? axeDamage : swordDamage;

        int chosen = preferredSlot;
        if (otherSlot != -1 && otherDamage - preferredDamage > threshold.getValue()) {
            chosen = otherSlot;
        }
        if (chosen == -1) {
            chosen = otherSlot;
        }
        if (chosen >= 0) {
            inventory.setSelectedSlot(chosen);
        }
    }

    private boolean skip(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        return antiBreak.getValue() && stack.isDamageable() && stack.getMaxDamage() - stack.getDamage() <= 10;
    }

    private static double damage(ItemStack stack) {
        var modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) {
            return 0.0;
        }
        return modifiers.applyOperations(EntityAttributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
    }
}
