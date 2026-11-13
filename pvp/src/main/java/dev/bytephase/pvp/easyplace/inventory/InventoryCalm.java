package dev.bytephase.pvp.easyplace.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Input;

@Environment(EnvType.CLIENT)
public final class InventoryCalm {
    private static final double STOPPED = 0.0025;
    private static final double HALTED = 1.0E-4;

    private InventoryCalm() {
    }

    public static boolean idle(LocalPlayer player) {
        Input in = player.input.keyPresses;
        return !moving(in) && !in.shift();
    }

    public static boolean still(LocalPlayer player) {
        return idle(player) && player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() < 0.0025;
    }

    public static boolean settle(Minecraft mc, LocalPlayer player) {
        if (still(player)) {
            return true;
        }

        Options options = mc.options;
        options.keyUp.setDown(false);
        options.keyDown.setDown(false);
        options.keyLeft.setDown(false);
        options.keyRight.setDown(false);
        options.keyJump.setDown(false);
        options.keySprint.setDown(false);
        options.keyUse.setDown(false);
        options.keyAttack.setDown(false);
        Input in = player.input.keyPresses;
        if (moving(in) || !player.onGround() || player.getDeltaMovement().horizontalDistanceSqr() >= 1.0E-4) {
            return false;
        }

        if (!overGround(player)) {
            return true;
        }

        options.keyShift.setDown(false);
        return false;
    }

    private static boolean moving(Input in) {
        return in.forward() || in.backward() || in.left() || in.right() || in.jump() || in.sprint();
    }

    private static boolean overGround(LocalPlayer player) {
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
        return !player.level().getBlockState(below).getCollisionShape(player.level(), below).isEmpty();
    }
}
