package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.AimSmoother;
import dev.bytephase.util.RotationUtil;
import dev.bytephase.util.SilentRotation;
import dev.bytephase.util.ServerFacing;
import net.minecraft.entity.Entity;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.explosion.ExplosionImpl;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Places and detonates end crystals using scored player and hostile targets. */
public final class AutoCrystal extends Module {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static Registry<Enchantment> cachedEnchantmentRegistry;
    private static RegistryEntry<Enchantment> protectionEnchantment;
    private static RegistryEntry<Enchantment> blastProtectionEnchantment;
    // Target acquisition can extend beyond the attack/place reach. Crystal
    // damage still uses vanilla exposure and placement remains bounded by the
    // interaction range, so a farther target is selected only when a valid
    // crystal position can actually affect it.
    private final NumberSetting range = add(new NumberSetting("Range", 18.0, 2.0, 24.0, 0.1));
    private final NumberSetting placeRange = add(new NumberSetting("PlaceRange", 4.5, 2.0, 6.0, 0.1));
    private final NumberSetting placeWallsRange = add(new NumberSetting("PlaceWallsRange", 4.5, 2.0, 6.0, 0.1));
    private final NumberSetting breakRange = add(new NumberSetting("BreakRange", 6.0, 2.0, 6.0, 0.1));
    private final NumberSetting breakWallsRange = add(new NumberSetting("BreakWallsRange", 4.5, 2.0, 6.0, 0.1));
    private final NumberSetting minDamage = add(new NumberSetting("MinDamage", 4.0, 1.0, 20.0, 0.5));
    private final NumberSetting facePlaceHealth = add(new NumberSetting("FacePlaceHealth", 8.0, 1.0, 20.0, 0.5));
    private final NumberSetting maxSelfDamage = add(new NumberSetting("MaxSelfDamage", 30.0, 1.0, 84.0, 0.5));
    private final NumberSetting minHealth = add(new NumberSetting("MinHealth", 3.0, 0.0, 20.0, 0.5));
    private final NumberSetting placeDelay = add(new NumberSetting("PlaceDelay", 0.0, 0.0, 10.0, 1.0));
    private final NumberSetting breakDelay = add(new NumberSetting("BreakDelay", 0.0, 0.0, 10.0, 1.0));
    // Crystals attempted per cycle. A cycle is one client tick at the default
    // 20 CyclesPerSecond.
    private final NumberSetting crystalsPerTick = add(new NumberSetting("CrystalsPerTick", 1.0, 1.0, 8.0, 1.0));
    // Above 20 the logic also runs between ticks (on rendered frames), so the
    // rate is no longer locked to the 20 Hz client tick. The effective rate is
    // limited by your frame rate.
    private final NumberSetting cyclesPerSecond = add(new NumberSetting("CyclesPerSecond", 20.0, 5.0, 100.0, 1.0));
    private final NumberSetting predictTicks = add(new NumberSetting("PredictTicks", 0.0, 0.0, 5.0, 1.0));
    private final ModeSetting targetSort = add(new ModeSetting("TargetSort",
            new String[]{"Distance", "Health", "Armor"}, "Distance"));
    private final ModeSetting placePriority = add(new ModeSetting("PlacePriority",
            new String[]{"Balance", "TargetDamage"}, "Balance"));
    private final BooleanSetting place = add(new BooleanSetting("Place", true));
    private final BooleanSetting breakCrystals = add(new BooleanSetting("Break", true));
    private final BooleanSetting throughWalls = add(new BooleanSetting("ThroughWalls", true));
    private final BooleanSetting switchToCrystals = add(new BooleanSetting("Switch", true));
    private final BooleanSetting swapBack = add(new BooleanSetting("SwapBack", false));
    private final BooleanSetting useOffhand = add(new BooleanSetting("UseOffhand", true));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", false));
    private final NumberSetting rotationSpeed = add(new NumberSetting("RotationSpeed", 90.0, 5.0, 360.0, 5.0));
    private final NumberSetting smoothing = add(new NumberSetting("Smoothing", 50.0, 0.0, 100.0, 5.0));
    private final BooleanSetting facePlace = add(new BooleanSetting("FacePlace", true));
    // Offhand crystals can be used while the main hand eats; don't block that
    // normal dual-hand interaction unless the user explicitly enables it.
    private final BooleanSetting pauseEating = add(new BooleanSetting("PauseEating", false));
    private final BooleanSetting pauseUsing = add(new BooleanSetting("PauseUsingItem", false));
    private final BooleanSetting requireAttack = add(new BooleanSetting("RequireAttack", false));
    private final BooleanSetting swing = add(new BooleanSetting("Swing", true));
    private final BooleanSetting targetPlayers = add(new BooleanSetting("TargetPlayers", true));
    private final BooleanSetting targetHostiles = add(new BooleanSetting("TargetHostiles", true));
    // Puts every crystal of a cycle on the single best base (falling back to the
    // next best only if that one will not take) instead of spreading them out.
    private final BooleanSetting focus = add(new BooleanSetting("Focus", true));
    // Prints once a second why candidate bases were rejected.
    private final BooleanSetting debug = add(new BooleanSetting("Debug", false));
    private final BooleanSetting conservative = add(new BooleanSetting("Conservative", false));
    private final BooleanSetting vanillaPackets = add(new BooleanSetting("VanillaPackets", false));

