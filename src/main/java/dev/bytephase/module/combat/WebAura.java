package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.BlockPlacementUtil;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

/** Places cobwebs on the nearest player so they cannot move away. */
public final class WebAura extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", 5.0, 1.0, 6.0, 0.5));
    private final NumberSetting delay = add(new NumberSetting("Delay", 2.0, 0.0, 20.0, 1.0));
    private final BooleanSetting head = add(new BooleanSetting("Head", false));

    private int cooldown;

    public WebAura() {
        super("WebAura", Category.COMBAT, "Places cobwebs on the nearest player");
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
        PlayerEntity target = null;
        double best = range.getValue() * range.getValue();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) {
                continue;
            }
            double distanceSq = player.squaredDistanceTo(mc.player);
            if (distanceSq <= best) {
                best = distanceSq;
                target = player;
            }
        }
        if (target == null) {
            return;
        }
        BlockPos feet = target.getBlockPos();
        if (tryWeb(mc, feet, slot) || head.getValue() && tryWeb(mc, feet.up(), slot)) {
            cooldown = delay.asInt();
        }
    }

    private static boolean tryWeb(net.minecraft.client.MinecraftClient mc, BlockPos pos, int slot) {
        return mc.world.getBlockState(pos).isReplaceable()
                && BlockPlacementUtil.place(mc, pos, slot, true, true);
    }
}
