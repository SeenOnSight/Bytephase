package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;

public final class SafeWalk extends Module {
    public SafeWalk() {
        super("SafeWalk", Category.MOVEMENT, "Prevents walking off block edges");
    }

    public Input swapSneak() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || !isEnabled()) {
            return null;
        }
        if (!mc.player.isOnGround() || mc.player.isGliding() || mc.player.isTouchingWater()) {
            return null;
        }
        Input input = mc.player.input;
        if (input == null || input.playerInput == null || input.playerInput.sneak()) {
            return null;
        }
        PlayerInput playerInput = input.playerInput;
        float forward = playerInput.forward() ? 1.0f : playerInput.backward() ? -1.0f : 0.0f;
        float strafe = playerInput.left() ? 1.0f : playerInput.right() ? -1.0f : 0.0f;
        if (forward == 0.0f && strafe == 0.0f) {
            return null;
        }

        double yaw = Math.toRadians(mc.player.getYaw());
        double dx = -Math.sin(yaw) * forward + Math.cos(yaw) * strafe;
        double dz = Math.cos(yaw) * forward + Math.sin(yaw) * strafe;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0e-4) {
            return null;
        }
        dx /= length;
        dz /= length;

        double lookAhead = 0.55;
        BlockPos below = BlockPos.ofFloored(
                mc.player.getX() + dx * lookAhead,
                mc.player.getY() - 0.2,
                mc.player.getZ() + dz * lookAhead);
        if (!mc.world.getBlockState(below).getCollisionShape(mc.world, below).isEmpty()) {
            return null;
        }
        return input;
    }
}
