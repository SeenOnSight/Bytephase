package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.item.BowItem;

public final class AutoBowRelease extends Module {
    private final NumberSetting chargeTicks = add(new NumberSetting("ChargeTicks", 20.0, 5.0, 40.0, 1.0));

    public AutoBowRelease() {
        super("AutoBowRelease", Category.COMBAT, "Releases your bow at full charge");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.interactionManager == null || mc.currentScreen != null) {
            return;
        }
        if (!mc.player.isUsingItem()) {
            return;
        }
        if (!(mc.player.getActiveItem().getItem() instanceof BowItem)) {
            return;
        }
        if (mc.player.getItemUseTime() >= (int) chargeTicks.asFloat()) {
            mc.interactionManager.stopUsingItem(mc.player);
        }
    }
}
