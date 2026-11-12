package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Stops sprinting immediately after a hit so the next hit can land the full
 * sprint knockback again.
 *
 * <p>Servers award the boosted knockback only when the attacker is sprinting,
 * and vanilla drops sprinting once that bonus is used. Clearing it explicitly
 * here means the client re-enters sprint before the next swing instead of the
 * server treating every follow-up hit as a reduced one. This is the logical
 * opposite of {@link KeepSprint}; enable one or the other.</p>
 */
public final class SprintReset extends Module {
    private final BooleanSetting playersOnly = add(new BooleanSetting("PlayersOnly", true));

    public SprintReset() {
        super("SprintReset", Category.COMBAT, "Resets sprint after a hit to keep full knockback");
    }

    @Override
    public void onAttack(PlayerEntity self, Entity target) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player != self) {
            return;
        }
        if (playersOnly.getValue() && !(target instanceof PlayerEntity)) {
            return;
        }
        if (!self.isSprinting()) {
            return;
        }
        self.setSprinting(false);
    }
}
