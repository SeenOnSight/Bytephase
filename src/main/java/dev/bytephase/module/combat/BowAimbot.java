package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.CameraAim;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class BowAimbot extends Module {
    public final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    public final BooleanSetting ballistic = add(new BooleanSetting("Ballistic", true));
    private final NumberSetting speed = add(new NumberSetting("Speed", 30.0, 2.0, 180.0, 2.0));
    private final NumberSetting smoothing = add(new NumberSetting("Smoothing", 50.0, 0.0, 100.0, 5.0));

    public BowAimbot() {
        super("BowAimbot", Category.COMBAT, "Aims your bow or crossbow at players");
    }

    @Override
    protected void onDisable() {
        CameraAim.release();
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.currentScreen != null) {
            return;
        }
        if (!mc.player.isUsingItem()) {
            CameraAim.release();
            return;
        }
        ItemStack active = mc.player.getActiveItem();
        if (active.getItem() != Items.BOW && active.getItem() != Items.CROSSBOW) {
            CameraAim.release();
            return;
        }
        Entity target = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : mc.world.getOtherEntities(mc.player,
                mc.player.getBoundingBox().expand(20.0))) {
            if (!(entity instanceof PlayerEntity) || entity == mc.player || !entity.isAlive()) {
                continue;
            }
            double dist = entity.squaredDistanceTo(mc.player);
            if (dist < 400 && dist < bestDist) {
                bestDist = dist;
                target = entity;
            }
        }
        if (target == null) {
            CameraAim.release();
            return;
        }
        Vec3d from = mc.player.getEyePos();
        Vec3d to = target.getEyePos().subtract(0, 0.5, 0);
        // Per-tick horizontal motion measured from positions: other players'
        // synced velocity is unreliable on the client.
        double motionX = target.getX() - target.lastX;
        double motionZ = target.getZ() - target.lastZ;
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        float yaw = (float) MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN - 90.0f;
        float pitch;
        if (ballistic.getValue()) {
            double distance = Math.sqrt(dx * dx + dz * dz);
            double speed;
            if (active.getItem() == Items.CROSSBOW) {
                speed = 3.15;
            } else {
                float charge = MathHelper.clamp(mc.player.getItemUseTime() / 20.0f, 0.0f, 1.0f);
                float pull = (charge * charge + charge * 2.0f) / 3.0f;
                speed = Math.max(0.5, 3.0 * pull);
            }
            double flightTicks = distance / speed;
            // Lead the target by how far it moves while the arrow is in flight,
            // then recompute the distance (and flight time) for that point.
            double leadX = dx + motionX * flightTicks;
            double leadZ = dz + motionZ * flightTicks;
            yaw = (float) MathHelper.atan2(leadZ, leadX) * MathHelper.DEGREES_PER_RADIAN - 90.0f;
            distance = Math.sqrt(leadX * leadX + leadZ * leadZ);
            flightTicks = distance / speed;
            double drop = 0.5 * 0.05 * flightTicks * flightTicks;
            pitch = (float) (-MathHelper.atan2(dy + drop, distance) * MathHelper.DEGREES_PER_RADIAN);
        } else {
            pitch = (float) (-MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * MathHelper.DEGREES_PER_RADIAN);
        }
        if (rotate.getValue()) {
            CameraAim.aim(yaw, pitch, speed.asFloat(), smoothing.asFloat() / 100.0f, false);
        } else {
            CameraAim.release();
        }
    }
}
