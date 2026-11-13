package dev.bytephase.pvp.module.impl.combat.melee;

import dev.bytephase.pvp.mixin.AccessorCombatMinecraft;
import dev.bytephase.pvp.mixin.AccessorLocalPlayer;
import dev.bytephase.pvp.mixin.AccessorTriggerbotKeyMapping;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import dev.bytephase.pvp.config.FriendStore;
import dev.bytephase.pvp.module.ModuleRegistry;
import dev.bytephase.pvp.module.impl.combat.ShieldBreakerModule;
import dev.bytephase.pvp.module.impl.misc.AutoEatModule;
import dev.bytephase.pvp.util.pvp.Rotation;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

@Environment(EnvType.CLIENT)
public final class MeleeAttacks {
    public static final float CRIT_CHARGE = 0.9F;
    private static final double LINE_EPSILON_SQ = 0.001;
    private static final int SHIELD_BROKEN_TICKS = 20;
    private static int actorId;
    private static int actionTick = -1;
    private static int attackActorId;
    private static int attackTick = -1;
    private static int shieldBrokenId;
    private static int shieldBrokenUntil;

    private MeleeAttacks() {
    }

    public static void noteAction(Player player) {
        actorId = player.getId();
        actionTick = player.tickCount;
    }

    public static boolean actedThisTick(Player player) {
        return actionTick == player.tickCount && actorId == player.getId();
    }

    public static void noteAttack(Player player) {
        noteAction(player);
        attackActorId = player.getId();
        attackTick = player.tickCount;
    }

    public static boolean attackedThisTick(Player player) {
        return attackTick == player.tickCount && attackActorId == player.getId();
    }

    public static void syncSprint(LocalPlayer player) {
        // Modules can change sprint after the last movement packet. Send that
        // change before the attack, using vanilla's own packet bookkeeping.
        ((AccessorLocalPlayer)player).pvp$sendSprintState();
    }

    public static boolean handsFree(Minecraft mc, LocalPlayer player) {
        return !player.isUsingItem()
            && !player.isHandsBusy()
            && !mc.gameMode.isDestroying()
            && !rowing(player)
            && !clickPending(mc.options.keySwapOffhand)
            && !clickPending(mc.options.keyDrop);
    }

    private static boolean rowing(LocalPlayer player) {
        Input sent = ((AccessorLocalPlayer)player).pvp$lastSentInput();
        return player.getVehicle() instanceof AbstractBoat && (sent.forward() != sent.backward() || sent.left() != sent.right());
    }

    public static boolean clickPending(Minecraft mc) {
        return clickPending(mc.options.keyAttack);
    }

    public static boolean clickPending(KeyMapping key) {
        return ((AccessorTriggerbotKeyMapping)key).pvp$triggerbotClickCount() > 0;
    }

    public static boolean rightClickComing(Minecraft mc) {
        return mc.options.keyUse.isDown() || clickPending(mc.options.keyUse);
    }

    public static void takeAttackClicks(Minecraft mc) {
        KeyMapping attack = mc.options.keyAttack;

        while (attack.consumeClick()) {
        }
    }

    public static void claimTick(Minecraft mc) {
        takeAttackClicks(mc);
        KeyMapping use = mc.options.keyUse;

        while (use.consumeClick()) {
        }

        mc.missTime = Math.max(mc.missTime, 1);
        AccessorCombatMinecraft access = (AccessorCombatMinecraft)mc;
        access.pvp$setRightClickDelay(Math.max(access.pvp$rightClickDelay(), 2));
    }

    public static boolean canAct(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) {
            return false;
        }

        if (AutoEatModule.isEating()) {
            return false;
        }

