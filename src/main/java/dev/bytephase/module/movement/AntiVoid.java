package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public final class AntiVoid extends Module {
    private final NumberSetting trigger = add(new NumberSetting("FallBlocks", 3.0, 0.0, 10.0, 0.5));

    public AntiVoid() {
        super("AntiVoid", Category.MOVEMENT, "Bounces you back up before you fall into the void");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc.player == null || mc.world == null) {
            return;
        }
        Entity self = mc.player;
        if (self.isOnGround() || self.fallDistance <= trigger.asFloat()) {
            return;
        }
        double floor = mc.world.getDimension().minY() + trigger.asFloat();
        if (self.getEntityPos().y > floor) {
            return;
        }
        Vec3d v = self.getVelocity();
        self.setVelocityClient(new Vec3d(v.x, 0.35, v.z));
        self.fallDistance = 0.0;
    }
}
