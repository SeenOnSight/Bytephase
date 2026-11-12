package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Pulls you down quickly when the block below has room for a step. */
public final class ReverseStep extends Module {
    private final NumberSetting fallSpeed = add(new NumberSetting("FallSpeed", 3.0, 0.1, 10.0, 0.1));
    private final NumberSetting fallDistance = add(new NumberSetting("FallDistance", 3.0, 0.5, 8.0, 0.5));

    public ReverseStep() {
        super("ReverseStep", Category.MOVEMENT, "Falls down blocks faster");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        if (!mc.player.isOnGround() || mc.player.isSubmergedInWater() || mc.player.isInLava()
                || mc.options.jumpKey.isPressed() || mc.player.noClip) {
            return;
        }
        if (mc.player.forwardSpeed == 0.0f && mc.player.sidewaysSpeed == 0.0f) {
            return;
        }

        // Look at where the player will be next tick. Standing on ground the
        // space just below is solid, so nothing happens; only when that space
        // is empty (a ledge) and solid ground exists within FallDistance below
        // it (not the void) is the player pulled down.
        Vec3d velocity = mc.player.getVelocity();
        Box ahead = mc.player.getBoundingBox().offset(velocity.x, 0.0, velocity.z);
        if (!mc.world.isSpaceEmpty(ahead.offset(0.0, -0.05, 0.0))) {
            return;
        }
        if (mc.world.isSpaceEmpty(ahead.offset(0.0, -(fallDistance.getValue() + 0.01), 0.0))) {
            return;
        }
        mc.player.setVelocityClient(new Vec3d(velocity.x, -fallSpeed.getValue(), velocity.z));
    }
}
