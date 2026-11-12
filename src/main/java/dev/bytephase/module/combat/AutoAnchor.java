package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.BlockPlacementUtil;
import dev.bytephase.util.BlockReachUtil;
import dev.bytephase.util.ExplosionDamage;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Places a respawn anchor next to a target, charges it with a single
 * glowstone and detonates it.
 *
 * <p>Outside the Nether a charged respawn anchor explodes (power 5) when used,
 * so this only runs in the Overworld and the End. Each run consumes one anchor
 * and one glowstone. Every placement is scored with the explosion damage the
 * target and you would take, and nothing is placed if you would take more than
 * {@code MaxSelfDamage} or drop below {@code MinHealth}.</p>
 */
public final class AutoAnchor extends Module {
    private static final float POWER = 5.0f;
    private static final int MAX_EVALUATED = 24;
    private static final int MAX_TARGETS = 3;
    private static final int SEARCH_RADIUS = 3;

    private final NumberSetting range = add(new NumberSetting("Range", 7.0, 3.0, 12.0, 0.5));
    private final NumberSetting placeRange = add(new NumberSetting("PlaceRange", 4.5, 2.0, 6.0, 0.1));
    private final NumberSetting minDamage = add(new NumberSetting("MinDamage", 6.0, 1.0, 20.0, 0.5));
    private final NumberSetting maxSelfDamage = add(new NumberSetting("MaxSelfDamage", 8.0, 0.0, 30.0, 0.5));
    private final NumberSetting minHealth = add(new NumberSetting("MinHealth", 10.0, 1.0, 20.0, 0.5));
    private final NumberSetting delay = add(new NumberSetting("Delay", 10.0, 0.0, 40.0, 1.0));
    // 0 places, charges and detonates inside one tick.
    private final NumberSetting stepDelay = add(new NumberSetting("StepDelay", 0.0, 0.0, 5.0, 1.0));
    private final BooleanSetting targetPlayers = add(new BooleanSetting("TargetPlayers", true));
    private final BooleanSetting targetHostiles = add(new BooleanSetting("TargetHostiles", false));
    private final BooleanSetting swapBack = add(new BooleanSetting("SwapBack", true));

    private enum Phase { IDLE, CHARGE, EXPLODE }

    private record Candidate(BlockPos pos, Vec3d center, double distanceSq) { }

    private record Scored(BlockPos pos, double score) { }

    private Phase phase = Phase.IDLE;
    private BlockPos anchor;
    private int cooldown;
    private int stepWait;
    private int originSlot = -1;

    public AutoAnchor() {
        super("AutoAnchor", Category.COMBAT, "Places, charges once and blows up a respawn anchor (Overworld and End)");
    }

    @Override
    protected void onEnable() {
        reset(0);
    }

    @Override
    protected void onDisable() {
        reset(0);
    }

    private void reset(int nextCooldown) {
        phase = Phase.IDLE;
        anchor = null;
        stepWait = 0;
        cooldown = nextCooldown;
        var mc = BytePhase.mc;
        if (originSlot >= 0 && swapBack.getValue() && mc != null && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(originSlot);
        }
        originSlot = -1;
    }

    /** Anchors only explode outside the Nether, so the module is limited to these two. */
    private static boolean supportedDimension(World world) {
        return world.getRegistryKey() == World.OVERWORLD || world.getRegistryKey() == World.END;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || mc.player.isDead() || mc.player.isSpectator()) {
            if (phase != Phase.IDLE) reset(0);
            return;
        }
        if (!supportedDimension(mc.world)) {
            if (phase != Phase.IDLE) reset(0);
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (stepWait > 0) {
            stepWait--;
            return;
        }
        if (mc.player.getHealth() + mc.player.getAbsorptionAmount() <= minHealth.getValue()) {
            if (phase != Phase.IDLE) reset(delay.asInt());
            return;
        }
        // Place, charge and detonate are separate steps; with StepDelay 0 they
        // all run in this one tick.
        for (int guard = 0; guard < 3; guard++) {
            boolean progressed = switch (phase) {
                case IDLE -> startSequence();
                case CHARGE -> charge();
                case EXPLODE -> explode();
            };
            if (!progressed || phase == Phase.IDLE) {
                break;
            }
            if (stepDelay.asInt() > 0) {
                stepWait = stepDelay.asInt();
                break;
            }
        }
    }

    private boolean startSequence() {
        var mc = BytePhase.mc;
        if (cooldown > 0) {
            return false;
        }
        int anchorSlot = findHotbar(Items.RESPAWN_ANCHOR);
        if (anchorSlot < 0 || findHotbar(Items.GLOWSTONE) < 0) {
            return false;
        }
        List<LivingEntity> targets = findTargets();
        if (targets.isEmpty()) {
            return false;
        }
        double selfLimit = Math.min(maxSelfDamage.getValue(),
                Math.max(0.0, mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1.0));
        List<Scored> options = new ArrayList<>();
        for (LivingEntity target : targets) {
            collectPlacements(target, selfLimit, options);
        }
        if (options.isEmpty()) {
            return false;
        }
        options.sort(Comparator.comparingDouble(Scored::score).reversed());
        originSlot = mc.player.getInventory().getSelectedSlot();
        // Try the best few; one may be unreachable or obstructed by an entity.
        for (int i = 0; i < Math.min(3, options.size()); i++) {
            BlockPos pos = options.get(i).pos();
            if (BlockPlacementUtil.place(mc, pos, anchorSlot, false, true)) {
                anchor = pos;
                phase = Phase.CHARGE;
                return true;
            }
        }
        originSlot = -1;
        cooldown = 2;
        return false;
    }

