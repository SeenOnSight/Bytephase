package dev.bytephase.util;

import dev.bytephase.BytePhase;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

/**
 * Keeps the client to one entity attack per tick.
 *
 * <p>Vanilla attacks on the initial attack-key press before module ticks run.
 * Without this guard, a combat module can send a second attack packet and play
 * a second swing in the same tick. The interaction-manager mixin marks vanilla
 * attacks, and auto-attack modules go through {@link #tryAttack}.</p>
 */
public final class AttackLimiter {
    private static int tick;
    private static int lastAttackTick = -1;

    private AttackLimiter() {
    }

    /** Called once at the start of every client tick. */
    public static void beginTick() {
        tick++;
    }

    /** Called for every entity attack, including vanilla attacks. */
    public static void markAttack() {
        lastAttackTick = tick;
    }

    public static boolean hasAttackedThisTick() {
        return lastAttackTick == tick;
    }

    /**
     * Attacks {@code target} unless vanilla or another module already attacked
     * this tick. Returns whether the attack was sent.
     */
    public static boolean tryAttack(PlayerEntity player, Entity target, boolean swing) {
        MinecraftClient mc = BytePhase.mc;
        if (mc == null || mc.player != player || mc.interactionManager == null
                || hasAttackedThisTick()) {
            return false;
        }
        mc.interactionManager.attackEntity(player, target);
        if (swing) {
            player.swingHand(Hand.MAIN_HAND);
        }
        return true;
    }
}
