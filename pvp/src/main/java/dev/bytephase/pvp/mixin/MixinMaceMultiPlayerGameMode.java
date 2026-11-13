package dev.bytephase.pvp.mixin;

import dev.bytephase.pvp.module.ModuleRegistry;
import dev.bytephase.pvp.module.impl.combat.MaceModule;
import dev.bytephase.pvp.module.impl.combat.ShieldBreakerModule;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMaceMultiPlayerGameMode {
    @Inject(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;ensureHasSentCarriedItem()V"))
    private void pvp$weaponBeforeAttack(Player player, Entity target, CallbackInfo ci) {
        LocalPlayer self = Minecraft.getInstance().player;
        if (player == self && !MeleeAttacks.actedThisTick(self)) {
            ShieldBreakerModule shieldBreaker = ModuleRegistry.get(ShieldBreakerModule.class);
            if (shieldBreaker != null && shieldBreaker.isEnabled()) {
                shieldBreaker.beforeAttack(self, target);
            }

            MaceModule mace = ModuleRegistry.get(MaceModule.class);
            if (mace != null && mace.isEnabled()) {
                mace.beforeAttack(self, target);
            }
        }
    }
}
