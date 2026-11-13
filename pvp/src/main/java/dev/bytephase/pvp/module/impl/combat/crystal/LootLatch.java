package dev.bytephase.pvp.module.impl.combat.crystal;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import dev.bytephase.pvp.event.impl.PacketAppliedEvent;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

@Environment(EnvType.CLIENT)
public final class LootLatch {
    private static final double RANGE_SQR = 100.0;
    private static final int GRACE_TICKS = 10;
    private static final long ACTIVE_MS = 250L;
    private static final Int2IntOpenHashMap DEAD = new Int2IntOpenHashMap();
    private static LocalPlayer self;
    private static int selfTick = -1;
    private static int clock;
    private static boolean reported;
    private static boolean latched;
    private static boolean killed;
    private static long updatedAt;

    private LootLatch() {
    }

    public static void update(Minecraft mc, LocalPlayer player) {
        if (player != self || player.tickCount != selfTick) {
            self = player;
            selfTick = player.tickCount;
            KeyMapping use = mc.options.keyUse;
            evaluate(mc.level, player, Crystals.isPhysicallyDown(mc, use), MeleeAttacks.clickPending(use));
        }
    }

    public static boolean latched() {
        return latched;
    }

    public static boolean killedNow() {
        return killed;
    }

    public static boolean active() {
        return latched && Util.getMillis() - updatedAt < 250L;
    }

    private static void evaluate(ClientLevel level, LocalPlayer player, boolean down, boolean newPress) {
        int now = ++clock;
        updatedAt = Util.getMillis();
        DEAD.int2IntEntrySet()
            .removeIf(entry -> now - entry.getIntValue() > 10 && !(level.getEntity(entry.getIntKey()) instanceof Player otherx && otherx.isDeadOrDying()));
        boolean fresh = reported;
        reported = false;

        for (Player other : level.players()) {
            if (other != player && other.isDeadOrDying() && !DEAD.containsKey(other.getId())) {
                DEAD.put(other.getId(), now);
                if (other.distanceToSqr(player) <= 100.0) {
                    fresh = true;
                }
            }
        }

        killed = fresh;
        if (fresh && down) {
            latched = true;
        } else if (!down || latched && newPress) {
            latched = false;
        }
    }

    private static void onPacket(PacketAppliedEvent event) {
        if (event.packet instanceof ClientboundEntityEventPacket packet && packet.getEventId() == 3) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                if (packet.getEntity(mc.level) instanceof Player other && other != mc.player && !DEAD.containsKey(other.getId())) {
                    DEAD.put(other.getId(), clock);
                    if (other.distanceToSqr(mc.player) <= 100.0) {
                        reported = true;
                    }
                }
            }
        }
    }

    static {
        PacketAppliedEvent.TYPE.listen(LootLatch.class, LootLatch::onPacket);
    }
}
