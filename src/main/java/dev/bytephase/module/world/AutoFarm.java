package dev.bytephase.module.world;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.block.CropBlock;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

/** Harvests the mature crop under the real crosshair using vanilla block interaction. */
public final class AutoFarm extends Module {
    private final NumberSetting delay = add(new NumberSetting("DelayTicks", 3, 1, 20, 1));
    private int cooldown;

    public AutoFarm() {
        super("AutoFarm", Category.WORLD, "Harvests mature crops you look at within normal block reach");
    }

    @Override
    protected void onDisable() { cooldown = 0; }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (cooldown > 0) { cooldown--; return; }
        if (mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || !mc.isWindowFocused() || !mc.player.isAlive()
                || mc.player.isSpectator() || mc.player.isUsingItem()
                || !(mc.crosshairTarget instanceof BlockHitResult hit)) return;
        double reach = mc.player.getBlockInteractionRange();
        if (mc.player.getEyePos().squaredDistanceTo(hit.getPos()) > reach * reach) return;
        var state = mc.world.getBlockState(hit.getBlockPos());
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMature(state)) return;
        if (mc.interactionManager.attackBlock(hit.getBlockPos(), hit.getSide())) {
            mc.player.swingHand(Hand.MAIN_HAND);
            cooldown = delay.asInt();
        }
    }
}
