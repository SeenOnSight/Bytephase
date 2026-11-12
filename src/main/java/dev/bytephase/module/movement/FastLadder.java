package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

public final class FastLadder extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", 2.0, 1.0, 4.0, 0.1));

    public FastLadder() {
        super("FastLadder", Category.MOVEMENT, "Makes you move faster on ladders");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || !mc.player.isClimbing()) {
            return;
        }
        // Vanilla only climbs ladders while the jump key is held. Setting a
        // positive Y velocity unconditionally would make the player float up
        // every ladder they touch.
        if (!mc.options.jumpKey.isPressed()) {
            return;
        }
        Vec3d v = mc.player.getVelocity();
        mc.player.setVelocityClient(new Vec3d(v.x, 0.15 * speed.asFloat(), v.z));
    }
}