        return dev.bytephase.pvp.CombatIntegration.services() == null
                || !dev.bytephase.pvp.CombatIntegration.services().isFreecam();
    }

    public static EntityHitResult crosshairHit(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != null && mc.level != null) {
            if (dev.bytephase.pvp.marlow.MarlowCrystal.raycast(player) instanceof EntityHitResult hit && hit.getType() == Type.ENTITY) {
                return lineIsClear(player, player.getEyePosition(), hit.getLocation()) ? hit : null;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public static boolean lineIsClear(LocalPlayer player, Vec3 from, Vec3 to) {
        BlockHitResult block = player.level().clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, player));
        return block.getType() == Type.MISS || block.getLocation().distanceToSqr(from) >= from.distanceToSqr(to) - 0.001;
    }

    public static boolean attack(Minecraft mc, EntityHitResult hit) {
        return attack(mc, hit, 0.0);
    }

    public static boolean attack(Minecraft mc, EntityHitResult hit, double extraRange) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            return false;
        }

        if (mc.missTime > 0 || actedThisTick(player)) {
            return false;
        }

        if (handsFree(mc, player) && !player.isSpectator()) {
            ItemStack held = player.getMainHandItem();
            if (held.has(DataComponents.PIERCING_WEAPON)) {
                return false;
            }

            if (held.isItemEnabled(mc.level.enabledFeatures()) && !player.cannotAttackWithItem(held, 0)) {
                Entity target = hit.getEntity();
                if (target.isRemoved()) {
                    return false;
                }

                AttackRange range = held.get(DataComponents.ATTACK_RANGE);
                boolean inReach = range != null
                    ? range.isInRange(player, hit.getLocation())
                    : player.isWithinEntityInteractionRange(target.getBoundingBox(), extraRange);
                if (!inReach) {
                    return false;
                }

                syncSprint(player);
                mc.gameMode.attack(player, target);
                if (!actedThisTick(player)) {
                    return false;
                }

                player.swing(InteractionHand.MAIN_HAND);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public static boolean stab(Minecraft mc, EntityHitResult hit) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            return false;
        }

        if (mc.missTime > 0 || actedThisTick(player)) {
            return false;
        }

        if (handsFree(mc, player) && !player.isSpectator()) {
            ItemStack held = player.getMainHandItem();
            PiercingWeapon piercing = held.get(DataComponents.PIERCING_WEAPON);
            if (piercing == null) {
                return false;
            }

            if (held.isItemEnabled(mc.level.enabledFeatures()) && !player.cannotAttackWithItem(held, 0)) {
                Entity target = hit.getEntity();
                if (target.isRemoved()) {
                    return false;
                }

                AttackRange range = held.get(DataComponents.ATTACK_RANGE);
                boolean inReach = range != null
                    ? range.isInRange(player, hit.getLocation())
                    : player.isWithinEntityInteractionRange(target.getBoundingBox(), 0.0);
                if (!inReach) {
                    return false;
                }

                syncSprint(player);
                mc.gameMode.piercingAttack(piercing);
                if (!actedThisTick(player)) {
                    return false;
                }

                player.swing(InteractionHand.MAIN_HAND);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public static Entity stabTarget(LocalPlayer player, Predicate<Entity> wanted) {
        AttackRange range = player.entityAttackRange();
        Rotation sent = ServerRotation.sent();
        Vec3 look = sent != null ? Vec3.directionFromRotation(sent.pitch(), sent.yaw()) : player.getViewVector(1.0F);
        Vec3 eye = player.getEyePosition();
        Vec3 start = eye.add(look.scale(range.effectiveMinRange(player)));
        Vec3 end = eye.add(look.scale(range.effectiveMaxRange(player) + Math.max(0.0, player.getKnownMovement().dot(look))));
        BlockHitResult wall = player.level().clip(new ClipContext(eye, end, Block.COLLIDER, Fluid.NONE, player));
        if (wall.getType() != Type.MISS) {
            end = wall.getLocation();
        }

        float margin = range.hitboxMargin();

        for (Entity entity : player.level().getEntities(player, new AABB(eye, end).inflate(margin + 1.0), wanted)) {
            AABB box = entity.getBoundingBox().inflate(margin);
            if (box.contains(start) || box.clip(start, end).isPresent()) {
                return entity;
            }
        }

        return null;
    }

    private static boolean isHittable(LocalPlayer self, Entity entity) {
        if (!(entity instanceof LivingEntity living && entity != self && !(entity instanceof ArmorStand))) {
            return false;
        } else {
            return living.isAlive() && entity.isAttackable() && self.getVehicle() != entity
                ? !(entity instanceof Player player && (player.isSpectator() || player.isCreative()))
                : false;
        }
    }

    public static boolean isEnemy(LocalPlayer self, Entity entity) {
        return isHittable(self, entity) && !isFriend(entity)
            ? !(entity instanceof Player player && (!self.canHarmPlayer(player) || self.connection.getPlayerInfo(player.getUUID()) == null))
            : false;
    }

    public static boolean isHostile(LivingEntity entity) {
        return entity instanceof Enemy && (!(entity instanceof NeutralMob) || entity instanceof Mob mob && mob.isAggressive());
    }

    public static float charge(Player player) {
        return player.getAttackStrengthScale(0.5F);
    }

    public static int ticksToFull(Player player) {
        return Math.max(0, Mth.ceil((1.0F - charge(player)) * player.getCurrentItemAttackStrengthDelay()));
    }

    public static boolean airborne(Player player) {
        return !player.onGround()
            && !player.onClimbable()
            && !player.isInWater()
            && !player.isMobilityRestricted()
            && !player.isPassenger()
            && !player.getAbilities().flying
            && !player.isFallFlying();
    }

    public static boolean wouldCrit(Player player, Entity target) {
        return charge(player) > 0.9F && player.fallDistance > 0.0 && critStance(player, target);
    }

    public static boolean critStance(Player player, Entity target) {
        return !player.onGround()
            && !player.onClimbable()
            && !player.isInWater()
            && !player.isMobilityRestricted()
            && !player.isPassenger()
            && !player.isSprinting()
            && target instanceof LivingEntity;
    }

    public static boolean shieldBlocks(LivingEntity target, Player attacker) {
        return shieldBlocks(target, attacker, target.getItemBlockingWith());
    }

    public static boolean shieldBlocks(LivingEntity target, Player attacker, ItemStack shield) {
        if (shield == null) {
            return false;
        }

        BlocksAttacks blocks = shield.get(DataComponents.BLOCKS_ATTACKS);
        if (blocks == null) {
            return false;
        }

        DamageSource source = attacker.level().damageSources().playerAttack(attacker);
        if (blocks.bypassedBy().map(tag -> source.is((TagKey<DamageType>)tag)).orElse(false)) {
            return false;
        }

        Vec3 facing = target.calculateViewVector(0.0F, target.getYHeadRot());
        Vec3 toAttacker = attacker.position().subtract(target.position());
        Vec3 flat = new Vec3(toAttacker.x, 0.0, toAttacker.z).normalize();
        double angle = Math.acos(Mth.clamp(flat.dot(facing), -1.0, 1.0));
        return blocks.resolveBlockedDamage(source, 1.0F, angle) > 0.0F;
    }

    public static boolean wastedOnShield(LocalPlayer self, LivingEntity target) {
        if (shieldBlocks(target, self) && !shieldBroken(target) && !disablesShields(self.getMainHandItem())) {
            ShieldBreakerModule breaker = ModuleRegistry.get(ShieldBreakerModule.class);
            if (breaker != null && breaker.isEnabled()) {
                Inventory inventory = self.getInventory();

                for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
                    if (disablesShields(inventory.getItem(slot))) {
                        return false;
                    }
                }

                return true;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    public static void noteShieldBroken(LivingEntity target) {
        shieldBrokenId = target.getId();
        shieldBrokenUntil = target.tickCount + 20;
    }

    public static boolean shieldBroken(LivingEntity target) {
        return target.getId() == shieldBrokenId && target.tickCount < shieldBrokenUntil;
    }

    public static boolean disablesShields(ItemStack stack) {
        Weapon weapon = stack.get(DataComponents.WEAPON);
        return weapon != null && weapon.disableBlockingForSeconds() > 0.0F;
    }

    public static boolean isMeleeWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS) || stack.is(Items.MACE) || stack.is(Items.TRIDENT);
    }

    public static double attackDamage(ItemStack stack) {
        double[] total = new double[]{0.0};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (Attributes.ATTACK_DAMAGE.equals(attribute) && modifier.operation() == Operation.ADD_VALUE) {
                total[0] += modifier.amount();
            }
        });
        return total[0];
    }

    public static boolean isFriend(Entity entity) {
        return entity instanceof Player player && FriendStore.getInstance().isFriend(player.getGameProfile().name());
    }

    public static int spread(int maxTicks) {
        return maxTicks > 0 ? ThreadLocalRandom.current().nextInt(maxTicks + 1) : 0;
    }
}
