package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.entity.player.PlayerEntity;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "attack", at = @At("HEAD"))
    private void bytephase$onAttack(net.minecraft.entity.Entity target, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (BytePhase.modules != null) {
            BytePhase.modules.attack((PlayerEntity) (Object) this, target);
        }
    }

}
