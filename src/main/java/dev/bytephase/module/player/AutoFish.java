package dev.bytephase.module.player;

import dev.bytephase.BytePhase;
import dev.bytephase.mixin.FishingBobberAccessor;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.util.Hand;

public final class AutoFish extends Module {
    private final NumberSetting delay = add(new NumberSetting("RecastTicks", 25.0, 10.0, 60.0, 1.0));
    private int cooldown;

    public AutoFish() {
        super("AutoFish", Category.PLAYER, "Fishes for you");
    }

    @Override
    protected void onEnable() {
        cooldown = 0;
    }

    @Override
    public void onTick() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.interactionManager == null || mc.currentScreen != null) {
            return;
        }
        if (!(mc.player.getMainHandStack().getItem() instanceof FishingRodItem)) {
            return;
        }
        FishingBobberEntity bobber = mc.player.fishHook;
        if (bobber == null) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            cooldown = 20;
            return;
        }
        if (((FishingBobberAccessor) bobber).bytephase$isCaught()) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            cooldown = (int) delay.asFloat();
        }
    }
}
