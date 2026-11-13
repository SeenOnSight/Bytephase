package dev.bytephase.pvp.mixin;

import dev.bytephase.pvp.marlow.MarlowCrystal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={MultiPlayerGameMode.class})
public class MarlowMultiPlayerGameModeMixin {
    @Inject(method={"attack"}, at={@At(value="INVOKE", target="Lnet/minecraft/world/entity/player/Player;attack(Lnet/minecraft/world/entity/Entity;)V", shift=At.Shift.AFTER)})
    private void bytephase$marlow$afterAttack(Player player, Entity entity, CallbackInfo ci) {
        if (!(entity instanceof EndCrystal)) {
            return;
        }
        EndCrystal crystal = (EndCrystal)entity;
        MarlowCrystal mod = MarlowCrystal.get();
        if (!mod.serverSession().isOptedOut()) {
            mod.crystalBreaker().breakIfPossible(Minecraft.getInstance(), crystal);
        }
    }
}

