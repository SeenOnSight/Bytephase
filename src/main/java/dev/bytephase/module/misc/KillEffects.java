package dev.bytephase.module.misc;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public final class KillEffects extends Module {
    private PlayerEntity lastTarget;

    public KillEffects() {
        super("KillEffects", Category.MISC, "Notifies you when you take down another player");
    }

    @Override
    public void onAttack(net.minecraft.entity.player.PlayerEntity self, net.minecraft.entity.Entity target) {
        if (isEnabled() && target instanceof PlayerEntity player && target != BytePhase.mc.player) {
            lastTarget = player;
        }
    }

    @Override
    protected void onDisable() {
        lastTarget = null;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) {
            lastTarget = null;
            return;
        }
        if (lastTarget == null) {
            return;
        }
        if (lastTarget.isDead()) {
            mc.inGameHud.getChatHud().addMessage(Text.literal(
                    "[BytePhase] You took down " + lastTarget.getName().getString()));
            mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.3f));
            lastTarget = null;
        } else if (lastTarget.isRemoved() || lastTarget.squaredDistanceTo(mc.player) > 6000.0) {
            lastTarget = null;
        }
    }
}
