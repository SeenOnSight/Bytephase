package dev.bytephase.pvp.module.impl.combat.crystal;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;

@Environment(EnvType.CLIENT)
public final class CrystalDamage {
    private static final double REACH = 12.0;
    private static final float SELF_SCALE = 1.15F;
    private static final float SELF_MARGIN = 2.0F;
    private static final EquipmentSlot[] ARMOR = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private CrystalDamage() {
    }

    public static float estimate(Vec3 center, LivingEntity entity) {
        return new CrystalDamage.Victim(entity, Vec3.ZERO).damage(center);
    }

    public static float estimate(Vec3 center, LivingEntity entity, float power, BlockPos ignored) {
        return new CrystalDamage.Victim(entity, Vec3.ZERO).damage(center, power, ignored);
    }

    public static float health(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    public static boolean endangers(Player self, float damage) {
        return !self.isCreative() && damage * 1.15F + 2.0F >= health(self);
    }

    private static float falloff(double exposure) {
        return falloff(exposure, 12.0);
    }

    private static float falloff(double exposure, double diameter) {
        return (float)((exposure * exposure + exposure) / 2.0 * 7.0 * diameter + 1.0);
    }

    private static float protection(LivingEntity entity) {
        Registry<Enchantment> enchantments = entity.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int total = 0;

        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                total += level(enchantments, Enchantments.PROTECTION, stack) + 2 * level(enchantments, Enchantments.BLAST_PROTECTION, stack);
            }
        }