    /** Charges the anchor once with a glowstone. */
    private boolean charge() {
        var mc = BytePhase.mc;
        if (anchor == null || !mc.world.getBlockState(anchor).isOf(Blocks.RESPAWN_ANCHOR)) {
            reset(delay.asInt());
            return false;
        }
        int glowstone = findHotbar(Items.GLOWSTONE);
        if (glowstone < 0 || !safeToBlow()) {
            reset(delay.asInt());
            return false;
        }
        mc.player.getInventory().setSelectedSlot(glowstone);
        if (!interactAnchor()) {
            reset(delay.asInt());
            return false;
        }
        phase = Phase.EXPLODE;
        return true;
    }

    /** Uses the charged anchor with something other than glowstone, which makes it explode. */
    private boolean explode() {
        var mc = BytePhase.mc;
        if (anchor == null || !mc.world.getBlockState(anchor).isOf(Blocks.RESPAWN_ANCHOR) || !safeToBlow()) {
            // Leave the (charged) anchor alone if blowing it is no longer safe.
            reset(delay.asInt());
            return false;
        }
        int slot = findNonGlowstoneSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        interactAnchor();
        reset(delay.asInt());
        return true;
    }

    private boolean interactAnchor() {
        var mc = BytePhase.mc;
        if (!BlockReachUtil.isWithinReach(mc, anchor, mc.player.getBlockInteractionRange())) {
            return false;
        }
        Vec3d eye = mc.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(anchor);
        Direction face = Direction.getFacing(eye.x - center.x, eye.y - center.y, eye.z - center.z);
        Vec3d hitPos = center.add(Vec3d.of(face.getVector()).multiply(0.5));
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new BlockHitResult(hitPos, face, anchor, false));
        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        return result.isAccepted();
    }

    /** Re-checks that detonating this anchor will not hurt you more than allowed. */
    private boolean safeToBlow() {
        var mc = BytePhase.mc;
        double self = ExplosionDamage.estimate(Vec3d.ofCenter(anchor), mc.player, POWER);
        double survivable = Math.max(0.0, mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1.0);
        return self <= maxSelfDamage.getValue() && self < survivable;
    }

    private void collectPlacements(LivingEntity target, double selfLimit, List<Scored> out) {
        var mc = BytePhase.mc;
        Vec3d eye = mc.player.getEyePos();
        Vec3d targetPos = target.getEntityPos();
        double reachSq = (placeRange.getValue() + 0.9) * (placeRange.getValue() + 0.9);
        BlockPos base = target.getBlockPos();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        List<Candidate> candidates = new ArrayList<>();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (int dy = -2; dy <= 3; dy++) {
                    cursor.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    var state = mc.world.getBlockState(cursor);
                    if (!state.isReplaceable() || !state.getFluidState().isEmpty()) continue;
                    Vec3d center = Vec3d.ofCenter(cursor);
                    if (eye.squaredDistanceTo(center) > reachSq) continue;
                    if (!ExplosionDamage.inRange(center, targetPos, POWER)) continue;
                    if (!hasSupport(cursor)) continue;
                    // Nothing solid can be placed inside an entity.
                    if (!mc.world.getOtherEntities(null, new Box(cursor), e -> !e.isSpectator()).isEmpty()) continue;
                    candidates.add(new Candidate(cursor.toImmutable(), center, center.squaredDistanceTo(targetPos)));
                }
            }
        }
        // Closer to the target means more damage, so score those first.
        candidates.sort(Comparator.comparingDouble(Candidate::distanceSq));
        if (candidates.size() > MAX_EVALUATED) {
            candidates = candidates.subList(0, MAX_EVALUATED);
        }
        for (Candidate candidate : candidates) {
            double damage = ExplosionDamage.estimate(candidate.center(), target, POWER);
            if (damage < minDamage.getValue()) continue;
            double self = ExplosionDamage.estimate(candidate.center(), mc.player, POWER);
            if (self > selfLimit) continue;
            out.add(new Scored(candidate.pos(), damage - self));
        }
    }

    private static boolean hasSupport(BlockPos pos) {
        var world = BytePhase.mc.world;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.offset(direction);
            if (!world.getBlockState(neighbor).getCollisionShape(world, neighbor).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private List<LivingEntity> findTargets() {
        var mc = BytePhase.mc;
        double rangeSq = range.getValue() * range.getValue();
        List<LivingEntity> found = new ArrayList<>();
        for (Entity entity : mc.world.getOtherEntities(mc.player, mc.player.getBoundingBox().expand(range.getValue()))) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isSpectator()
                    || living.squaredDistanceTo(mc.player) > rangeSq) {
                continue;
            }
            boolean player = living instanceof PlayerEntity;
            boolean hostile = !player && living.getType().getSpawnGroup() == SpawnGroup.MONSTER;
            if (player && targetPlayers.getValue() || hostile && targetHostiles.getValue()) {
                found.add(living);
            }
        }
        found.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mc.player)));
        return found.size() > MAX_TARGETS ? found.subList(0, MAX_TARGETS) : found;
    }

    private static int findHotbar(net.minecraft.item.Item item) {
        var inventory = BytePhase.mc.player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getStack(slot).isOf(item)) {
                return slot;
            }
        }
        return -1;
    }

    /** A hotbar slot that is not glowstone: glowstone would add a charge instead of detonating. */
    private static int findNonGlowstoneSlot() {
        var inventory = BytePhase.mc.player.getInventory();
        int selected = inventory.getSelectedSlot();
        if (!inventory.getStack(selected).isOf(Items.GLOWSTONE)) {
            return selected;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (!inventory.getStack(slot).isOf(Items.GLOWSTONE)) {
                return slot;
            }
        }
        return selected;
    }
}
