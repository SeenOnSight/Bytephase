package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.RenderBox;
import dev.bytephase.render.RenderProjection;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.LingeringPotionItem;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Nominal launch previews using the client's 1.21.11 projectile update order. */
public class Trajectories extends Module {
    private final ColorSetting pathColor = add(new ColorSetting("PathColor", 0xCC6C6FF5));
    private final ColorSetting hitColor = add(new ColorSetting("HitColor", 0xEFFF6677));
    private final NumberSetting ticks = add(new NumberSetting("PredictionTicks", 160, 40, 300, 20));
    private final NumberSetting previewHz = add(new NumberSetting("PreviewHz", 30, 10, 60, 5));
    private final BooleanSetting entities = add(new BooleanSetting("EntityCollisions", true));
    private final BooleanSetting impact = add(new BooleanSetting("ShowImpact", true));
    private final BooleanSetting walls = add(new BooleanSetting("ThroughWalls", true));
    private final List<Vec3d> path = new ArrayList<>(301);
    private final boolean throwablesOnly;
    private Vec3d impactPos;
    private long predictedAt;

    private record Launch(double gravity, double drag, double waterDrag, double speed,
                          float roll, double radius, double eyeOffset, boolean thrown) { }

    public Trajectories() { this("Trajectories", false); }

    protected Trajectories(String name, boolean throwablesOnly) {
        super(name, Category.RENDER, throwablesOnly
            ? "Shows throwable flight paths and block/entity impact points, including off-hand items"
            : "Predicts flight paths for bows, tridents and throwables");
        this.throwablesOnly = throwablesOnly;
    }

    @Override protected void onEnable() { predictedAt = 0; path.clear(); impactPos = null; }
    @Override protected void onDisable() { path.clear(); impactPos = null; }
    @Override public void onTick() {
        if (BytePhase.mc.player == null || BytePhase.mc.world == null) { path.clear(); impactPos = null; }
    }

