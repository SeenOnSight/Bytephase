package dev.bytephase.pvp.module.impl.combat.crystal;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class HurtClock {
    private static final int NEVER = -1000;
    private static final int WINDOW = 10;
    private static final int MAX_WAIT = 20;
    private static final int MAX_MARGIN = 2;
    private static final int RELAX_STREAK = 8;
    private static final int REMOVE_GRACE = 3;
    private static final double ANCHOR_MATCH_SQR = 0.25;
    private static final Int2ObjectOpenHashMap<HurtClock.Mark> MARKS = new Int2ObjectOpenHashMap<>();
    private static LocalPlayer lastPlayer;
    private static Level lastLevel;
    private static Packet<?> lastPacket;
    private static int now;
    private static int margin;
    private static int streak;

    private HurtClock() {
    }

    public static void note(LocalPlayer player, Packet<?> packet) {
        if (packet != lastPacket) {
            lastPacket = packet;
            if (packet instanceof ClientboundBundlePacket bundle) {
                for (Packet<?> inner : bundle.subPackets()) {
                    handle(player, inner);
                }
            } else {
                handle(player, packet);
            }
        }
    }

    public static void tick(LocalPlayer player) {
        now = player.tickCount;
        if (player != lastPlayer || player.level() != lastLevel) {
            MARKS.clear();
            margin = 0;
            streak = 0;
            lastPlayer = player;
            lastLevel = player.level();
        }

        ObjectIterator<Entry<HurtClock.Mark>> iterator = MARKS.int2ObjectEntrySet().iterator();

        while (iterator.hasNext()) {
            Entry<HurtClock.Mark> entry = iterator.next();
            if (lastLevel.getEntity(entry.getIntKey()) instanceof LivingEntity target && target.isAlive()) {
                HurtClock.Mark mark = entry.getValue();
                if (mark.removedAt != -1000 && !mark.confirmed && now - mark.removedAt > 3) {
                    margin = Math.min(2, margin + 1);
                    streak = 0;
                    mark.shotTick = -1000;
                    mark.removedAt = -1000;
                }
            } else {
                iterator.remove();
            }
        }
    }

    public static boolean freshAt(LivingEntity target, int ahead, int lead) {
        if (target.invulnerableTime - ahead - lead > 10) {
            return false;
        }

        HurtClock.Mark mark = MARKS.get(target.getId());
        return mark == null || mark.shotTick == -1000 || now + ahead - mark.shotTick >= 10 + margin;
    }

    public static int opensIn(LivingEntity target, int lead) {
        for (int ahead = 0; ahead < 20; ahead++) {
            if (freshAt(target, ahead, lead)) {
                return ahead;
            }
        }

        return 20;
    }

    public static float effective(CrystalDamage.Victim victim, float raw, int ahead, int lead) {
        LivingEntity target = victim.entity();
        if (freshAt(target, ahead, lead)) {
            return victim.reduce(raw);
        }

        HurtClock.Mark mark = MARKS.get(target.getId());
        float last = mark == null ? Float.MAX_VALUE : mark.lastRaw;
        return raw > last ? victim.reduce(raw - last) : 0.0F;
    }

    public static void shot(LocalPlayer player, LivingEntity target, float raw, Object key, int lead) {
        HurtClock.Mark mark = MARKS.computeIfAbsent(target.getId(), id -> new HurtClock.Mark());
        if (freshAt(target, 0, lead)) {
            mark.shotTick = player.tickCount;
            mark.shotRaw = raw;
            mark.lastRaw = raw;
            mark.shotKey = key;
            mark.confirmed = false;
            mark.removedAt = -1000;
        } else {
            mark.lastRaw = Math.max(mark.lastRaw, raw);
        }
    }

    private static void handle(LocalPlayer player, Packet<?> packet) {
        if (packet instanceof ClientboundDamageEventPacket damage) {
            damaged(player, damage);
        } else if (packet instanceof ClientboundRemoveEntitiesPacket remove) {
            removed(remove.getEntityIds());
        }
    }

    private static void damaged(LocalPlayer player, ClientboundDamageEventPacket packet) {
        if (player.level().getEntity(packet.entityId()) instanceof Player target && MeleeAttacks.isEnemy(player, target)) {
            HurtClock.Mark mark = MARKS.computeIfAbsent(target.getId(), id -> new HurtClock.Mark());
            if (mark.shotTick != -1000 && !mark.confirmed && ours(player, mark, packet)) {
                mark.lastRaw = mark.shotRaw;
                mark.confirmed = true;
                if (mark.shotKey instanceof Integer && ++streak >= 8) {
                    margin = Math.max(0, margin - 1);
                    streak = 0;
                }
            } else {
                Holder<DamageType> type = packet.sourceType();
                mark.shotTick = -1000;
                mark.removedAt = -1000;
                mark.lastRaw = !type.is(DamageTypes.PLAYER_ATTACK) && !type.is(DamageTypes.MOB_ATTACK) ? Float.MAX_VALUE : 0.0F;
            }
        }
    }

    private static boolean ours(LocalPlayer player, HurtClock.Mark mark, ClientboundDamageEventPacket packet) {
        Holder<DamageType> type = packet.sourceType();
        return type.is(DamageTypes.PLAYER_EXPLOSION)
            ? packet.sourceCauseId() == player.getId() && mark.shotKey instanceof Integer crystal && packet.sourceDirectId() == crystal
            : type.is(DamageTypes.BAD_RESPAWN_POINT)
                && mark.shotKey instanceof Vec3 center
                && packet.sourcePosition().filter(at -> at.distanceToSqr(center) < 0.25).isPresent();
    }

    private static void removed(IntList ids) {
        for (HurtClock.Mark mark : MARKS.values()) {
            if (mark.shotTick != -1000 && !mark.confirmed && mark.shotKey instanceof Integer crystal && ids.contains(crystal.intValue())) {
                mark.removedAt = now;
            }
        }
    }

    @Environment(EnvType.CLIENT)
    private static final class Mark {
        float lastRaw = Float.MAX_VALUE;
        int shotTick = -1000;
        float shotRaw;
        Object shotKey;
        boolean confirmed;
        int removedAt = -1000;
    }
}
