package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.movement.EntityControl;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractHorseEntity.class)
public abstract class AbstractHorseEntityMixin {
    /**
     * Vanilla requires a saddle before returning the first passenger as the
     * controller. Returning the passenger directly keeps the rest of the
     * steering path (yaw, jump charge and movement input) unchanged.
     */
    @Inject(method = "getControllingPassenger", at = @At("RETURN"), cancellable = true)
    private void bytephase$entityControl(CallbackInfoReturnable<LivingEntity> cir) {
        EntityControl module = BytePhase.modules == null ? null : BytePhase.modules.entityControl();
        if (module == null || !module.horses() || BytePhase.mc == null) {
            return;
        }
        Entity self = (Entity) (Object) this;
        if (self.getFirstPassenger() instanceof PlayerEntity player && player == BytePhase.mc.player) {
            cir.setReturnValue(player);
        }
    }
}
