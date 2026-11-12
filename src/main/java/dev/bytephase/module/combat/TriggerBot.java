package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.util.AttackLimiter;
import dev.bytephase.setting.BooleanSetting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

public final class TriggerBot extends Module {
    public final BooleanSetting onlyCrit = add(new BooleanSetting("OnlyFullCharge", true));

    private int cooldown;

    public TriggerBot() {
        super("TriggerBot", Category.COMBAT, "Attacks whatever your crosshair is on");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        HitResult target = mc.crosshairTarget;
        if (target == null || target.getType() != HitResult.Type.ENTITY) {
            return;
        }
        Entity entity = ((EntityHitResult) target).getEntity();
        if (entity == mc.player || !(entity instanceof LivingEntity) || !entity.isAlive()) {
            return;
        }
        if (onlyCrit.getValue() && mc.player.getAttackCooldownProgress(0.0f) < 0.9f) {
            return;
        }
        if (!AttackLimiter.tryAttack(mc.player, entity, true)) {
            return;
        }
        cooldown = 4;
    }
}