        return total;
    }

    private static int level(Registry<Enchantment> enchantments, ResourceKey<Enchantment> key, ItemStack stack) {
        return enchantments.get(key).map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack)).orElse(0);
    }

    @Environment(EnvType.CLIENT)
    private record Blast(Vec3 center, BlockPos ignored) {
    }

    @Environment(EnvType.CLIENT)
    public static final class Victim {
        private final LivingEntity entity;
        private final Vec3 position;
        private final AABB box;
        private final DamageSource source;
        private final float armor;
        private final float toughness;
        private final float protection;
        private final float resistance;
        private final BlocksAttacks shield;
        private final Map<Vec3, Float> exposures = new HashMap<>();
        private final Map<CrystalDamage.Blast, Float> blastExposures = new HashMap<>();

        public Victim(LivingEntity entity, Vec3 offset) {
            this.entity = entity;
            this.position = entity.position().add(offset);
            this.box = entity.getBoundingBox().move(offset);
            this.source = entity.damageSources().explosion(null, null);
            this.armor = entity.getArmorValue();
            this.toughness = (float)entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            this.protection = CrystalDamage.protection(entity);
            MobEffectInstance effect = entity.getEffect(MobEffects.RESISTANCE);
            this.resistance = effect == null ? 1.0F : Math.max(25 - (effect.getAmplifier() + 1) * 5, 0) / 25.0F;
            ItemStack blocking = entity.getItemBlockingWith();
            this.shield = blocking == null ? null : blocking.get(DataComponents.BLOCKS_ATTACKS);
        }

        public LivingEntity entity() {
            return this.entity;
        }

        public float damage(Vec3 center) {
            return this.damage(center, false);
        }

        public float damage(Vec3 center, boolean baseToCome) {
            return this.reduce(this.raw(center, baseToCome));
        }

        public float raw(Vec3 center, boolean baseToCome) {
            double distance = Math.sqrt(this.position.distanceToSqr(center)) / 12.0;
            if (distance > 1.0) {
                return 0.0F;
            }

            float damage = this.scaled(CrystalDamage.falloff((1.0 - distance) * this.exposure(center, baseToCome)));
            return damage - this.blocked(center, damage);
        }

        public float reduce(float raw) {
            float damage = CombatRules.getDamageAfterAbsorb(this.entity, raw, this.source, this.armor, this.toughness);
            return CombatRules.getDamageAfterMagicAbsorb(damage * this.resistance, this.protection);
        }

        public float damage(Vec3 center, float power, BlockPos ignored) {
            return this.reduce(this.raw(center, power, ignored));
        }

        public float raw(Vec3 center, float power, BlockPos ignored) {
            double diameter = power * 2.0;
            double distance = Math.sqrt(this.position.distanceToSqr(center)) / diameter;
            if (distance > 1.0) {
                return 0.0F;
            }

            float damage = this.scaled(CrystalDamage.falloff((1.0 - distance) * this.blastExposure(center, ignored), diameter));
            return damage - this.blocked(center, damage);
        }

        private float blocked(Vec3 center, float damage) {
            if (this.shield != null && !(damage <= 0.0F)) {
                Vec3 facing = this.entity.calculateViewVector(0.0F, this.entity.getYHeadRot());
                Vec3 toward = center.subtract(this.position);
                Vec3 flat = new Vec3(toward.x, 0.0, toward.z).normalize();
                double angle = Math.acos(Mth.clamp(flat.dot(facing), -1.0, 1.0));
                return this.shield.resolveBlockedDamage(this.source, damage, angle);
            } else {
                return 0.0F;
            }
        }

        public float upperBound(Vec3 center) {
            double distance = Math.sqrt(this.position.distanceToSqr(center)) / 12.0;
            return distance > 1.0 ? 0.0F : this.reduce(this.scaled(CrystalDamage.falloff(1.0 - distance)));
        }

        public float upperBound(Vec3 center, float power) {
            double diameter = power * 2.0;
            double distance = Math.sqrt(this.position.distanceToSqr(center)) / diameter;
            return distance > 1.0 ? 0.0F : this.reduce(this.scaled(CrystalDamage.falloff(1.0 - distance, diameter)));
        }

        private float exposure(Vec3 center, boolean baseToCome) {
            Float known = this.exposures.get(center);
            if (known != null) {
                return known;
            }

            AABB base = baseToCome ? new AABB(BlockPos.containing(center.x, center.y - 0.5, center.z)) : null;
            float seen = !this.openAir(center, null) || base != null && this.hull(center).intersects(base) ? this.seenPercent(center, base, null) : 1.0F;
            this.exposures.put(center, seen);
            return seen;
        }

        private float blastExposure(Vec3 center, BlockPos ignored) {
            CrystalDamage.Blast key = new CrystalDamage.Blast(center, ignored);
            Float known = this.blastExposures.get(key);
            if (known != null) {
                return known;
            }

            float seen = this.openAir(center, ignored) ? 1.0F : this.seenPercent(center, null, ignored);
            this.blastExposures.put(key, seen);
            return seen;
        }

        private AABB hull(Vec3 center) {
            return this.box.minmax(new AABB(center, center));
        }

        private boolean openAir(Vec3 center, BlockPos ignored) {
            Level level = this.entity.level();
            CollisionContext context = CollisionContext.of(this.entity);
            int minX = Mth.floor(Math.min(this.box.minX, center.x));
            int minY = Mth.floor(Math.min(this.box.minY, center.y));
            int minZ = Mth.floor(Math.min(this.box.minZ, center.z));
            int maxX = Mth.floor(Math.max(this.box.maxX, center.x));
            int maxY = Mth.floor(Math.max(this.box.maxY, center.y));
            int maxZ = Mth.floor(Math.max(this.box.maxZ, center.z));
            MutableBlockPos pos = new MutableBlockPos();

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        pos.set(x, y, z);
                        if (!pos.equals(ignored) && !level.getBlockState(pos).getCollisionShape(level, pos, context).isEmpty()) {
                            return false;
                        }
                    }
                }
            }

            return true;
        }

        private float seenPercent(Vec3 center, AABB base, BlockPos ignored) {
            double stepX = 1.0 / ((this.box.maxX - this.box.minX) * 2.0 + 1.0);
            double stepY = 1.0 / ((this.box.maxY - this.box.minY) * 2.0 + 1.0);
            double stepZ = 1.0 / ((this.box.maxZ - this.box.minZ) * 2.0 + 1.0);
            double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
            double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
            if (!(stepX < 0.0) && !(stepY < 0.0) && !(stepZ < 0.0)) {
                Level level = this.entity.level();
                int seen = 0;
                int total = 0;

                for (double fx = 0.0; fx <= 1.0; fx += stepX) {
                    for (double fy = 0.0; fy <= 1.0; fy += stepY) {
                        for (double fz = 0.0; fz <= 1.0; fz += stepZ) {
                            Vec3 from = new Vec3(
                                Mth.lerp(fx, this.box.minX, this.box.maxX) + offsetX,
                                Mth.lerp(fy, this.box.minY, this.box.maxY),
                                Mth.lerp(fz, this.box.minZ, this.box.maxZ) + offsetZ
                            );
                            BlockHitResult hit = level.clip(new ClipContext(from, center, Block.COLLIDER, Fluid.NONE, this.entity));
                            if ((hit.getType() == Type.MISS || hit.getBlockPos().equals(ignored)) && (base == null || !throughBlock(base, from, center))) {
                                seen++;
                            }

                            total++;
                        }
                    }
                }

                return (float)seen / total;
            } else {
                return 0.0F;
            }
        }

        private static boolean throughBlock(AABB block, Vec3 from, Vec3 to) {
            Vec3 end = new Vec3(Mth.lerp(1.0E-7, to.x, from.x), Mth.lerp(1.0E-7, to.y, from.y), Mth.lerp(1.0E-7, to.z, from.z));
            return block.contains(from) || block.clip(from, end).isPresent();
        }

        private float scaled(float damage) {
            if (!(this.entity instanceof Player)) {
                return damage;
            }

            return switch (this.entity.level().getDifficulty()) {
                case PEACEFUL -> 0.0F;
                case EASY -> Math.min(damage / 2.0F + 1.0F, damage);
                case NORMAL -> damage;
                case HARD -> damage * 1.5F;
            };
        }
    }
}
