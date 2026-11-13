package dev.bytephase.pvp.module.impl.combat;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Optional;
import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.EventPriority;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.CombatMacroLock;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.impl.combat.melee.SlotReturn;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import dev.bytephase.pvp.module.setting.ModeSetting;
import dev.bytephase.pvp.module.setting.NumberSetting;
import dev.bytephase.pvp.util.pvp.InputControl;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class MaceModule extends ClientModule {
    private static final String PICK_DAMAGE = "Damage";
    private static final String PICK_WIND_BURST = "Wind Burst";
    private static final String SMASH_DIRECT = "Direct";
    private static final String SMASH_DELAYED = "Delayed";
    private static final double REACH_MARGIN = 0.35;
    private static final float BREACH_GAIN = 1.15F;
    private final BooleanSetting autoSwap = this.addSetting("auto-swap", new BooleanSetting("Auto Swap", true));
    private final ModeSetting pick = this.addSetting("pick", new ModeSetting("Pick", "Damage", "Damage", "Wind Burst"));
    private final BooleanSetting breachSwap = this.addSetting("breach-swap", new BooleanSetting("Breach Swap", false));
    private final BooleanSetting swapBack = this.addSetting("swap-back", new BooleanSetting("Swap Back", true));
    private final BooleanSetting smashAssist = this.addSetting("smash-assist", new BooleanSetting("Smash Assist", false));
    private final ModeSetting smashTiming = this.addSetting("smash-timing", new ModeSetting("Smash Timing", "Delayed", "Direct", "Delayed"));
    private final NumberSetting minFall = this.addSetting("min-fall", new NumberSetting("Min Fall", 3.0F, 1.5F, 30.0F).step(0.5F));
    private final BooleanSetting mobs = this.addSetting("mobs", new BooleanSetting("Mobs", false));
    private final SlotReturn slots = new SlotReturn(this);
    private boolean smashed;
    private LocalPlayer trackedPlayer;
    private int delayedTargetId = -1;
    private int delayedTick = -1;

    public MaceModule() {
        super("mace", "Mace", "Swaps to the strongest mace for smash hits, with an optional smash assist.", ModuleCategory.COMBAT);
        this.pick.visibleWhen(this.autoSwap::get);
        this.breachSwap.visibleWhen(this.autoSwap::get);
        this.swapBack.visibleWhen(() -> this.autoSwap.get() || this.smashAssist.get());
        this.smashTiming.visibleWhen(this.smashAssist::get);
        this.minFall.visibleWhen(this.smashAssist::get);
        this.mobs.visibleWhen(this.smashAssist::get);
    }

    @Override
    protected void onDisable() {
        this.smashed = false;
        this.trackedPlayer = null;
        this.clearDelay();
        this.slots.drop(Minecraft.getInstance(), this.swapBack.get());
    }

    public void beforeAttack(LocalPlayer player, Entity target) {
        if (this.autoSwap.get() && target instanceof LivingEntity living) {
            if (!MeleeAttacks.shieldBlocks(living, player) || MeleeAttacks.shieldBroken(living)) {
                boolean smash = MaceItem.canSmashAttack(player);
                if (smash || this.breachSwap.get()) {
                    int slot = this.bestMace(player, living, smash);
                    if (slot >= 0) {
                        if (smash
                            || !(expected(player, player.getInventory().getItem(slot), living) < expected(player, player.getMainHandItem(), living) * 1.15F)) {
                            this.slots.take(player, slot);
                        }
                    }
                }
            }
        }
    }

    private int bestMace(LocalPlayer player, LivingEntity target, boolean smash) {
        boolean windBurst = smash && this.pick.is("Wind Burst");
        Inventory inventory = player.getInventory();
        int best = -1;
        boolean bestBurst = false;
        float bestDamage = 0.0F;

        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(Items.MACE) && !stack.nextDamageWillBreak() && (smash || level(stack, Enchantments.BREACH) != 0)) {
                boolean burst = windBurst && level(stack, Enchantments.WIND_BURST) > 0;
                float damage = expected(player, stack, target);
                if (best < 0 || (!bestBurst || burst) && (bestBurst != burst || !(damage <= bestDamage))) {
                    best = slot;
                    bestBurst = burst;
                    bestDamage = damage;
                }
            }
        }

        return best;
    }

    private static float expected(LocalPlayer player, ItemStack stack, LivingEntity target) {
        float charge = MeleeAttacks.charge(player);
        double fall = player.fallDistance;
        float damage = (float)(player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) + MeleeAttacks.attackDamage(stack))
            * (0.2F + 0.8F * charge * charge);
        if (stack.is(Items.MACE) && MaceItem.canSmashAttack(player)) {
            damage += (float)(smashBonus(fall) + 0.5 * level(stack, Enchantments.DENSITY) * fall);
        }

        if (MeleeAttacks.wouldCrit(player, target)) {
            damage *= 1.5F;
        }

        int sharpness = level(stack, Enchantments.SHARPNESS);
        if (sharpness > 0) {
            damage += charge * (0.5F * sharpness + 0.5F);
        }

        return afterArmor(target, damage, level(stack, Enchantments.BREACH));
    }

    private static double smashBonus(double fall) {
        if (fall <= 3.0) {
            return 4.0 * fall;
        } else {
            return fall <= 8.0 ? 12.0 + 2.0 * (fall - 3.0) : 22.0 + fall - 8.0;
        }
    }

    private static float afterArmor(LivingEntity target, float damage, int breach) {
        float armor = target.getArmorValue();
        float toughness = (float)target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float cut = Mth.clamp(armor - damage / (2.0F + toughness / 4.0F), armor * 0.2F, 20.0F) / 25.0F;
        return damage * (1.0F - Mth.clamp(cut - 0.15F * breach, 0.0F, 1.0F));
    }

    private static int level(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        for (Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(enchantment)) {
                return entry.getIntValue();
            }
        }

        return 0;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        // Wind Burst starts another fall without landing. Also rearm when a
        // confirmed smash resets fall distance or the local player changes.
        if (player == null || player != this.trackedPlayer || player.onGround()
                || player.getDeltaMovement().y > 0.0 || !MaceItem.canSmashAttack(player)) {
            this.smashed = false;
            this.clearDelay();
        }
        this.trackedPlayer = player;

        this.slots.tick(mc, this.swapBack.get());
    }

    @EventHandler
    public void onSmashTick(TickEvent event) {
        if (this.smashAssist.get()) {
            this.trySmash(Minecraft.getInstance());
        }
    }

    private void trySmash(Minecraft mc) {
        if (MeleeAttacks.canAct(mc) && !CombatMacroLock.heldByOther(this) && !AutoInvTotemModule.wantsHotbar()) {
            LocalPlayer player = mc.player;
            if (!MeleeAttacks.actedThisTick(player)
                && !MeleeAttacks.clickPending(mc)
                && !MeleeAttacks.rightClickComing(mc)
                && !ServerRotation.pending()
                && mc.missTime <= 0
                && MeleeAttacks.handsFree(mc, player)) {
                if (!(player.getDeltaMovement().y >= 0.0)) {
                    EntityHitResult hit = MeleeAttacks.crosshairHit(mc);
                    if (hit != null && hit.getEntity() instanceof LivingEntity target && MeleeAttacks.isEnemy(player, target)) {
                        if (target instanceof Player || this.mobs.get()) {
                            if (this.smashReady(player, target)) {
                                if (MeleeAttacks.shieldBlocks(target, player) && !MeleeAttacks.shieldBroken(target)) {
                                    int axe = ShieldBreakerModule.bestAxe(player);
                                    if (canHoldOneTick(player, target)
                                        && axe >= 0
                                        && (this.autoSwap.get() || this.swapBack.get())
                                        && this.slots.take(player, axe)) {
                                        if (MeleeAttacks.attack(mc, hit)) {
                                            MeleeAttacks.noteShieldBroken(target);
                                            MeleeAttacks.claimTick(mc);
                                        } else {
                                            this.slots.drop(mc, true);
                                        }
                                    }
                                } else if (this.holdsSmash(player, target)) {
                                    InputControl.dropSprint();
                                    mc.missTime = Math.max(mc.missTime, 1);
                                } else {
                                    if (MeleeAttacks.attack(mc, hit)) {
                                        this.smashed = true;
                                        MeleeAttacks.claimTick(mc);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void noteSmash() {
        this.smashed = true;
        this.clearDelay();
    }

    public boolean smashReady(LocalPlayer player, LivingEntity target) {
        return this.isEnabled()
            && !this.smashed
            && MaceItem.canSmashAttack(player)
            && player.fallDistance >= this.minFall.get()
            && (player.getMainHandItem().is(Items.MACE) || this.autoSwap.get() && this.bestMace(player, target, true) >= 0);
    }

    public boolean holdsSmash(LocalPlayer player, LivingEntity target) {
        if (this.delayedTargetId == target.getId() && player.tickCount > this.delayedTick) {
            return false;
        }
        boolean chargeSoon = MeleeAttacks.charge(player) <= 0.9F && player.getAttackStrengthScale(1.5F) > 0.9F;
        if (canHoldOneTick(player, target) && (this.smashTiming.is("Delayed") || chargeSoon || target.hurtTime > 0 || player.isSprinting())) {
            // Delayed means one tick. Re-evaluating the same delay every tick
            // could postpone the smash until the player had already landed.
            this.delayedTargetId = target.getId();
            this.delayedTick = player.tickCount;
            return true;
        }
        return false;
    }

    private void clearDelay() {
        this.delayedTargetId = -1;
        this.delayedTick = -1;
    }

    private static boolean canHoldOneTick(LocalPlayer player, LivingEntity target) {
        Vec3 motion = player.getDeltaMovement();
        if (!player.level().noCollision(player, player.getBoundingBox().move(motion))) {
            return false;
        }

        double reach = player.entityInteractionRange() - 0.35;
        if (reach <= 0.0) {
            return false;
        }

        Vec3 nextEye = player.getEyePosition().add(motion);
        Vec3 targetStep = target.position().subtract(target.xo, target.yo, target.zo);
        AABB nextBox = target.getBoundingBox().move(targetStep).inflate(target.getPickRadius());
        if (nextBox.contains(nextEye)) {
            return true;
        }

        Optional<Vec3> aim = nextBox.clip(nextEye, nextEye.add(player.getViewVector(1.0F).scale(reach)));
        return aim.isPresent() && MeleeAttacks.lineIsClear(player, nextEye, aim.get());
    }
}