    private static final long SECOND_NANOS = 1_000_000_000L;
    private static final long RETRY_NANOS = 250_000_000L;
    private static final long CONFIRM_NANOS = 2_000_000_000L;
    private final java.util.ArrayDeque<Long> placeTimes = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Long> breakTimes = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Long> explosionTimes = new java.util.ArrayDeque<>();
    private int hudPlaces = -1, hudBreaks = -1, hudExplosions = -1;
    private String hudText;
    private final java.util.Map<Integer, PendingAttack> pendingAttacks = new java.util.HashMap<>();
    private net.minecraft.client.world.ClientWorld trackedWorld;
    private final java.util.Map<BlockPos, Long> pendingPlaces = new java.util.HashMap<>();
    // Delays are counted in cycles (== ticks at 20 CyclesPerSecond).
    private long cycleCount;
    private long placeReadyCycle;
    private long breakReadyCycle;
    private long lastCycleNanos;
    // Debug: [0] base blocked above, [1] pending, [2] out of reach, [3] entity in the way,
    // [4] not enough damage, [5] too much self damage, [6] bases considered, [7] placed
    private final int[] reject = new int[9];
    private long debugNanos;
    // How long an unconfirmed placement reserves its base (and neighbours).
    // Scaled to your latency so a failed placement stops locking out nearby bases.
    private long pendingTimeout = RETRY_NANOS;
    // The base being hammered, and whether the server has confirmed its crystal.
    private BlockPos focusBase;
    private long focusStart;
    private boolean focusConfirmed;
    // Bases that never confirmed, skipped until the stored time.
    private final Map<BlockPos, Long> coolingBases = new HashMap<>();
    // Debug: nearest bases to the target, with the reason each was rejected.
    private final BlockPos[] debugPos = new BlockPos[3];
    private final String[] debugWhy = new String[3];
    private final double[] debugDist = new double[3];
    private Vec3d debugAnchor;
    private final AimSmoother aim = new AimSmoother();
    private int correctionTicks;
    private boolean waitingForBreak;
    // How far off the smoothed aim may be before the interaction is sent. A
    // placement made while still swinging toward the base reads as a failed
    // rotation check on strict servers, so wait for the turn to finish.
    private static final float AIM_TOLERANCE = 8.0f;

    public AutoCrystal() {
        super("AutoCrystal", Category.COMBAT, "Places and breaks crystals with a self-damage limit");
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    private void resetState() {
        correctionTicks = 0;
        waitingForBreak = false;
        cycleCount = 0;
        placeReadyCycle = 0;
        breakReadyCycle = 0;
        lastCycleNanos = 0;
        java.util.Arrays.fill(reject, 0);
        focusBase = null;
        focusConfirmed = false;
        coolingBases.clear();
        aim.reset();
        SilentRotation.clear();
        trackedWorld = null;
        pendingPlaces.clear();
        pendingAttacks.clear();
        placeTimes.clear();
        breakTimes.clear();
        explosionTimes.clear();
        hudPlaces = hudBreaks = hudExplosions = -1;
        hudText = null;
    }

    @Override
    public void onServerCorrection() {
        resetState();
        if (isConservative()) correctionTicks = 10;
    }

    private void stopAiming() {
        aim.reset();
        SilentRotation.clear();
    }

    private static int rate(java.util.ArrayDeque<Long> times, long now) {
        while (!times.isEmpty() && now - times.peekFirst() >= SECOND_NANOS) times.removeFirst();
        return times.size();
    }

    @Override
    public String getHudText() {
        long now = System.nanoTime();
        int places = rate(placeTimes, now), breaks = rate(breakTimes, now), explosions = rate(explosionTimes, now);
        if (hudText == null || places != hudPlaces || breaks != hudBreaks || explosions != hudExplosions) {
            hudPlaces = places;
            hudBreaks = breaks;
            hudExplosions = explosions;
            hudText = getName() + " [P " + places + "/s B " + breaks + "/s X " + explosions + "/s]";
        }
        return hudText;
    }

    /** Release the retry guard when the server confirms this crystal spawned. */
    public void onCrystalSpawn(Vec3d position) {
        if (!isEnabled() || BytePhase.mc == null || BytePhase.mc.world != trackedWorld) return;
        BlockPos base = BlockPos.ofFloored(position).down();
        pendingPlaces.remove(base);
        if (base.equals(focusBase)) {
            focusConfirmed = true;
        }
    }

    /** Conservative retries allow for high latency; Normal retains the short retry window. */
    private long pendingTimeoutNanos() {
        var mc = BytePhase.mc;
        int ping = 0;
        if (mc != null && mc.player != null && mc.getNetworkHandler() != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) ping = entry.getLatency();
        }
        long nanos = (ping * 2L + 100L) * 1_000_000L;
        return isConservative()
                ? Math.max(RETRY_NANOS, Math.min(CONFIRM_NANOS, nanos))
                : Math.max(120_000_000L, Math.min(RETRY_NANOS, nanos));
    }

    /** Associate nearby server explosion packets with our recent crystal attacks. */
    public void onCrystalExplosion(Vec3d center) {
        if (!isEnabled() || BytePhase.mc == null || BytePhase.mc.world != trackedWorld) return;
        long now = System.nanoTime();
        Integer match = null;
        double nearest = 2.25;
        for (var entry : pendingAttacks.entrySet()) {
            PendingAttack attack = entry.getValue();
            double distance = center.squaredDistanceTo(attack.position());
            if (now - attack.time() < CONFIRM_NANOS && distance < nearest) {
                nearest = distance;
                match = entry.getKey();
            }
        }
        if (match != null) {
            pendingAttacks.remove(match);
            explosionTimes.addLast(now);
            rate(explosionTimes, now);
        }
    }

    private record PendingAttack(Vec3d position, long time) { }

