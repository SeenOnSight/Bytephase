package dev.bytephase.util;

import dev.bytephase.BytePhase;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.ExplosionImpl;

/**
 * Estimates the damage an explosion of a given power deals to an entity,
 * following vanilla's formula: block exposure, distance falloff, difficulty,
 * armor, Protection/Blast Protection and Resistance.
 *
 * <p>End crystals have power 6; respawn anchors (outside the Nether) and beds
 * have power 5.</p>
 */
public final class ExplosionDamage {
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static Registry<Enchantment> cachedRegistry;
    private static RegistryEntry<Enchantment> protection;
    private static RegistryEntry<Enchantment> blastProtection;

    private ExplosionDamage() {
    }

    /** Largest damage this power can deal at {@code distance} (full exposure, no armor). */
    public static boolean inRange(Vec3d center, Vec3d entityPos, float power) {
        return center.squaredDistanceTo(entityPos) < (double) (power * 2.0f) * (power * 2.0f);
    }

    /** Estimated damage to {@code entity} from an explosion at {@code center}. */
    public static double estimate(Vec3d center, LivingEntity entity, float power) {
        var mc = BytePhase.mc;
        if (mc == null || mc.world == null) {
            return 0.0;
        }
        if (entity.isInvulnerable() || entity instanceof PlayerEntity player && player.isCreative()) {
            return 0.0;
        }
        double radius = power * 2.0;
        double distance = Math.sqrt(entity.getEntityPos().squaredDistanceTo(center));
        if (distance >= radius) {
            return 0.0;
        }
        double impact = (1.0 - distance / radius) * ExplosionImpl.calculateReceivedDamage(center, entity);
        if (impact <= 0.0) {
            return 0.0;
        }
        double damage = (impact * impact + impact) / 2.0 * 7.0 * radius + 1.0;
        if (entity instanceof PlayerEntity) {
            damage = switch (mc.world.getDifficulty()) {
                case PEACEFUL -> 0.0;
                case EASY -> Math.min(damage / 2.0 + 1.0, damage);
                case HARD -> damage * 1.5;
                default -> damage;
            };
        }
        DamageSource source = mc.world.getDamageSources().explosion(null, null);
        damage = DamageUtil.getDamageLeft(entity, (float) damage, source,
                (float) entity.getAttributeValue(EntityAttributes.ARMOR),
                (float) entity.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS));
        damage *= 1.0f - Math.min(20, protectionPoints(entity)) / 25.0f;
        StatusEffectInstance resistance = entity.getStatusEffect(StatusEffects.RESISTANCE);
        if (resistance != null) {
            damage *= Math.max(0.0f, 1.0f - 0.2f * (resistance.getAmplifier() + 1));
        }
        return damage;
    }

    private static int protectionPoints(LivingEntity entity) {
        Registry<Enchantment> registry = BytePhase.mc.world.getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT);
        if (registry != cachedRegistry) {
            cachedRegistry = registry;
            protection = registry.getEntry(Enchantments.PROTECTION.getValue()).orElse(null);
            blastProtection = registry.getEntry(Enchantments.BLAST_PROTECTION.getValue()).orElse(null);
        }
        int points = 0;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = entity.getEquippedStack(slot);
            if (protection != null) {
                points += EnchantmentHelper.getLevel(protection, stack);
            }
            if (blastProtection != null) {
                points += 2 * EnchantmentHelper.getLevel(blastProtection, stack);
            }
        }
        return points;
    }
}
