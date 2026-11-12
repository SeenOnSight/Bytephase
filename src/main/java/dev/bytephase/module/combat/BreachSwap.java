package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.MaceUtil;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Swaps to the right mace for the target right before a smash lands.
 *
 * <p>Breach shaves 15% per level off the target's armor, so it wins against
 * heavily armored players; Density adds damage per block fallen and wins
 * against everything else. Hold both in your hotbar and this picks per hit.
 * If AutoWindBurst is on and you already hold a Wind Burst mace, the swap is
 * skipped so the launch is not lost.</p>
 */
public final class BreachSwap extends Module {
    private final NumberSetting minArmor = add(new NumberSetting("MinArmor", 10.0, 0.0, 20.0, 1.0));
    private final BooleanSetting smashOnly = add(new BooleanSetting("SmashOnly", true));
    private final BooleanSetting densityFallback = add(new BooleanSetting("DensityFallback", true));
    private final BooleanSetting swapBack = add(new BooleanSetting("SwapBack", true));

    private int restoreSlot = -1;

    public BreachSwap() {
        super("BreachSwap", Category.COMBAT, "Swaps to a Breach or Density mace depending on the target's armor");
    }

    @Override
    protected void onDisable() {
        restoreSlot = -1;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (restoreSlot >= 0 && mc != null && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(restoreSlot);
        }
        restoreSlot = -1;
    }

    @Override
    public void onBeforeAttack(PlayerEntity self, Entity target) {
        var mc = BytePhase.mc;
        if (mc == null || self != mc.player || !(target instanceof LivingEntity living)) {
            return;
        }
        // Swapping away from the Wind Burst mace would lose the launch the loop depends on.
        if (ElytraMace.isBusy()) {
            return;
        }
        if (smashOnly.getValue() && !MaceUtil.canSmash(self)) {
            return;
        }
        AutoWindBurst windBurst = BytePhase.modules.get(AutoWindBurst.class);
        if (windBurst != null && windBurst.isEnabled()
                && MaceUtil.level(self.getMainHandStack(), Enchantments.WIND_BURST) > 0) {
            return;
        }
        int breach = MaceUtil.findHotbarMace(self, Enchantments.BREACH);
        int density = MaceUtil.findHotbarMace(self, Enchantments.DENSITY);
        boolean armored = living.getArmor() >= minArmor.asInt();
        int preferred = armored ? breach : (densityFallback.getValue() ? density : breach);
        int fallback = armored ? (densityFallback.getValue() ? density : -1) : breach;
        int chosen = preferred >= 0 ? preferred : fallback;
        int selected = self.getInventory().getSelectedSlot();
        if (chosen < 0 || chosen == selected) {
            return;
        }
        if (swapBack.getValue() && restoreSlot < 0) {
            restoreSlot = selected;
        }
        // The hook runs before the interaction manager syncs the slot, so the
        // server sees the swap together with the attack.
        self.getInventory().setSelectedSlot(chosen);
    }
}