    @Override public void onFrame() {
        long now = System.nanoTime();
        if (now - predictedAt < 1_000_000_000L / previewHz.asInt()) return;
        predictedAt = now;
        path.clear(); impactPos = null;
        var mc = BytePhase.mc;
        if (mc.player == null || mc.world == null) return;
        var player = mc.player;
        ItemStack stack = player.isUsingItem() ? player.getActiveItem() : player.getMainHandStack();
        Launch launch = launch(stack);
        if (launch == null && !player.isUsingItem()) launch = launch(player.getOffHandStack());
        if (launch == null) return;
        // The dedicated module owns throwable previews when both entries are enabled.
        ThrowableTrajectory dedicated = BytePhase.modules.get(ThrowableTrajectory.class);
        if (!throwablesOnly && launch.thrown && dedicated != null && dedicated.isEnabled()) return;

        Vec3d pos = player.getEyePos().add(player.getLerpedPos(RenderProjection.tickDelta())
            .subtract(player.getEntityPos())).subtract(0, launch.eyeOffset, 0);
        double pitch = Math.toRadians(player.getPitch()), yaw = Math.toRadians(player.getYaw());
        Vec3d velocity = new Vec3d(-Math.sin(yaw) * Math.cos(pitch),
            -Math.sin(pitch + Math.toRadians(launch.roll)), Math.cos(yaw) * Math.cos(pitch))
            .normalize().multiply(launch.speed);
        Vec3d movement = player.getMovement();
        velocity = velocity.add(movement.x, player.isOnGround() ? 0 : movement.y, movement.z);
        path.add(pos);
        for (int i = 0; i < ticks.asInt(); i++) {
            boolean water = mc.world.getFluidState(BlockPos.ofFloored(pos)).isIn(FluidTags.WATER);
            if (launch.thrown) {
                // ThrownEntity applies gravity and drag before its collision/movement step.
                velocity = velocity.add(0, -launch.gravity, 0).multiply(water ? launch.waterDrag : launch.drag);
            } else if (water) {
                // PersistentProjectileEntity applies water drag before moving, air drag afterwards.
                velocity = velocity.multiply(launch.waterDrag);
            }
            Vec3d next = pos.add(velocity);
            BlockPos block = BlockPos.ofFloored(next);
            if (!mc.world.getChunkManager().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                    || next.y < mc.world.getBottomY() - 64) break;
            var blockHit = mc.world.raycast(new RaycastContext(pos, next,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
            Vec3d hit = blockHit.getType() == HitResult.Type.MISS ? null : blockHit.getPos();
            double nearest = hit == null ? pos.squaredDistanceTo(next) : pos.squaredDistanceTo(hit);
            if (entities.getValue()) {
                Box sweep = new Box(pos, next).expand(launch.radius + 1.0);
                for (Entity entity : mc.world.getOtherEntities(player, sweep,
                        candidate -> !candidate.isSpectator() && candidate.canHit() && !candidate.isRemoved())) {
                    if (entity.isConnectedThroughVehicle(player)) continue;
                    var entry = entity.getBoundingBox().expand(launch.radius + entity.getTargetingMargin()).raycast(pos, next);
                    if (entry.isPresent()) {
                        double distance = pos.squaredDistanceTo(entry.get());
                        if (distance <= nearest) { hit = entry.get(); nearest = distance; }
                    }
                }
            }
            if (hit != null) { path.add(hit); impactPos = hit; break; }
            path.add(next); pos = next;
            if (!launch.thrown) {
                if (!water) velocity = velocity.multiply(launch.drag);
                velocity = velocity.add(0, -launch.gravity, 0);
            }
        }
    }

    private Launch launch(ItemStack stack) {
        var player = BytePhase.mc.player;
        if (stack.getItem() instanceof BowItem) {
            if (throwablesOnly || !player.isUsingItem()) return null;
            float charge = Math.min(1f, player.getItemUseTime() / 20f);
            float pull = Math.min(1f, (charge * charge + 2f * charge) / 3f);
            if (pull < 0.1f) return null;
            return new Launch(0.05, 0.99, 0.6, pull * 3, 0, 0.25, 0.1, false);
        }
        if (stack.getItem() instanceof CrossbowItem) {
            if (throwablesOnly || !CrossbowItem.isCharged(stack)) return null;
            return new Launch(0.05, 0.99, 0.6, 3.15, 0, 0.25, 0.1, false);
        }
        if (stack.getItem() instanceof TridentItem) {
            if (throwablesOnly || !player.isUsingItem() || player.getItemUseTime() < 10) return null;
            return new Launch(0.05, 0.99, 0.99, 2.5, 0, 0.25, 0.1, false);
        }
        if (stack.isOf(Items.WIND_CHARGE)) return new Launch(0, 1, 1, 1.5, 0, 0.15625, 0, true);
        if (stack.getItem() instanceof EnderPearlItem || stack.getItem() instanceof SnowballItem || stack.getItem() instanceof EggItem)
            return new Launch(0.03, 0.99, 0.8, 1.5, 0, 0.125, 0.1, true);
        if (stack.getItem() instanceof SplashPotionItem || stack.getItem() instanceof LingeringPotionItem)
            return new Launch(0.05, 0.99, 0.8, 0.5, -20, 0.125, 0.1, true);
        if (stack.getItem() instanceof ExperienceBottleItem)
            return new Launch(0.07, 0.99, 0.8, 0.7, -20, 0.125, 0.1, true);
        return null;
    }

    @Override public void onRender3D(net.minecraft.client.util.math.MatrixStack matrices,
            net.minecraft.client.render.VertexConsumerProvider consumers, float tickDelta) {
        if (path.size() < 2) return;
        if (impact.getValue() && impactPos != null) {
            RenderBox.drawOutlineBox(new Box(impactPos.x - 0.12, impactPos.y - 0.12, impactPos.z - 0.12,
                impactPos.x + 0.12, impactPos.y + 0.12, impactPos.z + 0.12), hitColor.getValue(), walls.getValue());
        }
        RenderBox.drawLineStrip(path, pathColor.getValue(), walls.getValue());
    }
}