    private static final class DamageBudget {
        private double remaining;
        DamageBudget(double limit) { remaining = limit; }
        void reserve(double damage) { remaining = Math.max(0.0, remaining - damage); }
    }

    @Override
    public void onTick() {
        if (!usesVanillaPackets()) tickCycle();
    }

    public boolean usesVanillaPackets() { return vanillaPackets.getValue(); }

    private boolean isConservative() { return conservative.getValue() || usesVanillaPackets(); }

    public void onPreTick() {
        if (usesVanillaPackets()) tickCycle();
    }

    private void tickCycle() {
        if (correctionTicks > 0) {
            correctionTicks--;
            return;
        }
        run(System.nanoTime());
    }

    /** Extra cycles between ticks when CyclesPerSecond is above the 20 Hz tick rate. */
    @Override
    public void onFrame() {
        if (!isConservative() && cyclesPerSecond.getValue() > 20.0) {
            run(System.nanoTime());
        }
    }

    private void run(long now) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null) {
            if (trackedWorld != null) resetState();
            return;
        }
        if (correctionTicks > 0) return;
        if (trackedWorld != mc.world) {
            resetState();
            trackedWorld = mc.world;
        }
        double cps = isConservative() ? Math.min(20.0, cyclesPerSecond.getValue()) : cyclesPerSecond.getValue();
        if (cps != 20.0) {
            // Leave a little slack so ordinary timing jitter never skips a cycle.
            long interval = (long) (SECOND_NANOS / cps);
            if (now - lastCycleNanos < interval * 4 / 5) return;
        }
        lastCycleNanos = now;
        cycleCount++;
        pendingAttacks.values().removeIf(attack -> now - attack.time() >= CONFIRM_NANOS);
        pendingTimeout = pendingTimeoutNanos();
        pendingPlaces.values().removeIf(time -> now - time >= pendingTimeout);
        coolingBases.values().removeIf(until -> now >= until);
        // A focus base whose crystal never showed up did not take; give it a
        // short rest so the next best base is used instead of retrying forever.
        if (focusBase != null && !focusConfirmed && now - focusStart > pendingTimeout) {
            coolingBases.put(focusBase, now + 400_000_000L);
            focusBase = null;
        }
        if (mc.currentScreen != null || mc.player.isDead() || mc.player.isSpectator()
                || usesVanillaPackets() && ServerFacing.hasConflicts()) {
            stopAiming();
            return;
        }
        if (mc.player.getHealth() <= minHealth.getValue()
                || pauseEating.getValue() && mc.player.isUsingItem()
                        && mc.player.getActiveItem().get(DataComponentTypes.FOOD) != null
                || pauseUsing.getValue() && mc.player.isUsingItem()
                || requireAttack.getValue() && !mc.options.attackKey.isPressed()) {
            stopAiming();
            return;
        }
        if (!breakCrystals.getValue() && !place.getValue()) {
            stopAiming();
            return;
        }
        if (!rotate.getValue() && !isConservative()) {
            stopAiming();
        }
        printDebug(now);

        List<TargetData> targets = findTargets();
        if (targets.isEmpty()) {
            stopAiming();
            return;
        }
        DamageProfile selfProfile = damageProfile(mc.player);

        int limit = isConservative() ? 1 : crystalsPerTick.asInt();
        // Reserve the combined estimated self-damage across every request in
        // this batch; individually safe explosions can still be lethal together.
        DamageBudget budget = new DamageBudget(safeSelfDamageLimit());
        // Even a one-crystal batch can break AND place: both requests must
        // share the budget rather than each spending the entire lethal margin.
        if (breakCrystals.getValue() && cycleCount >= breakReadyCycle) {
            waitingForBreak = false;
            int broken = 0;
            while (broken < limit && breakTargetCrystal(targets, selfProfile, budget)) broken++;
            if (broken > 0) {
                breakReadyCycle = cycleCount + Math.max(isConservative() ? 1 : 0, breakDelay.asInt()) + 1;
                if (isConservative()) return;
            }
            if (usesVanillaPackets() && waitingForBreak) return;
        }
        if (place.getValue() && cycleCount >= placeReadyCycle) {
            int placed = 0;
            if (focus.getValue() && !isConservative()) {
                placed = placeFocused(targets, selfProfile, budget, limit);
            } else {
                placed = placeBatch(targets, selfProfile, budget, limit);
            }
            if (placed > 0) placeReadyCycle = cycleCount + Math.max(isConservative() ? 1 : 0, placeDelay.asInt()) + 1;
        }
    }

    /** More targets than this only multiplies the candidate-scoring cost without changing the pick. */
    private static final int MAX_TARGETS = 8;

    private record Candidate(LivingEntity entity, double sortScore) { }

    private List<TargetData> findTargets() {
        var mc = BytePhase.mc;
        double rangeSq = range.getValue() * range.getValue();
        String sort = targetSort.getValue();
        List<Candidate> candidates = new ArrayList<>(MAX_TARGETS + 1);
        // Query the world's spatial entity index around the player instead of
        // walking every loaded entity on every tick. This includes client-made
        // test players and limits the target set to the configured radius.
        Box targetArea = mc.player.getBoundingBox().expand(range.getValue());
        for (Entity entity : mc.world.getOtherEntities(mc.player, targetArea)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            boolean playerTarget = living instanceof PlayerEntity && targetPlayers.getValue();
            boolean hostileTarget = !(living instanceof PlayerEntity)
                    && living.getType().getSpawnGroup() == SpawnGroup.MONSTER && targetHostiles.getValue();
            if ((!playerTarget && !hostileTarget) || living == mc.player || !living.isAlive()
                    || living.isSpectator() || living.squaredDistanceTo(mc.player) > rangeSq) {
                continue;
            }
            double sortScore = switch (sort) {
                case "Health" -> living.getHealth() + living.getAbsorptionAmount();
                case "Armor" -> living instanceof PlayerEntity player ? player.getArmor() : 0.0;
                default -> living.squaredDistanceTo(mc.player);
            };
            // Keep only the best eight, in stable score order. Sorting every
            // entity and allocating a candidate per mob was unnecessary in farms.
            if (candidates.size() == MAX_TARGETS && sortScore >= candidates.getLast().sortScore()) continue;
            int index = candidates.size();
            while (index > 0 && candidates.get(index - 1).sortScore() > sortScore) index--;
            candidates.add(index, new Candidate(living, sortScore));
            if (candidates.size() > MAX_TARGETS) candidates.removeLast();
        }
        // Rank on the cheap score first, then build the (armor, enchantment,
        // effect) damage profile only for the few targets that will be used.
        // In a mob farm this skips dozens of profile builds every tick.
        List<TargetData> targets = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            LivingEntity living = candidate.entity();
            double sortScore = candidate.sortScore();
            Vec3d predicted = living.getEntityPos()
                    .add(living.getVelocity().multiply(predictTicks.getValue()));
            Box bounds = living.getBoundingBox().offset(predicted.subtract(living.getEntityPos()));
            // Vanilla computes explosion distance from the entity's position
            // (at its feet), not the centre of its bounding box.
            Vec3d center = predicted;
            double effectiveHealth = living.getHealth() + living.getAbsorptionAmount();
            double requiredDamage = facePlace.getValue() && effectiveHealth <= facePlaceHealth.getValue()
                    ? 1.0 : minDamage.getValue();
            targets.add(new TargetData(living, center, bounds, requiredDamage, sortScore,
                    damageProfile(living)));
        }
        return targets;
    }

    private boolean breakTargetCrystal(List<TargetData> targets, DamageProfile selfProfile, DamageBudget budget) {
        var mc = BytePhase.mc;
        double visibleRange = effectiveBreakRange();
        double searchRange = isConservative() ? visibleRange : Math.max(visibleRange, breakWallsRange.getValue());
        double rangeSq = searchRange * searchRange;
        Box searchBox = mc.player.getBoundingBox().expand(searchRange);
        long now = System.nanoTime();
        EndCrystalEntity bestCrystal = null;
        double bestSelfDamage = 0.0;
        double bestDamage = Double.NEGATIVE_INFINITY;
        for (var entity : mc.world.getOtherEntities(mc.player, searchBox)) {
            if (!(entity instanceof EndCrystalEntity crystal) || !crystal.isAlive()
                    || crystal.squaredDistanceTo(mc.player) > rangeSq
                    || !withinInteractionRange(crystal.getBoundingBox().getCenter(), crystal.getBlockPos(),
                            visibleRange, breakWallsRange.getValue())) {
                continue;
            }
            PendingAttack pending = pendingAttacks.get(crystal.getId());
            if (pending != null && now - pending.time() < (isConservative() ? pendingTimeout : RETRY_NANOS)) continue;
            Vec3d blast = crystal.getEntityPos();
            double selfDamage = selfDamageAt(blast, selfProfile);
            if (selfDamage > budget.remaining) continue;
            for (TargetData target : targets) {
                if (maximumDamageAt(blast, target.center(), target.damageProfile()) < target.minDamage()) continue;
                double damage = damageAt(blast, target.center(), target.damageProfile(), target.bounds());
                if (damage >= target.minDamage() && damage > bestDamage) {
                    bestCrystal = crystal;
                    bestSelfDamage = selfDamage;
                    bestDamage = damage;
                }
            }
        }
        if (bestCrystal == null) return false;
        waitingForBreak = true;
        Vec3d aimPoint = usesVanillaPackets() ? bestCrystal.getBoundingBox().getCenter() : bestCrystal.getEntityPos();
        if ((rotate.getValue() || isConservative()) && !face(aimPoint)) return false;
        if (usesVanillaPackets() && !ServerFacing.hitsEntity(bestCrystal.getBoundingBox(), visibleRange)) return false;
        mc.interactionManager.attackEntity(mc.player, bestCrystal);
        if (swing.getValue() || usesVanillaPackets()) mc.player.swingHand(Hand.MAIN_HAND);
        budget.reserve(bestSelfDamage);
        pendingAttacks.put(bestCrystal.getId(), new PendingAttack(bestCrystal.getEntityPos(), now));
        breakTimes.addLast(now);
        rate(breakTimes, now);
        return true;
    }

    private static final int MAX_PLACEMENTS = 8;

    private record Placement(BlockPos pos, double score, double selfDamage, boolean stale) { }

    private static final String[] WHY = {
            "blocked above", "pending", "out of reach", "entity in the way",
            "not enough damage", "too much self damage", "", "", "cooling down"
    };

    /** Rank once per cycle; recheck budget and reservations after each commit. */
    private int placeBatch(List<TargetData> targets, DamageProfile selfProfile, DamageBudget budget, int limit) {
        List<Placement> ranked = findPlacements(targets, selfProfile, budget);
        int placed = 0;
        for (Placement placement : ranked) {
            if (placed >= limit) break;
            if (placement.selfDamage() > budget.remaining
                    || overlapsPending(placement.pos(), System.nanoTime(), false)) continue;
            if (usesVanillaPackets()) return commitPlacement(placement, true, budget) ? 1 : 0;
            if (commitPlacement(placement, true, budget)) placed++;
        }
        return placed;
    }

    /**
     * Focus mode: rank the bases once, put every crystal of this cycle on the
     * best one, and move to the next best only if that base will not take a
     * crystal at all.
     */
    private int placeFocused(List<TargetData> targets, DamageProfile selfProfile, DamageBudget budget,
                             int limit) {
        List<Placement> ranked = findPlacements(targets, selfProfile, budget);
        int placed = 0;
        for (Placement placement : ranked) {
            boolean first = true;
            while (placed < limit && commitPlacement(placement, first, budget)) {
                placed++;
                first = false;
            }
            // Once a base has taken a crystal it stays the only one used this
            // cycle; a base that took none falls through to the next best.
            if (placed >= limit || !first) break;
        }
        return placed;
    }

    private boolean hasCrystalToPlace() {
        var mc = BytePhase.mc;
        return useOffhand.getValue() && mc.player.getOffHandStack().isOf(Items.END_CRYSTAL) || findCrystalSlot() >= 0;
    }

    private void reject(int reason, BlockPos pos) {
        reject[reason]++;
        if (!debug.getValue() || debugAnchor == null) return;
        offerDebug(pos, WHY[reason]);
    }

    private void offerDebug(BlockPos pos, String why) {
        double distance = Vec3d.ofCenter(pos).squaredDistanceTo(debugAnchor);
        int worst = -1;
        for (int i = 0; i < debugPos.length; i++) {
            if (debugPos[i] == null) { worst = i; break; }
            if (worst < 0 || debugDist[i] > debugDist[worst]) worst = i;
        }
        if (debugPos[worst] == null || distance < debugDist[worst]) {
            debugPos[worst] = pos;
            debugWhy[worst] = why;
            debugDist[worst] = distance;
        }
    }

    /** Scans once and returns the best bases, best first. */
    private List<Placement> findPlacements(List<TargetData> targets, DamageProfile selfProfile, DamageBudget budget) {
        var mc = BytePhase.mc;
        if (!hasCrystalToPlace()) return List.of();
        double visibleRange = effectivePlaceRange();
        double placeRangeValue = isConservative() ? visibleRange : Math.max(visibleRange, placeWallsRange.getValue());
        BlockPos scanOrigin = mc.player.getBlockPos();
        int horizontal = (int) Math.ceil(placeRangeValue);
        double selfLimit = budget.remaining;
        Vec3d eye = mc.player.getEyePos();
        int originX = scanOrigin.getX();
        int originY = scanOrigin.getY();
        int originZ = scanOrigin.getZ();
        Box entitySearchBox = mc.player.getBoundingBox().expand(placeRangeValue + 3.0);
        var nearbyEntities = mc.world.getOtherEntities(mc.player, entitySearchBox);
        List<Placement> ranked = new ArrayList<>(MAX_PLACEMENTS + 1);
        double threshold = Double.NEGATIVE_INFINITY;
        boolean targetPriority = placePriority.getValue().equals("TargetDamage");
        long scanNow = System.nanoTime();
        double placeRangeSq = placeRangeValue * placeRangeValue;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        if (debug.getValue()) {
            debugAnchor = targets.isEmpty() ? null : targets.getFirst().center();
            java.util.Arrays.fill(debugPos, null);
        }
        for (int dx = -horizontal; dx <= horizontal; dx++) {
            for (int dz = -horizontal; dz <= horizontal; dz++) {
                for (int dy = -horizontal; dy <= horizontal + (usesVanillaPackets() ? 2 : 0); dy++) {
                    double centerX = originX + dx + 0.5;
                    double centerY = originY + dy + 1.0;
                    double centerZ = originZ + dz + 0.5;
                    double eyeDx = centerX - eye.x;
                    double eyeDy = centerY - eye.y;
                    double eyeDz = centerZ - eye.z;
                    if (!usesVanillaPackets() && eyeDx * eyeDx + eyeDy * eyeDy + eyeDz * eyeDz > placeRangeSq) {
                        continue;
                    }
                    if (usesVanillaPackets()) {
                        double closestX = MathHelper.clamp(eye.x, centerX - 0.5, centerX + 0.5) - eye.x;
                        double closestY = MathHelper.clamp(eye.y, centerY - 1.0, centerY) - eye.y;
                        double closestZ = MathHelper.clamp(eye.z, centerZ - 0.5, centerZ + 0.5) - eye.z;
                        if (closestX * closestX + closestY * closestY + closestZ * closestZ > placeRangeSq) continue;
                    }
                    cursor.set(originX + dx, originY + dy, originZ + dz);
                    var base = mc.world.getBlockState(cursor);
                    if (!(base.isOf(Blocks.OBSIDIAN) || base.isOf(Blocks.BEDROCK))) {
                        continue;
                    }
                    BlockPos pos = cursor.toImmutable();
                    reject[6]++;
                    if (!mc.world.isAir(pos.up())) {
                        reject(0, pos);
                        continue;
                    }
                    Vec3d center = new Vec3d(centerX, centerY, centerZ);
                    Long cooling = coolingBases.get(pos);
                    if (cooling != null && scanNow < cooling) {
                        reject(8, pos);
                        continue;
                    }
                    // The base we are hammering is exempt from its own pending
                    // reservation; that reservation is what made the best base
                    // unusable for a whole round trip after every placement.
                    boolean isFocus = focus.getValue() && !isConservative() && pos.equals(focusBase);
                    Long previousPlace = pendingPlaces.get(pos);
                    if (!isFocus && previousPlace != null && scanNow - previousPlace < pendingTimeout) {
                        reject(1, pos);
                        continue;
                    }
                    if (usesVanillaPackets() ? placementHit(pos) == null
                            : !withinInteractionRange(center, pos, visibleRange, placeWallsRange.getValue())) {
                        reject(2, pos);
                        continue;
                    }
                    Box crystalBox = new Box(pos.getX(), pos.getY() + 1.0, pos.getZ(),
                            pos.getX() + 1.0, pos.getY() + 3.0, pos.getZ() + 1.0);
                    boolean blocked = mc.player.getBoundingBox().intersects(crystalBox)
                            || overlapsPending(pos, scanNow, isFocus);
                    // A crystal we attacked a moment ago is already gone on the
                    // server; the client just has not been told yet.
                    boolean staleOverlap = false;
                    if (!blocked) {
                        for (var entity : nearbyEntities) {
                            if (!entity.getBoundingBox().intersects(crystalBox)) continue;
                            if (entity instanceof EndCrystalEntity crystal) {
                                PendingAttack attack = pendingAttacks.get(crystal.getId());
                                if (!isConservative() && attack != null && scanNow - attack.time() < RETRY_NANOS) {
                                    staleOverlap = true;
                                    continue;
                                }
                            }
                            blocked = true;
                            break;
                        }
                    }
                    if (blocked) {
                        reject(3, pos);
                        continue;
                    }
                    TargetData candidateTarget = null;
                    double targetDamage = Double.NEGATIVE_INFINITY;
                    for (TargetData target : targets) {
                        double upperBound = maximumDamageAt(center, target.center(), target.damageProfile());
                        // With Debug on, skip the cheap prune so a rejected base still reports its real damage.
                        if (upperBound < target.minDamage() || !debug.getValue() && upperBound <= threshold) continue;
                        double damage = damageAt(center, target.center(), target.damageProfile(), target.bounds());
                        if (damage >= target.minDamage() && damage > targetDamage) {
                            candidateTarget = target;
                            targetDamage = damage;
                        }
                    }
                    if (candidateTarget == null) {
                        reject(4, pos);
                        continue;
                    }
                    double selfDamage = selfDamageAt(center, selfProfile);
                    if (selfDamage > selfLimit) {
                        reject(5, pos);
                        continue;
                    }
                    double score = targetPriority ? targetDamage : targetDamage - selfDamage;
                    if (debug.getValue() && debugAnchor != null) {
                        offerDebug(pos, "OK score " + Math.round(score * 10.0) / 10.0);
                    }
                    Placement placement = new Placement(pos, score, selfDamage, staleOverlap);
                    if (ranked.size() < MAX_PLACEMENTS) {
                        ranked.add(placement);
                    } else if (score > threshold) {
                        int worst = 0;
                        for (int i = 1; i < ranked.size(); i++) {
                            if (ranked.get(i).score() < ranked.get(worst).score()) worst = i;
                        }
                        ranked.set(worst, placement);
                    }
                    if (ranked.size() >= MAX_PLACEMENTS) {
                        threshold = Double.POSITIVE_INFINITY;
                        for (Placement entry : ranked) threshold = Math.min(threshold, entry.score());
                    }
                }
            }
        }
        ranked.sort(Comparator.comparingDouble(Placement::score).reversed());
        return ranked;
    }

    /** Sends the place packet for one ranked base. */
    private boolean commitPlacement(Placement placement, boolean reserveDamage, DamageBudget budget) {
        var mc = BytePhase.mc;
        Hand hand = useOffhand.getValue() && mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)
                ? Hand.OFF_HAND : Hand.MAIN_HAND;
        int slot = hand == Hand.OFF_HAND ? -1 : findCrystalSlot();
        int originalSlot = mc.player.getInventory().getSelectedSlot();
        if (hand == Hand.MAIN_HAND && slot < 0) return false;
        BlockPos best = placement.pos();
        BlockHitResult hit = usesVanillaPackets() ? placementHit(best)
                : new BlockHitResult(new Vec3d(best.getX() + 0.5, best.getY() + 1.0, best.getZ() + 0.5),
                        Direction.UP, best, false);
        if (hit == null) return false;
        Vec3d aimPoint = usesVanillaPackets() ? hit.getPos().lerp(Vec3d.ofCenter(best), 0.001) : hit.getPos();
        if ((rotate.getValue() || isConservative()) && !face(aimPoint)) return false;
        if (usesVanillaPackets()) {
            hit = ServerFacing.blockHit(best, effectivePlaceRange());
            if (hit == null) return false;
        }
        if (hand == Hand.MAIN_HAND && switchToCrystals.getValue() && slot >= 0) {
            mc.player.getInventory().setSelectedSlot(slot);
        } else if (hand == Hand.MAIN_HAND && !mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) {
            return false;
        }
        ActionResult result;
        try {
            result = mc.interactionManager.interactBlock(mc.player, hand, hit);
            if ((swing.getValue() || usesVanillaPackets()) && (result.isAccepted() || placement.stale())) mc.player.swingHand(hand);
        } finally {
            if (hand == Hand.MAIN_HAND && swapBack.getValue() && slot >= 0 && !usesVanillaPackets()) {
                mc.player.getInventory().setSelectedSlot(originalSlot);
            }
        }
        // The client predicts a placement next to a not-yet-removed crystal as a
        // failure, but the packet was still sent and the server will accept it.
        if (result.isAccepted() || placement.stale()) {
            long now = System.nanoTime();
            reject[7]++;
            if (reserveDamage) budget.reserve(placement.selfDamage());
            pendingPlaces.put(best, now);
            focusBase = best;
            focusStart = now;
            focusConfirmed = false;
            placeTimes.addLast(now);
            rate(placeTimes, now);
            return true;
        }
        return false;
    }

    private void printDebug(long now) {
        if (!debug.getValue()) {
            java.util.Arrays.fill(reject, 0);
            return;
        }
        if (now - debugNanos < SECOND_NANOS) return;
        debugNanos = now;
        var hud = BytePhase.mc.inGameHud;
        if (hud != null) {
            hud.getChatHud().addMessage(net.minecraft.text.Text.literal("[BytePhase] AutoCrystal/s: bases "
                    + reject[6] + " | blockedAbove " + reject[0] + " pending " + reject[1]
                    + " reach " + reject[2] + " entity " + reject[3] + " lowDmg " + reject[4]
                    + " selfDmg " + reject[5] + " | placed " + reject[7]));
            StringBuilder nearest = new StringBuilder();
            for (int i = 0; i < debugPos.length; i++) {
                if (debugPos[i] == null) continue;
                if (nearest.length() > 0) nearest.append("; ");
                nearest.append(debugPos[i].toShortString()).append(" ").append(debugWhy[i]);
            }
            if (nearest.length() > 0) {
                hud.getChatHud().addMessage(net.minecraft.text.Text.literal(
                        "[BytePhase] bases nearest the target: " + nearest));
            }
        }
        java.util.Arrays.fill(reject, 0);
    }

    private int findCrystalSlot() {
        var inventory = BytePhase.mc.player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getStack(slot).isOf(Items.END_CRYSTAL)) return slot;
        }
        return -1;
    }

    private boolean overlapsPending(BlockPos pos, long now, boolean allowOwn) {
        if (pendingPlaces.isEmpty()) return false;
        Box candidate = new Box(pos.getX(), pos.getY() + 1.0, pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 3.0, pos.getZ() + 1.0);
        for (var pending : pendingPlaces.entrySet()) {
            BlockPos p = pending.getKey();
            if (now - pending.getValue() >= pendingTimeout || allowOwn && p.equals(pos)) continue;
            Box reserved = new Box(p.getX() - 0.5, p.getY() + 1.0, p.getZ() - 0.5,
                    p.getX() + 1.5, p.getY() + 3.0, p.getZ() + 1.5);
            if (reserved.intersects(candidate)) return true;
        }
        return false;
    }

    private double effectivePlaceRange() {
        return isConservative() ? Math.min(placeRange.getValue(),
                BytePhase.mc.player.getAttributeValue(EntityAttributes.BLOCK_INTERACTION_RANGE)) : placeRange.getValue();
    }

    private double effectiveBreakRange() {
        return isConservative() ? Math.min(breakRange.getValue(),
                BytePhase.mc.player.getAttributeValue(EntityAttributes.ENTITY_INTERACTION_RANGE)) : breakRange.getValue();
    }

    /** Uses Meteor-style separate reach for visible targets and targets behind blocks. */
    private boolean withinInteractionRange(Vec3d point, BlockPos targetBlock,
                                           double visibleRange, double wallsRange) {
        var mc = BytePhase.mc;
        var hit = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), point,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        boolean visible = hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS
                || hit.getBlockPos().equals(targetBlock);
        if (!visible && (isConservative() || !throughWalls.getValue())) return false;
        double allowed = visible ? visibleRange : wallsRange;
        return point.squaredDistanceTo(mc.player.getEyePos()) <= allowed * allowed;
    }

    /**
     * Turns the server-side aim toward {@code target} by one smoothed step.
     *
     * @return true once the aim is close enough to interact from.
     */
    private boolean face(Vec3d target) {
        var player = BytePhase.mc.player;
        Vec3d eye = player.getEyePos();
        float targetYaw = RotationUtil.yawTo(eye, target);
        float targetPitch = RotationUtil.pitchTo(eye, target);
        if (!aim.isInitialized()) {
            aim.reset(usesVanillaPackets() ? ServerFacing.continuousYaw(player.getYaw()) : player.getYaw(), player.getPitch());
        }
        aim.tick(targetYaw, targetPitch, rotationSpeed.asFloat(),
                smoothing.asFloat() / 100.0f, false);
        if (usesVanillaPackets()) {
            SilentRotation.clear();
            player.setYaw(aim.yaw());
            player.setPitch(aim.pitch());
        } else {
            SilentRotation.set(aim.yaw(), aim.pitch());
            SilentRotation.flush();
        }
        return RotationUtil.angleBetween(aim.yaw(), aim.pitch(), targetYaw, targetPitch)
                <= AIM_TOLERANCE;
    }

    private BlockHitResult placementHit(BlockPos pos) {
        var mc = BytePhase.mc;
        var hit = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), Vec3d.ofCenter(pos),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        double reach = effectivePlaceRange();
        return hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)
                && hit.getPos().squaredDistanceTo(mc.player.getEyePos()) <= reach * reach ? hit : null;
    }

    /** Estimates vanilla crystal damage with block exposure and armor reduction. */
    private static double damageAt(Vec3d explosion, Vec3d targetCenter,
                                   DamageProfile profile, Box targetBounds) {
        double distance = Math.sqrt(targetCenter.squaredDistanceTo(explosion));
        if (distance >= 12.0) {
            return 0.0;
        }
        // ExplosionImpl.calculateReceivedDamage returns block exposure
        // directly (missed rays / all rays). When prediction is enabled, use
        // the predicted box for those rays too; mixing a predicted distance
        // with current-position exposure mis-scores targets near cover.
        LivingEntity entity = profile.entity();
        Box currentBounds = entity.getBoundingBox();
        double received = sameBounds(targetBounds, currentBounds)
                ? ExplosionImpl.calculateReceivedDamage(explosion, entity)
                : calculateReceivedDamage(explosion, entity, targetBounds);
        double impact = (1.0 - distance / 12.0) * received;
        return damageForImpact(impact, profile);
    }

    /** Damage upper bound at full exposure, used to skip needless raycasts. */
    private static double maximumDamageAt(Vec3d explosion, Vec3d targetCenter, DamageProfile profile) {
        double distance = Math.sqrt(targetCenter.squaredDistanceTo(explosion));
        if (distance >= 12.0) return 0.0;
        return damageForImpact(1.0 - distance / 12.0, profile);
    }

    private static double damageForImpact(double impact, DamageProfile profile) {
        if (impact <= 0.0) {
            return 0.0;
        }

        double damage = (impact * impact + impact) * 42.0 + 1.0;
        // Vanilla scales explosion damage for players with world difficulty
        // before armor. Applying it here also keeps self-damage conservative.
        if (profile.entity() instanceof PlayerEntity) {
            damage = switch (BytePhase.mc.world.getDifficulty()) {
                case PEACEFUL -> 0.0;
                case EASY -> Math.min(damage / 2.0 + 1.0, damage);
                case HARD -> damage * 1.5;
                default -> damage;
            };
        }
        damage = DamageUtil.getDamageLeft(profile.entity(), (float) damage, profile.explosionSource(),
                profile.armor(), profile.toughness());
        damage *= 1.0f - Math.min(20, profile.protection()) / 25.0f;
        return damage * profile.resistanceMultiplier();
    }

    /** Snapshot armor and defensive effects once per target for this tick's many candidate scores. */
    private static DamageProfile damageProfile(LivingEntity living) {
        var registry = BytePhase.mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        if (registry != cachedEnchantmentRegistry) {
            cachedEnchantmentRegistry = registry;
            protectionEnchantment = registry.getEntry(Enchantments.PROTECTION.getValue()).orElse(null);
            blastProtectionEnchantment = registry.getEntry(Enchantments.BLAST_PROTECTION.getValue()).orElse(null);
        }
        int protection = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = living.getEquippedStack(slot);
            if (protectionEnchantment != null) {
                protection += EnchantmentHelper.getLevel(protectionEnchantment, stack);
            }
            if (blastProtectionEnchantment != null) {
                protection += 2 * EnchantmentHelper.getLevel(blastProtectionEnchantment, stack);
            }
        }
        StatusEffectInstance resistance = living.getStatusEffect(StatusEffects.RESISTANCE);
        double resistanceMultiplier = resistance == null ? 1.0
                : Math.max(0.0f, 1.0f - 0.2f * (resistance.getAmplifier() + 1));
        return new DamageProfile(living,
                (float) living.getAttributeValue(EntityAttributes.ARMOR),
                (float) living.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS), protection,
                resistanceMultiplier, BytePhase.mc.world.getDamageSources().explosion(null, null));
    }

    private static boolean sameBounds(Box first, Box second) {
        return first.minX == second.minX && first.minY == second.minY && first.minZ == second.minZ
                && first.maxX == second.maxX && first.maxY == second.maxY && first.maxZ == second.maxZ;
    }

    /** Mirrors 1.21.11 ExplosionImpl's exposure sampling for a predicted box. */
    private static double calculateReceivedDamage(Vec3d explosion, Entity entity, Box bounds) {
        double stepX = 1.0 / (bounds.getLengthX() * 2.0 + 1.0);
        double stepY = 1.0 / (bounds.getLengthY() * 2.0 + 1.0);
        double stepZ = 1.0 / (bounds.getLengthZ() * 2.0 + 1.0);
        if (stepX < 0.0 || stepY < 0.0 || stepZ < 0.0) {
            return 0.0;
        }
        double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
        double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
        int visible = 0;
        int total = 0;
        for (double x = 0.0; x <= 1.0; x += stepX) {
            for (double y = 0.0; y <= 1.0; y += stepY) {
                for (double z = 0.0; z <= 1.0; z += stepZ) {
                    Vec3d sample = new Vec3d(
                            MathHelper.lerp(x, bounds.minX, bounds.maxX) + offsetX,
                            MathHelper.lerp(y, bounds.minY, bounds.maxY),
                            MathHelper.lerp(z, bounds.minZ, bounds.maxZ) + offsetZ);
                    if (entity.getEntityWorld().raycast(new RaycastContext(sample, explosion,
                            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity)
                            ).getType() == net.minecraft.util.hit.HitResult.Type.MISS) {
                        visible++;
                    }
                    total++;
                }
            }
        }
        return total == 0 ? 0.0 : (double) visible / total;
    }

    private static double selfDamageAt(Vec3d explosion, DamageProfile profile) {
        var player = profile.entity();
        // Creative and invulnerable players cannot take ordinary crystal damage.
        if (player.isInvulnerable() || player instanceof PlayerEntity playerEntity && playerEntity.isCreative()) {
            return 0.0;
        }
        return damageAt(explosion, player.getEntityPos(), profile, player.getBoundingBox());
    }

    /** Honor the configured cap while keeping a one-health-point lethal margin. */
    private double safeSelfDamageLimit() {
        var player = BytePhase.mc.player;
        double survivable = Math.max(0.0, player.getHealth() + player.getAbsorptionAmount() - 1.0);
        return Math.min(maxSelfDamage.getValue(), survivable);
    }

    private record DamageProfile(LivingEntity entity, float armor, float toughness, int protection,
                                 double resistanceMultiplier, DamageSource explosionSource) { }

    private record TargetData(LivingEntity entity, Vec3d center, Box bounds,
                              double minDamage, double sortScore, DamageProfile damageProfile) { }
}
