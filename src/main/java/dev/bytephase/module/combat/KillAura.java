package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.util.AimSmoother;
import dev.bytephase.util.AttackLimiter;
import dev.bytephase.util.RotationUtil;
import dev.bytephase.util.SilentRotation;
import dev.bytephase.util.ServerFacing;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Automatically attacks nearby entities.
 *
 * <p>Targets are ranked by the selected criterion, and the aim turns toward the
 * chosen target with an eased, accelerating turn. The rotation is sent to the
 * server without moving the camera: your view stays where you pointed it while
 * your avatar turns and the hits land.</p>
 */
public final class KillAura extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", 4.2, 3.0, 6.0, 0.1));
    private final NumberSetting cooldown = add(new NumberSetting("Cooldown", 90.0, 50.0, 100.0, 1.0));
    private final ModeSetting sort = add(new ModeSetting("Sort", new String[]{"Distance", "Health", "Angle"}, "Distance"));
    private final BooleanSetting players = add(new BooleanSetting("Players", true));
    private final BooleanSetting hostiles = add(new BooleanSetting("Hostiles", false));
    private final BooleanSetting throughWalls = add(new BooleanSetting("ThroughWalls", false));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    private final NumberSetting rotationSpeed = add(new NumberSetting("RotationSpeed", 45.0, 5.0, 360.0, 5.0));
    private final NumberSetting smoothing = add(new NumberSetting("Smoothing", 50.0, 0.0, 100.0, 5.0));
    private final BooleanSetting humanize = add(new BooleanSetting("Humanize", true));
    private final BooleanSetting swing = add(new BooleanSetting("Swing", true));
    private final BooleanSetting conservative = add(new BooleanSetting("Conservative", false));
    private final BooleanSetting requireAttack = add(new BooleanSetting("RequireAttack", false));
    private final BooleanSetting vanillaPackets = add(new BooleanSetting("VanillaPackets", false));

    private final AimSmoother aim = new AimSmoother();
    private int correctionTicks;

    public KillAura() {
        super("KillAura", Category.COMBAT, "Automatically attacks nearby entities");
    }

    @Override
    protected void onDisable() {
        stopAiming();
        correctionTicks = 0;
    }

    @Override
    public void onServerCorrection() {
        stopAiming();
        if (isConservative()) correctionTicks = 10;
    }

    public boolean usesVanillaPackets() { return vanillaPackets.getValue(); }

    private boolean isConservative() { return conservative.getValue() || usesVanillaPackets(); }

    public void onPreTick() {
        if (usesVanillaPackets()) runTick();
    }

    @Override
    public void onTick() {
        if (!usesVanillaPackets()) runTick();
    }

    private void runTick() {
        if (correctionTicks > 0) {
            correctionTicks--;
            stopAiming();
            return;
        }
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.player.isDead() || mc.player.isSpectator() || mc.currentScreen != null
                || requireAttack.getValue() && !mc.options.attackKey.isPressed()
                || usesVanillaPackets() && ServerFacing.hasConflicts()) {
            stopAiming();
            return;
        }
        Entity target = findTarget();
        if (target == null) {
            stopAiming();
            return;
        }
        boolean shouldRotate = rotate.getValue() || isConservative();
        if (!shouldRotate) {
            stopAiming();
        } else {
            face(target);
        }
        double requiredCooldown = isConservative() ? 100.0 : cooldown.getValue();
        if (mc.player.getAttackCooldownProgress(0.0f) * 100.0f < requiredCooldown) {
            return;
        }
        // Do not send an attack before a limited-speed rotation has actually
        // reached the target; the server may validate the current facing.
        if (!shouldRotate || isAimedAt(target)) {
            if (!usesVanillaPackets()) SilentRotation.flush();
            AttackLimiter.tryAttack(mc.player, target, swing.getValue() || usesVanillaPackets());
        }
    }

    private void stopAiming() {
        aim.reset();
        SilentRotation.clear();
    }

    private Entity findTarget() {
        var mc = BytePhase.mc;
        double reach = effectiveRange();
        double rangeSq = reach * reach;
        String selectedSort = sort.getValue();
        Entity best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Entity entity : mc.world.getOtherEntities(mc.player,
                mc.player.getBoundingBox().expand(reach))) {
            if (entity == mc.player || !entity.isAlive() || entity.isSpectator()
                    || entity.squaredDistanceTo(mc.player) > rangeSq) {
                continue;
            }
            boolean valid = players.getValue() && entity instanceof PlayerEntity
                    || hostiles.getValue() && entity.getType().getSpawnGroup() == SpawnGroup.MONSTER;
            if (!valid) {
                continue;
            }
            if ((isConservative() || !throughWalls.getValue()) && !mc.player.canSee(entity)) {
                continue;
            }
            double score = switch (selectedSort) {
                case "Health" -> health(entity);
                case "Angle" -> angleTo(entity);
                default -> entity.squaredDistanceTo(mc.player);
            };
            if (score < bestScore) {
                best = entity;
                bestScore = score;
            }
        }
        return best;
    }

    private static float health(Entity entity) {
        return entity instanceof LivingEntity living ? living.getHealth() : Float.MAX_VALUE;
    }

    private double angleTo(Entity entity) {
        var mc = BytePhase.mc;
        Vec3d eye = mc.player.getEyePos();
        Vec3d target = entity.getBoundingBox().getCenter();
        double dx = target.x - eye.x;
        double dz = target.z - eye.z;
        float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0f;
        float current = SilentRotation.isActive() ? SilentRotation.yaw() : mc.player.getYaw();
        return Math.abs(MathHelper.wrapDegrees(yaw - current));
    }

    private boolean isAimedAt(Entity target) {
        if (usesVanillaPackets()) return ServerFacing.hitsEntity(target.getBoundingBox(), effectiveRange());
        Vec3d eye = BytePhase.mc.player.getEyePos();
        Vec3d direction = Vec3d.fromPolar(aim.pitch(), aim.yaw());
        var bounds = isConservative() ? target.getBoundingBox() : target.getBoundingBox().expand(0.1);
        return bounds.raycast(eye, eye.add(direction.multiply(
                effectiveRange() + (isConservative() ? 0.0 : 1.0)))).isPresent();
    }

    private double effectiveRange() {
        // Read the server-provided attribute directly, not Reach's overridden accessor.
        return isConservative() ? Math.min(range.getValue(),
                BytePhase.mc.player.getAttributeValue(EntityAttributes.ENTITY_INTERACTION_RANGE)) : range.getValue();
    }

    private void face(Entity target) {
        var mc = BytePhase.mc;
        Vec3d eye = mc.player.getEyePos();
        Vec3d to = target.getBoundingBox().getCenter();
        float targetYaw = RotationUtil.yawTo(eye, to);
        float targetPitch = RotationUtil.pitchTo(eye, to);

        if (!aim.isInitialized()) {
            aim.reset(usesVanillaPackets() ? ServerFacing.continuousYaw(mc.player.getYaw()) : mc.player.getYaw(),
                    mc.player.getPitch());
        }
        aim.tick(targetYaw, targetPitch, rotationSpeed.asFloat(),
                smoothing.asFloat() / 100.0f, humanize.getValue());
        if (usesVanillaPackets()) {
            SilentRotation.clear();
            mc.player.setYaw(aim.yaw());
            mc.player.setPitch(aim.pitch());
        } else {
            SilentRotation.set(aim.yaw(), aim.pitch());
        }
    }
}
