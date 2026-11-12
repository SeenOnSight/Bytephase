package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

public final class AutoJump extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", new String[]{"Jump", "LowHop"}, "Jump"));
    private final ModeSetting jumpIf = add(new ModeSetting("JumpIf", new String[]{"Always", "Walking", "Sprinting"}, "Walking"));
    private final NumberSetting hopHeight = add(new NumberSetting("HopHeight", 0.42, 0.05, 1.0, 0.01));

    public AutoJump() {
        super("AutoJump", Category.MOVEMENT, "Jumps automatically");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) {
            return;
        }
        if (!mc.player.isOnGround() || mc.player.isSneaking() || mc.player.isUsingItem()) {
            return;
        }

        boolean moving = mc.player.forwardSpeed != 0.0f || mc.player.sidewaysSpeed != 0.0f;
        boolean shouldJump = switch (jumpIf.getValue()) {
            case "Sprinting" -> mc.player.isSprinting() && moving;
            case "Walking" -> moving;
            default -> true;
        };
        if (!shouldJump) {
            return;
        }

        if (mode.getValue().equals("LowHop")) {
            Vec3d velocity = mc.player.getVelocity();
            mc.player.setVelocityClient(new Vec3d(velocity.x, hopHeight.getValue(), velocity.z));
        } else {
            mc.player.jump();
        }
    }
}
