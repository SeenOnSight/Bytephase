package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.PlayerInput;

/**
 * Releases forward and sprint just before hitting a player so the opponent's
 * return knockback is reduced.
 *
 * <p>Adapted from Anubis' {@code WTapRecodeModule}. The sprint command and the
 * input rewrite happen before the attack packet is sent; {@link #maskInput}
 * runs inside {@code ClientPlayerEntity.tickMovement}, which is the only place
 * the client movement input is actually consumed.</p>
 */
public final class WTap extends Module {
    private final NumberSetting holdTicks = add(new NumberSetting("HoldTicks", 2.0, 1.0, 10.0, 1.0));
    private final NumberSetting cooldown = add(new NumberSetting("Cooldown", 4.0, 0.0, 20.0, 1.0));

    private int remaining;
    private int cooldownLeft;

    public WTap() {
        super("WTap", Category.COMBAT, "Stops sprinting right after a hit");
    }

    @Override
    protected void onDisable() {
        remaining = 0;
        cooldownLeft = 0;
    }

    @Override
    public void onBeforeAttack(PlayerEntity self, Entity target) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player != self || cooldownLeft > 0 || !(target instanceof PlayerEntity)) {
            return;
        }
        remaining = holdTicks.asInt();
        cooldownLeft = cooldown.asInt() + remaining;
        if (mc.player.isSprinting() && mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(
                    mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            mc.player.setSprinting(false);
        }
    }

    @Override
    public void onTickAlways() {
        if (cooldownLeft > 0) {
            cooldownLeft--;
        }
        if (remaining > 0) {
            remaining--;
        }
    }

    /** @return true while forward/sprint input should be suppressed. */
    public boolean masking() {
        return isEnabled() && remaining > 0;
    }

    public PlayerInput maskInput(PlayerInput input) {
        return new PlayerInput(false, input.backward(), input.left(), input.right(),
                input.jump(), input.sneak(), false);
    }
}
