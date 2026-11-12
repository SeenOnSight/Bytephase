package dev.bytephase.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.bytephase.BytePhase;
import dev.bytephase.module.combat.Reach;
import dev.bytephase.module.player.SpeedMine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.entity.player.PlayerEntity;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "attack", at = @At("HEAD"))
    private void bytephase$onAttack(net.minecraft.entity.Entity target, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (BytePhase.modules != null) {
            BytePhase.modules.attack((PlayerEntity) (Object) this, target);
        }
    }

    @Inject(method = "getBlockInteractionRange", at = @At("RETURN"), cancellable = true)
    private void bytephase$blockRange(CallbackInfoReturnable<Double> cir) {
        Reach reach = BytePhase.modules == null ? null : BytePhase.modules.reach();
        if (reach == null || !bytephase$isLocalPlayer()) {
            return;
        }
        cir.setReturnValue(reach.getBlockRange());
    }

    @Inject(method = "getEntityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void bytephase$entityRange(CallbackInfoReturnable<Double> cir) {
        Reach reach = BytePhase.modules == null ? null : BytePhase.modules.reach();
        if (reach == null || !bytephase$isLocalPlayer()) {
            return;
        }
        cir.setReturnValue(reach.getEntityRange());
    }

    @Inject(method = "getContainerInteractionRange", at = @At("RETURN"), cancellable = true)
    private void bytephase$containerRange(CallbackInfoReturnable<Double> cir) {
        Reach reach = BytePhase.modules == null ? null : BytePhase.modules.reach();
        if (reach == null || !bytephase$isLocalPlayer()) {
            return;
        }
        cir.setReturnValue(reach.getBlockRange());
    }

    @ModifyReturnValue(method = "getBlockBreakingSpeed", at = @At("RETURN"))
    private float bytephase$speedMine(float original) {
        SpeedMine speedMine = BytePhase.modules == null ? null : BytePhase.modules.speedMine();
        if (speedMine == null || !bytephase$isLocalPlayer()) {
            return original;
        }
        return speedMine.modify(original);
    }

    @Unique
    private boolean bytephase$isLocalPlayer() {
        return BytePhase.mc != null && BytePhase.mc.player == (Object) this;
    }
}
