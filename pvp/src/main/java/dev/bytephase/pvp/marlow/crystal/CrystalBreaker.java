package dev.bytephase.pvp.marlow.crystal;

import dev.bytephase.pvp.marlow.crystal.AttackDamage;
import dev.bytephase.pvp.marlow.crystal.KeptCrystals;
import dev.bytephase.pvp.marlow.crystal.SequencedLevel;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

public final class CrystalBreaker {
    private final KeptCrystals keptCrystals;

    public CrystalBreaker(KeptCrystals keptCrystals) {
        this.keptCrystals = keptCrystals;
    }

    public void breakIfPossible(Minecraft client, EndCrystal crystal) {
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) {
            return;
        }
        if (!level.getWorldBorder().isWithinBounds(crystal.blockPosition()) || !AttackDamage.breaksCrystal(player)) {
            return;
        }
        this.keptCrystals.keep(crystal, ((SequencedLevel)level).bytephase$marlow$blockSequence());
    }
}

