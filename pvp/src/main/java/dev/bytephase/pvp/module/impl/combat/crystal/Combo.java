package dev.bytephase.pvp.module.impl.combat.crystal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntFunction;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class Combo {
    public static final double TARGET_RANGE = 10.0;
    public static final int MAX_TARGETS = 3;
    public static final float LETHAL_RANK = 1000.0F;
    public static final float POP_RANK = 500.0F;
    public static final float SELF_WEIGHT = 0.5F;
    public static final float ANCHOR_EDGE = 1.0F;
    private static final float BLAST_PROOF = 600.0F;
    private static final double FOCUS_BONUS = 2.0;
    private static final double WEAK_BONUS = 2.0;
    private static final float WEAK_HEALTH = 10.0F;
    private static final double MAX_STEP_SQR = 1.0;
    private static final double GRAVITY = 0.08;
    private static final double AIR_DRAG = 0.98;
    private static final Combo.Kind[] SETUP = new Combo.Kind[]{Combo.Kind.CHARGE, Combo.Kind.CRYSTAL, Combo.Kind.OBSIDIAN, Combo.Kind.ANCHOR};
    private static final Map<LivingEntity, Combo.Track> TRACKS = new WeakHashMap<>();
    private static final double PREFER_BONUS = 6.0;
    private static LivingEntity focus;
    private static LivingEntity preferred;
    private static int preferredUntil;
    private static LivingEntity flying;
    private static IntFunction<Vec3> flight;
    private static int flightUntil;

    private Combo() {
    }

    public static List<LivingEntity> enemies(Minecraft mc, LocalPlayer player) {
        List<LivingEntity> enemies = new ArrayList<>();

        for (AbstractClientPlayer other : mc.level.players()) {
            if (MeleeAttacks.isEnemy(player, other) && player.distanceToSqr(other) < 100.0) {
                enemies.add(other);
            }
        }

        LivingEntity chosen = player.tickCount <= preferredUntil ? preferred : null;
        enemies.sort(
            Comparator.comparingDouble(
                enemy -> player.distanceTo(enemy)
                    - (enemy == focus ? 2.0 : 0.0)
                    - (enemy == chosen ? 6.0 : 0.0)
                    - (CrystalDamage.health(enemy) <= 10.0F ? 2.0 : 0.0)
            )
        );
        focus = enemies.isEmpty() ? null : enemies.get(0);
        return enemies.size() > 3 ? enemies.subList(0, 3) : enemies;
    }

    public static void prefer(LocalPlayer player, LivingEntity enemy) {
        preferred = enemy;
        preferredUntil = player.tickCount + 2;
    }

    public static void flight(LocalPlayer player, LivingEntity enemy, IntFunction<Vec3> guess) {
        flying = enemy;
        flight = guess;
        flightUntil = player.tickCount + 2;
    }

    public static List<LivingEntity> friends(Minecraft mc, LocalPlayer player) {
        List<LivingEntity> friends = new ArrayList<>();

        for (AbstractClientPlayer other : mc.level.players()) {
            if (other != player && other.isAlive() && MeleeAttacks.isFriend(other) && player.distanceToSqr(other) < 100.0) {
                friends.add(other);
            }
        }

        return friends;
    }

    public static Vec3 lead(LivingEntity entity, int ticks) {
        InterpolationHandler handler = entity.getInterpolation();
        Vec3 server = handler == null ? entity.position() : handler.position();
        Vec3 lag = server.subtract(entity.position());
        int ahead = ticks;
        Vec3 velocity;
        if (entity instanceof LocalPlayer) {
            velocity = entity.position().subtract(entity.xo, entity.yo, entity.zo);
        } else {
            Combo.Track track = TRACKS.computeIfAbsent(entity, ignored -> new Combo.Track());
            track.note(entity, server);
            velocity = track.velocity(entity);
            ahead += Math.min(track.age(entity), entity.getType().updateInterval());
            Vec3 flown = flown(entity, ticks);
            if (flown != null) {
                return flown;
            }
        }

        return ticks > 0 && !(velocity.horizontalDistanceSqr() > 1.0) ? lag.add(travel(entity, server, velocity, ahead)) : lag;
    }

    private static Vec3 flown(LivingEntity entity, int ticks) {
        LocalPlayer self = Minecraft.getInstance().player;
        return ticks > 0 && entity == flying && flight != null && self != null && self.tickCount <= flightUntil ? flight.apply(ticks) : null;
    }

    private static Vec3 travel(LivingEntity entity, Vec3 from, Vec3 velocity, int ticks) {
        AABB box = entity.getBoundingBox().move(from.subtract(entity.position()));
        boolean floating = entity.isFallFlying() || entity.isInLiquid() || entity.onClimbable() || entity.isNoGravity() || entity.isPassenger();
        double x = velocity.x;
        double y = velocity.y;
        double z = velocity.z;
        Vec3 moved = Vec3.ZERO;

        for (int tick = 0; tick < ticks; tick++) {
            if (!floating) {
                y = (y - 0.08) * 0.98;
            }

            Vec3 step = Entity.collideBoundingBox(entity, new Vec3(x, y, z), box, entity.level(), List.of());
            if (step.x != x) {
                x = 0.0;
            }

            if (step.y != y) {
                y = 0.0;
            }

            if (step.z != z) {
                z = 0.0;
            }

            box = box.move(step);
            moved = moved.add(step);
        }

        return moved;
    }

    public static boolean inHole(LivingEntity target) {
        BlockPos feet = target.blockPosition();

        for (Direction side : Plane.HORIZONTAL) {
            BlockState state = target.level().getBlockState(feet.relative(side));
            if (state.getBlock().getExplosionResistance() < 600.0F) {
                return false;
            }
        }

        return true;
    }

    public static boolean kills(float effective, LivingEntity target) {
        return effective >= CrystalDamage.health(target);
    }

    public static float value(float effective, LivingEntity target) {
        float health = CrystalDamage.health(target);
        float rank = effective < health ? 0.0F : (target.isHolding(Items.TOTEM_OF_UNDYING) ? 500.0F : 1000.0F);
        return Math.min(effective, health) + rank;
    }

    public static Combo.Shot pick(List<Combo.Shot> shots) {
        Combo.Shot crystal = best(shots, Combo.Kind.BREAK);
        Combo.Shot anchor = best(shots, Combo.Kind.DETONATE);
        if (crystal == null && anchor == null) {
            for (Combo.Kind kind : SETUP) {
                Combo.Shot shot = best(shots, kind);
                if (shot != null) {
                    return shot;
                }
            }

            return null;
        } else {
            boolean anchorWins = crystal == null || anchor != null && (anchor.value() > crystal.value() + 1.0F || anchor.kills() && !crystal.kills());
            return anchorWins ? anchor : crystal;
        }
    }

    private static Combo.Shot best(List<Combo.Shot> shots, Combo.Kind kind) {
        Combo.Shot best = null;

        for (Combo.Shot shot : shots) {
            if (shot.kind() == kind && (best == null || shot.value() > best.value() || shot.value() == best.value() && shot.readyIn() < best.readyIn())) {
                best = shot;
            }
        }

        return best;
    }

    @Environment(EnvType.CLIENT)
    public enum Kind {
        BREAK,
        CRYSTAL,
        OBSIDIAN,
        ANCHOR,
        CHARGE,
        DETONATE;
    }

    @Environment(EnvType.CLIENT)
    public record Shot(Combo.Kind kind, Vec3 aim, int route, float value, boolean kills, int readyIn, Object plan) {
    }

    @Environment(EnvType.CLIENT)
    private static final class Track {
        private Vec3 last;
        private int lastAt;
        private int sampledAt;
        private Vec3 velocity = Vec3.ZERO;

        void note(LivingEntity entity, Vec3 server) {
            int now = entity.tickCount;
            if (now < this.sampledAt) {
                this.last = null;
            }

            boolean watched = now - this.sampledAt <= 1;
            this.sampledAt = now;
            if (!server.equals(this.last)) {
                int interval = entity.getType().updateInterval();
                int span = watched ? interval : Math.max(interval, now - this.lastAt);
                this.velocity = this.last == null ? Vec3.ZERO : server.subtract(this.last).scale(1.0 / span);
                this.last = server;
                this.lastAt = now;
            }
        }

        int age(LivingEntity entity) {
            return entity.tickCount - this.lastAt;
        }

        Vec3 velocity(LivingEntity entity) {
            return this.age(entity) > entity.getType().updateInterval() + 1 ? Vec3.ZERO : this.velocity;
        }
    }
}
