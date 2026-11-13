package dev.bytephase.pvp.marlow.crystal;

import dev.bytephase.pvp.marlow.crystal.KeptCrystal;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

public final class KeptCrystals {
    private static final long HIDDEN_TICKS = 30L;
    private final List<EndCrystal> kept = new ArrayList<EndCrystal>();
    private long lastKeptAt;
    private volatile long ticks;
    private boolean seeingPast;

    private static boolean isHidden(Entity entity, long keptSince) {
        KeptCrystal crystal;
        return entity instanceof KeptCrystal && (crystal = (KeptCrystal)entity).bytephase$marlow$isKept() && crystal.bytephase$marlow$keptAt() - keptSince > 0L;
    }

    public boolean isHidden(Entity entity) {
        return KeptCrystals.isHidden(entity, this.ticks - 30L);
    }

    public void keep(EndCrystal crystal, int sequence) {
        long now = this.ticks;
        this.forgetSettled(now - 30L);
        if (KeptCrystals.isHidden((Entity)crystal, now - 30L)) {
            return;
        }
        ((KeptCrystal)crystal).bytephase$marlow$keep(now, sequence);
        this.kept.add(crystal);
        this.lastKeptAt = now;
    }

    public void ticked() {
        ++this.ticks;
    }

    public void clear() {
        for (EndCrystal crystal : kept) ((KeptCrystal) crystal).bytephase$marlow$release();
        kept.clear();
        lastKeptAt = 0;
        seeingPast = false;
    }

    public void acknowledged(int sequence) {
        this.forgetSettled(this.ticks - 30L);
        this.kept.removeIf(crystal -> {
            KeptCrystal keptCrystal = (KeptCrystal)crystal;
            if (keptCrystal.bytephase$marlow$sequence() >= sequence) {
                return false;
            }
            keptCrystal.bytephase$marlow$release();
            return true;
        });
    }

    public <T> T seePast(Supplier<T> lookup) {
        boolean previous = this.seeingPast;
        this.seeingPast = true;
        try {
            T t = lookup.get();
            return t;
        }
        finally {
            this.seeingPast = previous;
        }
    }

    public Predicate<? super Entity> hide(Predicate<? super Entity> predicate) {
        long keptSince = this.ticks - 30L;
        if (!this.seeingPast || this.nothingKept(keptSince)) {
            return predicate;
        }
        return entity -> !KeptCrystals.isHidden(entity, keptSince) && predicate.test((Entity)entity);
    }

    public List<Entity> withoutHidden(List<Entity> entities) {
        long keptSince = this.ticks - 30L;
        for (Entity entity : entities) {
            if (!KeptCrystals.isHidden(entity, keptSince)) continue;
            ArrayList<Entity> shown = new ArrayList<Entity>(entities);
            shown.removeIf(other -> KeptCrystals.isHidden(other, keptSince));
            return shown;
        }
        return entities;
    }

    public Iterable<Entity> hide(Iterable<Entity> entities) {
        long keptSince = this.ticks - 30L;
        if (this.nothingKept(keptSince)) {
            return entities;
        }
        return Iterables.filter(entities, entity -> !KeptCrystals.isHidden(entity, keptSince));
    }

    private void forgetSettled(long keptSince) {
        this.kept.removeIf(crystal -> crystal.isRemoved() || !KeptCrystals.isHidden((Entity)crystal, keptSince));
    }

    private boolean nothingKept(long keptSince) {
        if (this.kept.isEmpty()) {
            return true;
        }
        if (this.lastKeptAt - keptSince <= 0L) {
            this.kept.clear();
            return true;
        }
        return false;
    }
}

