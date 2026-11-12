package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.util.math.Box;

/** Jumps when you are about to walk off a block. */
public final class Parkour extends Module {
    private final NumberSetting edgeDistance = add(new NumberSetting("EdgeDistance", 0.02, 0.001, 0.1, 0.001));
    private final NumberSetting lookAhead = add(new NumberSetting("LookAhead", 0.25, 0.05, 0.75, 0.05));

    public Parkour() {
        super("Parkour", Category.MOVEMENT, "Jumps at the edge of blocks");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        if (!mc.player.isOnGround() || mc.player.isSneaking() || mc.options.jumpKey.isPressed()) {
            return;
        }

        double margin = edgeDistance.getValue();
        var velocity = mc.player.getVelocity();
        double horizontalSpeed = velocity.horizontalLength();
        if (horizontalSpeed < 0.03) {
            return;
        }
        double lead = Math.min(0.75, lookAhead.getValue());
        double scale = lead / horizontalSpeed;
        Box box = mc.player.getBoundingBox().offset(velocity.x * scale, -0.5, velocity.z * scale)
                .contract(margin, 0.0, margin);
        if (mc.world.getBlockCollisions(mc.player, box).iterator().hasNext()) {
            return;
        }
        mc.player.jump();
    }
}
