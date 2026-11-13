package dev.bytephase.pvp.util.pvp;

import dev.bytephase.pvp.module.impl.movement.SprintModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;

@Environment(EnvType.CLIENT)
public final class InputControl {
    private static boolean dropSprint;
    private static boolean releaseForward;
    private static boolean backward;
    private static boolean jump;
    private static boolean idle;
    private static boolean sprintSuppressed;

    private InputControl() {
    }

    public static void dropSprint() {
        dropSprint = true;
    }

    public static void releaseForward(boolean backward) {
        releaseForward = true;
        InputControl.backward |= backward;
    }

    public static void jump() {
        jump = true;
    }

    public static void idle() {
        idle = true;
    }

    public static void beginTick() {
        sprintSuppressed = false;
    }

    public static boolean sprintSuppressed() {
        return sprintSuppressed || dropSprint || idle;
    }

    public static Input shape(LocalPlayer player, Input keys) {
        boolean forward = keys.forward();
        boolean back = keys.backward();
        boolean left = keys.left();
        boolean right = keys.right();
        boolean up = keys.jump();
        boolean sprint = keys.sprint() || SprintModule.holding();
        if (idle) {
            sprint = false;
            up = false;
            right = false;
            left = false;
            back = false;
            forward = false;
        }

        if (releaseForward && !idle) {
            forward = false;
            back |= backward;
        }

        if (jump && !idle) {
            up = true;
        }

        if (dropSprint || idle) {
            sprintSuppressed = true;
            sprint = false;
            player.setSprinting(false);
        }

        idle = false;
        jump = false;
        backward = false;
        releaseForward = false;
        dropSprint = false;
        return forward == keys.forward()
                && back == keys.backward()
                && left == keys.left()
                && right == keys.right()
                && up == keys.jump()
                && sprint == keys.sprint()
            ? keys
            : new Input(forward, back, left, right, up, keys.shift(), sprint);
    }
}
