package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

public final class ElytraFly extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.1, 4.0, 0.1));
    private final BooleanSetting takeoff = add(new BooleanSetting("Takeoff", true));

    public ElytraFly() {
        super("ElytraFly", Category.MOVEMENT, "Enhanced elytra control");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) {
            return;
        }
        if (!mc.player.isGliding()) {
            if (takeoff.getValue() && mc.currentScreen == null && mc.options.jumpKey.isPressed()
                    && mc.getNetworkHandler() != null && mc.player.checkGliding()) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(
                        mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
            return;
        }
        mc.player.fallDistance = 0.0;
        var v = mc.player.getVelocity();
        double speedValue = speed.getValue();
        double pitchRadians = Math.toRadians(mc.player.getPitch());
        double vx = v.x;
        double vz = v.z;
        if (mc.options.forwardKey.isPressed()) {
            double rad = Math.toRadians(mc.player.getYaw());
            vx = -Math.sin(rad) * speedValue;
            vz = Math.cos(rad) * speedValue;
        }
        double dy;
        if (mc.options.sneakKey.isPressed()) {
            dy = -0.4;
        } else if (mc.options.jumpKey.isPressed()) {
            dy = 0.4;
        } else {
            // Positive Minecraft pitch looks down, so this descends when the
            // camera points down and climbs when it points up.
            dy = -0.02 * pitchRadians;
        }
        // Set a target velocity instead of multiplying the previous one.
        mc.player.setVelocityClient(new net.minecraft.util.math.Vec3d(vx, dy, vz));
    }
}
