package dev.bytephase.pvp.marlow.crystal;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.client.player.LocalPlayer;

final class AttackDamage {
    private static final int STRENGTH_ENDING_TICKS = 30;

    private AttackDamage() {
    }

    static boolean breaksCrystal(LocalPlayer player) {
        MobEffectInstance weakness;
        double damage = player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        damage += AttackDamage.weaponDamage(player.getMainHandItem());
        MobEffectInstance strength = player.getEffect(MobEffects.STRENGTH);
        if (strength != null && (strength.getDuration() < 0 || strength.getDuration() > 30)) {
            damage += 3.0 * (double)(strength.getAmplifier() + 1);
        }
        if ((weakness = player.getEffect(MobEffects.WEAKNESS)) != null) {
            damage -= 4.0 * (double)(weakness.getAmplifier() + 1);
        }
        return damage > 0.0;
    }

    private static double weaponDamage(ItemStack item) {
        if (item.isEmpty()) {
            return 0.0;
        }
        double[] sum = new double[]{0.0};
        item.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (Attributes.ATTACK_DAMAGE.equals(attribute)) {
                sum[0] = sum[0] + modifier.amount();
            }
        });
        return sum[0];
    }
}

