package dev.bytephase.pvp.module.impl.combat.crystal;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class LootGuard {
    private static final double HALF_WIDTH = 8.0;
    private static final double HALF_HEIGHT = 4.0;
    private static final double WATCH_RANGE = 16.0;
    private static final double LOADED_RANGE = 32.0;
    private static final long OTHER_MS = 60000L;
    private static final long OWN_MS = 300000L;
    private static final long EMPTY_GRACE_MS = 3000L;
    private static final List<LootGuard.Spot> SPOTS = new ArrayList<>();
    private static final IntSet SEEN = new IntOpenHashSet();
    private static ResourceKey<Level> dimension;
    private static LocalPlayer lastPlayer;
    private static int lastTick = -1;
    private static boolean selfDead;

    private LootGuard() {
    }

    public static void update(ClientLevel level, LocalPlayer player) {
        if (player != lastPlayer || player.tickCount != lastTick) {
            lastPlayer = player;
            lastTick = player.tickCount;
            dimension = level.dimension();
            long now = Util.getMillis();
            if (player.isDeadOrDying()) {
                if (!selfDead) {
                    SPOTS.add(new LootGuard.Spot(dimension, player.position(), now, now + 300000L));
                }

                selfDead = true;
            } else {
                selfDead = false;
            }

            SEEN.removeIf(id -> level.getEntity(id) == null);

            for (Player other : level.players()) {
                if (other != player && other.isDeadOrDying() && SEEN.add(other.getId()) && other.distanceTo(player) <= 16.0) {
                    SPOTS.add(new LootGuard.Spot(dimension, other.position(), now, now + 60000L));
                }
            }

            SPOTS.removeIf(
                spot -> now > spot.until()
                    || spot.dimension() == dimension
                        && now - spot.since() > 3000L
                        && spot.at().distanceTo(player.position()) <= 32.0
                        && level.getEntitiesOfClass(ItemEntity.class, spot.box()).isEmpty()
            );
        }
    }

    public static boolean near(Vec3 center) {
        for (LootGuard.Spot spot : SPOTS) {
            if (spot.dimension() == dimension && spot.box().contains(center)) {
                return true;
            }
        }

        return false;
    }

    @Environment(EnvType.CLIENT)
    private record Spot(ResourceKey<Level> dimension, Vec3 at, long since, long until) {
        AABB box() {
            return new AABB(this.at.x - 8.0, this.at.y - 4.0, this.at.z - 8.0, this.at.x + 8.0, this.at.y + 4.0, this.at.z + 8.0);
        }
    }
}
