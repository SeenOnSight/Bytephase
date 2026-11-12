package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Restores sprint after an attack so the client keeps its sprint motion.
 *
 * <p>Vanilla clears sprinting when a hit lands with the sprint knockback
 * bonus, which drops the player to walking speed for the rest of the fight.
 * This re-applies sprint for a short window after the hit, but only while a
 * forward key is held, the player is not blocked from sprinting and hunger is
 * high enough for vanilla to accept it.</p>
 */
public final class KeepSprint extends Module {
    private final NumberSetting ticks = add(new NumberSetting("Ticks", 6.0, 1.0, 20.0, 1.0));

    private int remaining;

    public KeepSprint() {
        super("KeepSprint", Category.COMBAT, "Keeps your sprint motion after attacking");
    }

    @Override
    protected void onDisable() {
        remaining = 0;
    }

    @Override
    public void onAttack(PlayerEntity self, Entity target) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player != self) {
            return;
        }
        remaining = ticks.asInt();
    }

    @Override
    public void onTick() {
        if (remaining <= 0) {
            return;
        }
        remaining--;
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.currentScreen != null) {
            return;
        }
        var player = mc.player;
        if (player.isSprinting() || player.isSneaking() || player.isUsingItem()
                || player.isTouchingWater() || player.hasVehicle()
                || player.getHungerManager().getFoodLevel() <= 6
                || !mc.options.forwardKey.isPressed()) {
            return;
        }
        player.setSprinting(true);
    }
}
