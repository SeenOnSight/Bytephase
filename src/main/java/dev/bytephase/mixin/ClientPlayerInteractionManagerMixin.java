package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.util.AttackLimiter;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void bytephase$beforeAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        AttackLimiter.markAttack();
        if (BytePhase.modules != null) {
            BytePhase.modules.beforeAttack(player, target);
        }
    }
}
