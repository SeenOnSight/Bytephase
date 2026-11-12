package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.BlockPlacementUtil;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

/** Places a cobweb on your own feet, slowing anything that tries to reach you. */
public final class SelfWeb extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", 2.0, 0.0, 20.0, 1.0));
    private final BooleanSetting head = add(new BooleanSetting("Head", false));

    private int cooldown;

    public SelfWeb() {
        super("SelfWeb", Category.COMBAT, "Places cobwebs on your own position");
    }

    @Override
    protected void onEnable() {
        cooldown = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || mc.player.isSpectator()) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        int slot = BlockPlacementUtil.findHotbarBlock(mc, Blocks.COBWEB);
        if (slot < 0) {
            return;
        }
        BlockPos feet = mc.player.getBlockPos();
        boolean placed = mc.world.getBlockState(feet).isReplaceable()
                && BlockPlacementUtil.place(mc, feet, slot, true, true);
        if (!placed && head.getValue() && mc.world.getBlockState(feet.up()).isReplaceable()) {
            placed = BlockPlacementUtil.place(mc, feet.up(), slot, true, true);
        }
        if (placed) {
            cooldown = delay.asInt();
        }
    }
}
