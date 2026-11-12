package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

/**
 * Climbs walls while pressing into them.
 *
 * <p>Ported from Anubis' {@code SpiderModule}: vanilla cancels vertical motion
 * when a player collides horizontally, so re-applying a small upward velocity
 * after the movement tick lets the player slide up the wall.</p>
 */
public final class Spider extends Module {
    private final NumberSetting climbSpeed = add(new NumberSetting("ClimbSpeed", 0.2, 0.05, 1.0, 0.05));
    private final BooleanSetting holdJump = add(new BooleanSetting("HoldJump", false));

    public Spider() {
        super("Spider", Category.MOVEMENT, "Climbs walls like a spider");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || !mc.player.horizontalCollision) {
            return;
        }
        if (holdJump.getValue() && !mc.options.jumpKey.isPressed()) {
            return;
        }
        Vec3d velocity = mc.player.getVelocity();
        double target = climbSpeed.getValue();
        if (velocity.y < target) {
            mc.player.setVelocityClient(new Vec3d(velocity.x, target, velocity.z));
        }
    }
}
