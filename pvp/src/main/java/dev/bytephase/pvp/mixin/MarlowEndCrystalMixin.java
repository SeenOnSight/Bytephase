package dev.bytephase.pvp.mixin;

import dev.bytephase.pvp.marlow.crystal.KeptCrystal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={EndCrystal.class})
public class MarlowEndCrystalMixin
implements KeptCrystal {
    @Unique
    private boolean bytephase$marlow$kept;
    @Unique
    private long bytephase$marlow$keptAt;
    @Unique
    private int bytephase$marlow$sequence;

    @Override
    public void bytephase$marlow$keep(long keptAt, int sequence) {
        this.bytephase$marlow$kept = true;
        this.bytephase$marlow$keptAt = keptAt;
        this.bytephase$marlow$sequence = sequence;
    }

    @Override
    public void bytephase$marlow$release() {
        this.bytephase$marlow$kept = false;
    }

    @Override
    public boolean bytephase$marlow$isKept() {
        return this.bytephase$marlow$kept;
    }

    @Override
    public long bytephase$marlow$keptAt() {
        return this.bytephase$marlow$keptAt;
    }

    @Override
    public int bytephase$marlow$sequence() {
        return this.bytephase$marlow$sequence;
    }
}

