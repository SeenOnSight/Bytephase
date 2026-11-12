package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;

/**
 * Keeps the levitation effect off the local player. The effect packet is
 * dropped before the client applies it, and an effect that was already active
 * when the module was enabled is removed.
 */
public final class AntiLevitation extends Module {
    public AntiLevitation() {
        super("AntiLevitation", Category.MOVEMENT, "Cancels levitation on you");
    }

    @Override
    protected void onEnable() {
        clearEffect();
    }

    @Override
    public void onTick() {
        clearEffect();
    }

    private static void clearEffect() {
        var mc = BytePhase.mc;
        if (mc != null && mc.player != null) {
            mc.player.removeStatusEffect(StatusEffects.LEVITATION);
        }
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        var mc = BytePhase.mc;
        if (!isEnabled() || mc == null || mc.player == null) {
            return false;
        }
        return packet instanceof EntityStatusEffectS2CPacket effect
                && effect.getEntityId() == mc.player.getId()
                && StatusEffects.LEVITATION.equals(effect.getEffectId());
    }
}
