package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.MovementUtil;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

public final class FastWeb extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", 1.5, 1.1, 3.0, 0.05));

    public FastWeb() {
        super("FastWeb", Category.MOVEMENT, "Makes you faster in cobwebs");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        BlockPos feet = BlockPos.ofFloored(mc.player.getEntityPos());
        BlockPos head = feet.up();
        if (mc.world.getBlockState(feet).getBlock() != Blocks.COBWEB
                && mc.world.getBlockState(head).getBlock() != Blocks.COBWEB) {
            return;
        }
        MovementUtil.accelerateHorizontal(mc.player, 0.25 * speed.getValue(), 0.005);
    }
}
