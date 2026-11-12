package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.RenderBox;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Draws a skeleton over players, built from the body yaw and stance of the
 * target rather than from the model's bones.
 *
 * <p>The geometry is a set of line segments plus a head box. Every offset is
 * expressed in the target's own frame (right / up / forward) and rotated by its
 * body yaw, so the skeleton turns with the player, and sneaking lowers the
 * spine to match the smaller hitbox.</p>
 */
public final class Skeleton extends Module {
    private static final double HIP_Y = 0.86;
    private static final double SHOULDER_Y = 1.44;
    private static final double NECK_Y = 1.58;
    private static final double SHOULDER_X = 0.24;
    private static final double HIP_X = 0.11;
    private static final double SNEAK_DROP = 0.24;

    private final NumberSetting range = add(new NumberSetting("Range", 48.0, 4.0, 128.0, 1.0));
    private final BooleanSetting self = add(new BooleanSetting("Self", false));
    private final BooleanSetting throughWalls = add(new BooleanSetting("ThroughWalls", true));
    private final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFFFF));

    public Skeleton() {
        super("Skeleton", Category.RENDER, "Draws a skeleton over players");
    }

    @Override
    public void onRender3D(MatrixStack matrices,
                           net.minecraft.client.render.VertexConsumerProvider consumers,
                           float tickDelta) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        double rangeSq = range.getValue() * range.getValue();
        Vec3d eye = mc.player.getEntityPos();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player && !self.getValue()) {
                continue;
            }
            if (player.isInvisible() || player.isSpectator()) {
                continue;
            }
            if (player.getEntityPos().squaredDistanceTo(eye) > rangeSq) {
                continue;
            }
            if (!dev.bytephase.render.RenderProjection.isVisible(player.getBoundingBox().expand(0.6))) {
                continue;
            }
            draw(player, tickDelta);
        }
    }

    private void draw(PlayerEntity player, float tickDelta) {
        Vec3d base = player.getLerpedPos(tickDelta);
        double yaw = Math.toRadians(player.getBodyYaw());
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);

        double drop = player.isSneaking() ? SNEAK_DROP : 0.0;
        double hipY = HIP_Y - drop;
        double shoulderY = SHOULDER_Y - drop;

        Vec3d hipCenter = at(base, rightX, rightZ, forwardX, forwardZ, 0.0, hipY, 0.0);
        Vec3d neck = at(base, rightX, rightZ, forwardX, forwardZ, 0.0, shoulderY + 0.02, 0.0);
        Vec3d headTop = at(base, rightX, rightZ, forwardX, forwardZ, 0.0, NECK_Y - drop + 0.12, 0.0);

        RenderBox.drawLine(hipCenter, neck, color.getValue(), throughWalls.getValue());
        RenderBox.drawLine(neck, headTop, color.getValue(), throughWalls.getValue());

        Vec3d leftShoulder = at(base, rightX, rightZ, forwardX, forwardZ, -SHOULDER_X, shoulderY, 0.0);
        Vec3d rightShoulder = at(base, rightX, rightZ, forwardX, forwardZ, SHOULDER_X, shoulderY, 0.0);
        RenderBox.drawLine(leftShoulder, rightShoulder, color.getValue(), throughWalls.getValue());

        Vec3d leftHand = at(base, rightX, rightZ, forwardX, forwardZ, -SHOULDER_X - 0.05, shoulderY - 0.42, 0.02);
        Vec3d rightHand = at(base, rightX, rightZ, forwardX, forwardZ, SHOULDER_X + 0.05, shoulderY - 0.42, 0.02);
        RenderBox.drawLine(leftShoulder, leftHand, color.getValue(), throughWalls.getValue());
        RenderBox.drawLine(rightShoulder, rightHand, color.getValue(), throughWalls.getValue());

        Vec3d leftHip = at(base, rightX, rightZ, forwardX, forwardZ, -HIP_X, hipY, 0.0);
        Vec3d rightHip = at(base, rightX, rightZ, forwardX, forwardZ, HIP_X, hipY, 0.0);
        RenderBox.drawLine(leftHip, rightHip, color.getValue(), throughWalls.getValue());

        Vec3d leftFoot = at(base, rightX, rightZ, forwardX, forwardZ, -HIP_X - 0.02, 0.03, 0.0);
        Vec3d rightFoot = at(base, rightX, rightZ, forwardX, forwardZ, HIP_X + 0.02, 0.03, 0.0);
        RenderBox.drawLine(leftHip, leftFoot, color.getValue(), throughWalls.getValue());
        RenderBox.drawLine(rightHip, rightFoot, color.getValue(), throughWalls.getValue());

        Vec3d headCenter = at(base, rightX, rightZ, forwardX, forwardZ, 0.0, NECK_Y - drop + 0.06, 0.0);
        RenderBox.drawOutlineBox(new Box(headCenter, headCenter).expand(0.18, 0.20, 0.18),
                color.getValue(), throughWalls.getValue());
    }

    private static Vec3d at(Vec3d base, double rightX, double rightZ,
                            double forwardX, double forwardZ,
                            double right, double up, double forward) {
        return base.add(rightX * right + forwardX * forward, up, rightZ * right + forwardZ * forward);
    }

    /** The skeleton segments for one player, as start/end pairs. Used by the regression test. */
    static List<Vec3d> segmentsFor(PlayerEntity player) {
        Vec3d base = player.getEntityPos();
        double yaw = Math.toRadians(player.getBodyYaw());
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double drop = player.isSneaking() ? SNEAK_DROP : 0.0;
        List<Vec3d> segments = new ArrayList<>();
        segments.add(at(base, rightX, rightZ, forwardX, forwardZ, 0.0, HIP_Y - drop, 0.0));
        segments.add(at(base, rightX, rightZ, forwardX, forwardZ, 0.0, SHOULDER_Y - drop + 0.02, 0.0));
        segments.add(at(base, rightX, rightZ, forwardX, forwardZ, -SHOULDER_X, SHOULDER_Y - drop, 0.0));
        segments.add(at(base, rightX, rightZ, forwardX, forwardZ, SHOULDER_X, SHOULDER_Y - drop, 0.0));
        return segments;
    }
}
