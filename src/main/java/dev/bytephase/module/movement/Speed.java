package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.MovementUtil;
import net.minecraft.util.math.Vec3d;

/**
 * Ground movement speed.
 *
 * <p>Refined against Anubis' {@code SpeedModule}: Vanilla mode scales the
 * sprinting ground speed, Strafe mode bunny-hops and then holds a higher air
 * speed. Both modes share the same liquid/sneak/ground gating.</p>
 */
public final class Speed extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", new String[]{"Vanilla", "Strafe"}, "Vanilla"));
    private final NumberSetting multiplier = add(new NumberSetting("Multiplier", 1.3, 1.0, 3.0, 0.05));
    private final NumberSetting strafeSpeed = add(new NumberSetting("StrafeSpeed", 0.36, 0.28, 0.70, 0.01));
    private final BooleanSetting onlyOnGround = add(new BooleanSetting("OnlyGround", false));
    private final BooleanSetting inLiquids = add(new BooleanSetting("InLiquids", false));
    private final BooleanSetting whenSneaking = add(new BooleanSetting("WhenSneaking", false));

    public Speed() {
        super("Speed", Category.MOVEMENT, "Increases movement speed");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.player.isSpectator() || mc.currentScreen != null) {
            return;
        }
        var player = mc.player;
        if (!forwardPressed(mc)) {
            return;
        }
        if (!inLiquids.getValue() && (player.isTouchingWater() || player.isInLava())) {
            return;
        }
        if (!whenSneaking.getValue() && player.isSneaking()) {
            return;
        }
        if (player.getAbilities().flying || player.isGliding()) {
            return;
        }
        if (onlyOnGround.getValue() && !player.isOnGround()) {
            return;
        }

        if (mode.getValue().equals("Strafe")) {
            if (player.isOnGround() && !player.isSneaking()) {
                Vec3d velocity = player.getVelocity();
                player.setVelocityClient(new Vec3d(velocity.x, 0.42, velocity.z));
            }
            MovementUtil.accelerateHorizontal(player, strafeSpeed.getValue(), 0.02);
            return;
        }

        // Vanilla sprinting on the ground settles around 0.287 blocks/tick.
        MovementUtil.accelerateHorizontal(player, 0.2873 * multiplier.getValue(), 0.02);
    }

    private static boolean forwardPressed(net.minecraft.client.MinecraftClient mc) {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
    }
}
