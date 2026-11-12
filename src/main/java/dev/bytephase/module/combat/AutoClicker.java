package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.util.AttackLimiter;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;

public final class AutoClicker extends Module {
    private final NumberSetting delayTicks = add(new NumberSetting("DelayTicks", 1.0, 0.0, 5.0, 1.0));
    private final BooleanSetting playersOnly = add(new BooleanSetting("PlayersOnly", false));
    private final BooleanSetting respectCooldown = add(new BooleanSetting("RespectCooldown", true));

    private int cooldown;

    public AutoClicker() {
        super("AutoClicker", Category.COMBAT, "Attacks repeatedly while you hold your attack button");
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
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) {
            return;
        }
        Entity target = hit.getEntity();
        if (target == mc.player || !target.isAlive()) {
            return;
        }
        if (playersOnly.getValue() && !(target instanceof PlayerEntity)) {
            return;
        }
        if (!mc.options.attackKey.isPressed()) {
            return;
        }
        if (respectCooldown.getValue() && mc.player.getAttackCooldownProgress(0.0f) < 0.9f) {
            return;
        }
        if (!AttackLimiter.tryAttack(mc.player, target, true)) {
            return;
        }
        cooldown = (int) delayTicks.asFloat();
    }
}
