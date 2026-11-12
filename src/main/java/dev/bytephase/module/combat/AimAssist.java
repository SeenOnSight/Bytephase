package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.CameraAim;
import dev.bytephase.util.RotationUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Gently steers your aim at the closest target in front of you.
 *
 * <p>The target angle is picked once per tick, but the turn itself happens every
 * rendered frame with an exponential ease, so the camera glides instead of
 * stepping twenty times a second.</p>
 */
public final class AimAssist extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", 3.0, 1.0, 40.0, 0.5));
    private final NumberSetting speed = add(new NumberSetting("Speed", 25.0, 1.0, 120.0, 1.0));
    private final NumberSetting smoothing = add(new NumberSetting("Smoothing", 65.0, 0.0, 100.0, 5.0));
    private final BooleanSetting humanize = add(new BooleanSetting("Humanize", true));
    private final NumberSetting fov = add(new NumberSetting("Fov", 90.0, 30.0, 180.0, 5.0));
    private final BooleanSetting playersOnly = add(new BooleanSetting("PlayersOnly", true));

    public AimAssist() {
        super("AimAssist", Category.COMBAT, "Gently steers your aim at nearby targets");
    }

    @Override
    protected void onDisable() {
        CameraAim.release();
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.currentScreen != null
                || !mc.isWindowFocused() || !mc.options.attackKey.isPressed()) {
            CameraAim.release();
            return;
        }
        double effectiveRange = Math.min(range.getValue(), mc.player.getEntityInteractionRange());
        double maxRangeSq = effectiveRange * effectiveRange;
        Vec3d look = mc.player.getRotationVector();
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Entity entity : mc.world.getOtherEntities(mc.player,
                mc.player.getBoundingBox().expand(effectiveRange))) {
            if (entity == mc.player || !entity.isAlive() || !(entity instanceof LivingEntity living)) {
                continue;
            }
            if (playersOnly.getValue() && !(entity instanceof PlayerEntity)) {
                continue;
            }
            double distSq = entity.squaredDistanceTo(mc.player);
            if (distSq > maxRangeSq || distSq >= bestDistSq) {
                continue;
            }
            if (!mc.player.canSee(living)) {
                continue;
            }
            Vec3d direction = living.getEyePos().subtract(mc.player.getEyePos()).normalize();
            double angle = Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(direction), -1.0, 1.0)));
            if (angle > fov.getValue() * 0.5) {
                continue;
            }
            bestDistSq = distSq;
            best = living;
        }
        if (best == null) {
            CameraAim.release();
            return;
        }
        Vec3d from = mc.player.getEyePos();
        Vec3d to = RotationUtil.aimPoint(best, mc.player.getY());
        CameraAim.aim(RotationUtil.yawTo(from, to), RotationUtil.pitchTo(from, to),
                speed.asFloat(), smoothing.asFloat() / 100.0f, humanize.getValue());
    }
}
