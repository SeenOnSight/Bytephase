package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.AttackLimiter;
import dev.bytephase.util.CameraAim;
import dev.bytephase.util.MaceUtil;
import dev.bytephase.util.RotationUtil;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Automatically smash-attacks a nearby target with a Wind Burst mace while you
 * are falling. A smash with Wind Burst launches you back into the air, which
 * is what lets ElytraMace take over and bring you back down on the target.
 */
public final class AutoWindBurst extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", 3.0, 1.0, 6.0, 0.1));
    private final NumberSetting minFall = add(new NumberSetting("MinFall", 3.0, 1.6, 30.0, 0.5));
    private final NumberSetting delay = add(new NumberSetting("Delay", 4.0, 0.0, 20.0, 1.0));
    // Off by default: a smash and its Wind Burst launch do not need a recharged mace,
    // and waiting blocks spear-then-mace combos (a jab resets the attack timer).
    private final BooleanSetting waitForCharge = add(new BooleanSetting("WaitForCharge", false));
    private final BooleanSetting targetPlayers = add(new BooleanSetting("TargetPlayers", true));
    private final BooleanSetting targetHostiles = add(new BooleanSetting("TargetHostiles", false));
    private final BooleanSetting swapBack = add(new BooleanSetting("SwapBack", true));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", false));
    private final NumberSetting rotationSpeed = add(new NumberSetting("RotationSpeed", 60.0, 5.0, 360.0, 5.0));
    private final NumberSetting smoothing = add(new NumberSetting("Smoothing", 20.0, 0.0, 100.0, 5.0));

    private int cooldown;
    private int restoreSlot = -1;
    private boolean aiming;

    public AutoWindBurst() {
        super("AutoWindBurst", Category.COMBAT, "Smash-attacks with a Wind Burst mace while falling");
    }

    @Override
    protected void onDisable() {
        releaseAim();
        restoreSlot = -1;
        cooldown = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || mc.player.isDead() || mc.player.isSpectator()) {
            releaseAim();
            return;
        }
        PlayerEntity player = mc.player;
        if (restoreSlot >= 0) {
            player.getInventory().setSelectedSlot(restoreSlot);
            restoreSlot = -1;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        int slot = MaceUtil.findHotbarMace(player, Enchantments.WIND_BURST);
        if (slot < 0 || !MaceUtil.canSmash(player) || player.fallDistance < minFall.getValue()) {
            releaseAim();
            return;
        }
        LivingEntity target = findTarget();
        if (target == null) {
            releaseAim();
            return;
        }
        Vec3d eye = player.getEyePos();
        if (rotate.getValue()) {
            Vec3d point = RotationUtil.aimPoint(target, player.getY());
            float yaw = RotationUtil.yawTo(eye, point);
            float pitch = RotationUtil.pitchTo(eye, point);
            CameraAim.aim(yaw, pitch, rotationSpeed.asFloat(), smoothing.asFloat() / 100.0f, false);
            aiming = true;
            if (RotationUtil.angleBetween(player.getYaw(), player.getPitch(), yaw, pitch) > 10.0f) {
                return;
            }
        }
        if (distanceTo(eye, target.getBoundingBox()) > range.getValue() || cooldown > 0) {
            return;
        }
        // Optional: full damage needs a charged weapon, but the launch does not.
        if (waitForCharge.getValue() && player.getAttackCooldownProgress(0.5f) < 0.9f) {
            return;
        }
        int selected = player.getInventory().getSelectedSlot();
        if (slot != selected) {
            if (swapBack.getValue()) {
                restoreSlot = selected;
            }
            player.getInventory().setSelectedSlot(slot);
        }
        if (AttackLimiter.tryAttack(player, target, true)) {
            cooldown = delay.asInt();
        } else if (restoreSlot >= 0) {
            player.getInventory().setSelectedSlot(restoreSlot);
            restoreSlot = -1;
        }
    }

    private LivingEntity findTarget() {
        var mc = BytePhase.mc;
        double reach = range.getValue() + 1.5;
        // Whoever you hit last (and ElytraMace is chasing) comes first.
        LivingEntity preferred = ElytraMace.preferredTarget();
        if (preferred != null && preferred.isAlive() && preferred != mc.player
                && distanceTo(mc.player.getEyePos(), preferred.getBoundingBox()) <= reach) {
            return preferred;
        }
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        Vec3d eye = mc.player.getEyePos();
        for (Entity entity : mc.world.getOtherEntities(mc.player, mc.player.getBoundingBox().expand(reach))) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isSpectator()) {
                continue;
            }
            boolean player = living instanceof PlayerEntity;
            boolean hostile = !player && living.getType().getSpawnGroup() == SpawnGroup.MONSTER;
            if (!(player && targetPlayers.getValue() || hostile && targetHostiles.getValue())) {
                continue;
            }
            double distance = distanceTo(eye, living.getBoundingBox());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        return best;
    }

    /** Distance from {@code eye} to the nearest point of {@code box}. */
    private static double distanceTo(Vec3d eye, Box box) {
        double x = MathHelper.clamp(eye.x, box.minX, box.maxX);
        double y = MathHelper.clamp(eye.y, box.minY, box.maxY);
        double z = MathHelper.clamp(eye.z, box.minZ, box.maxZ);
        return eye.distanceTo(new Vec3d(x, y, z));
    }

    private void releaseAim() {
        if (aiming) {
            CameraAim.release();
            aiming = false;
        }
    }
}
