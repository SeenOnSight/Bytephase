package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.RenderBox;
import dev.bytephase.setting.ColorSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.LingeringPotionItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.TridentItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.item.SnowballItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class Trajectories extends Module {
    private final ColorSetting pathColor = add(new ColorSetting("PathColor", 0xCCFFFF55));
    private final ColorSetting hitColor = add(new ColorSetting("HitColor", 0xCCFF3030));
    private final List<Vec3d> path = new ArrayList<>(81);
    private Vec3d impactPos;

    public Trajectories() {
        super("Trajectories", Category.RENDER, "Predicts paths for bows and throwables");
    }

    @Override
    public void onTick() {
        path.clear();
        impactPos = null;
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        ItemStack stack = mc.player.getInventory().getStack(mc.player.getInventory().getSelectedSlot());
        if (mc.player.isUsingItem()) {
            stack = mc.player.getActiveItem();
        }
        double gravity;
        double drag;
        double initialSpeed;
        // Vanilla's setVelocity(pitch, yaw, roll, ...) lifts only the vertical
        // component by |roll| degrees; thrown potions and XP bottles use -20.
        float roll = 0.0f;
        if (stack.getItem() instanceof BowItem) {
            float charge = mc.player.isUsingItem()
                    ? Math.min(1.0f, mc.player.getItemUseTime() / 20.0f)
                    : 0.0f;
            if (charge <= 0.12f) {
                return;
            }
            gravity = 0.05;
            drag = 0.99;
            float pull = (charge * charge + 2.0f * charge) / 3.0f;
            initialSpeed = pull * 3.0;
        } else if (stack.getItem() instanceof CrossbowItem) {
            if (!CrossbowItem.isCharged(stack)) {
                return;
            }
            gravity = 0.05;
            drag = 0.99;
            initialSpeed = 3.15;
        } else if (stack.getItem() instanceof TridentItem) {
            // A trident only flies once it has been drawn for ten ticks.
            if (!mc.player.isUsingItem() || mc.player.getItemUseTime() < 10) {
                return;
            }
            gravity = 0.05;
            drag = 0.99;
            initialSpeed = 2.5;
        } else if (stack.getItem() instanceof EnderPearlItem || stack.getItem() instanceof SnowballItem
                || stack.getItem() instanceof EggItem) {
            gravity = 0.03;
            drag = 0.99;
            initialSpeed = 1.5;
        } else if (stack.getItem() instanceof SplashPotionItem
                || stack.getItem() instanceof LingeringPotionItem) {
            gravity = 0.05;
            drag = 0.99;
            initialSpeed = 0.5;
            roll = -20.0f;
        } else if (stack.getItem() instanceof ExperienceBottleItem) {
            gravity = 0.07;
            drag = 0.99;
            initialSpeed = 0.7;
            roll = -20.0f;
        } else {
            return;
        }

        // Projectiles leave the hand slightly below eye level.
        Vec3d pos = mc.player.getEyePos().subtract(0.0, 0.1, 0.0);
        double pitchRad = Math.toRadians(mc.player.getPitch());
        double yawRad = Math.toRadians(mc.player.getYaw());
        Vec3d vel = new Vec3d(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad + Math.toRadians(roll)),
                Math.cos(yawRad) * Math.cos(pitchRad)).normalize().multiply(initialSpeed);
        path.add(pos);
        for (int i = 0; i < 80; i++) {
            Vec3d moved = pos.add(vel);
            BlockPos blockPos = BlockPos.ofFloored(moved);
            if (!mc.world.getChunkManager().isChunkLoaded(
                    blockPos.getX() >> 4, blockPos.getZ() >> 4)) {
                break;
            }
            BlockHitResult hit = mc.world.raycast(new RaycastContext(pos, moved,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3d hitPos = hit.getPos();
                path.add(hitPos);
                impactPos = hitPos;
                break;
            }
            path.add(moved);
            pos = moved;
            vel = vel.multiply(drag).add(0, -gravity, 0);
        }
    }

    @Override
    public void onRender3D(net.minecraft.client.util.math.MatrixStack matrices,
                           net.minecraft.client.render.VertexConsumerProvider consumers,
                           float tickDelta) {
        if (path.isEmpty()) return;
        if (impactPos != null) {
            RenderBox.drawOutlineBox(new Box(impactPos.x - 0.1, impactPos.y - 0.1, impactPos.z - 0.1,
                    impactPos.x + 0.1, impactPos.y + 0.1, impactPos.z + 0.1), hitColor.getValue());
        }
        RenderBox.drawLineStrip(path, pathColor.getValue());
    }
}
