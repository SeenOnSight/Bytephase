package dev.bytephase.pvp.mixin;

import dev.bytephase.pvp.module.ModuleRegistry;
import dev.bytephase.pvp.module.impl.combat.WTapRecodeModule;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {
    @Inject(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;ensureHasSentCarriedItem()V"))
    private void pvp$beforeAttack(Player player, Entity entity, CallbackInfo ci) {
        WTapRecodeModule wTap = ModuleRegistry.get(WTapRecodeModule.class);
        if (wTap != null && wTap.isEnabled()) {
            wTap.beforeAttack(player, entity);
        }
    }

    @Inject(method = "attack", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V",
        shift = At.Shift.AFTER))
    private void pvp$noteAttackPacket(Player player, Entity entity, CallbackInfo ci) {
        if (player == Minecraft.getInstance().player) {
            MeleeAttacks.noteAttack(player);
        }
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void pvp$afterAttack(Player player, Entity entity, CallbackInfo ci) {
        WTapRecodeModule wTap = ModuleRegistry.get(WTapRecodeModule.class);
        if (wTap != null && wTap.isEnabled()) {
            wTap.afterAttack();
        }
    }

    @Inject(method = "piercingAttack", at = @At("RETURN"))
    private void pvp$afterStab(PiercingWeapon weapon, CallbackInfo ci) {
        MeleeAttacks.noteAttack(Minecraft.getInstance().player);
        WTapRecodeModule wTap = ModuleRegistry.get(WTapRecodeModule.class);
        if (wTap != null && wTap.isEnabled()) {
            wTap.afterStab();
        }
    }

    @Inject(method = "useItem", at = @At("RETURN"))
    private void pvp$afterUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (player == Minecraft.getInstance().player) {
            MeleeAttacks.noteAction(player);
        }
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void pvp$afterUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (player == Minecraft.getInstance().player) {
            MeleeAttacks.noteAction(player);
        }
    }

    @Inject(method = "interact", at = @At("RETURN"))
    private void pvp$afterInteract(Player player, Entity entity, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (player == Minecraft.getInstance().player) {
            MeleeAttacks.noteAction(player);
        }
    }

    @Inject(method = "interactAt", at = @At("RETURN"))
    private void pvp$afterInteractAt(Player player, Entity entity, EntityHitResult hit, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (player == Minecraft.getInstance().player) {
            MeleeAttacks.noteAction(player);
        }
    }

    @Inject(method = "releaseUsingItem", at = @At("RETURN"))
    private void pvp$afterRelease(Player player, CallbackInfo ci) {
        if (player == Minecraft.getInstance().player) {
            MeleeAttacks.noteAction(player);
        }
    }
}
