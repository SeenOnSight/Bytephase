package dev.bytephase.pvp.mixin;

import dev.bytephase.pvp.module.impl.combat.FriendGuardModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.PiercingWeapon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinFriendGuardMultiPlayerGameMode {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true, order = 900)
    private void pvp$friendGuard(Player player, Entity target, CallbackInfo ci) {
        if (FriendGuardModule.intercepts(player, target)) {
            player.resetAttackStrengthTicker();
            ci.cancel();
        }
    }

    @Inject(method = "piercingAttack", at = @At("HEAD"), cancellable = true, order = 900)
    private void pvp$friendGuardStab(PiercingWeapon weapon, CallbackInfo ci) {
        if (FriendGuardModule.interceptsStab(Minecraft.getInstance().player)) {
            ci.cancel();
        }
    }
}
