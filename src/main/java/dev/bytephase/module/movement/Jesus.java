package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import net.minecraft.registry.tag.FluidTags;

public final class Jesus extends Module {
    public Jesus() {
        super("Jesus", Category.MOVEMENT, "Walks on water");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        var feetFluid = mc.world.getFluidState(mc.player.getBlockPos());
        if (!feetFluid.isIn(FluidTags.WATER) && !feetFluid.isIn(FluidTags.LAVA)) {
            return;
        }
        if (mc.options.sneakKey.isPressed()) {
            return;
        }
        var v = mc.player.getVelocity();
        boolean lava = feetFluid.isIn(FluidTags.LAVA);
        // Fully under the surface: float up. Feet-deep only: hold level
        // rather than sinking a little more every tick.
        boolean submerged = mc.world.getFluidState(mc.player.getBlockPos().up()).isIn(
                lava ? FluidTags.LAVA : FluidTags.WATER);
        double up = lava ? 0.4 : submerged ? 0.12 : 0.0;
        double y = v.y < 0.0 ? 0.0 : v.y;
        mc.player.setVelocityClient(new net.minecraft.util.math.Vec3d(v.x * 0.9, Math.max(y, up), v.z * 0.9));
    }
}
