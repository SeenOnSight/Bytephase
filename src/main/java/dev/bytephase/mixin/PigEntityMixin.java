package dev.bytephase.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.bytephase.BytePhase;
import dev.bytephase.module.movement.EntityControl;
import net.minecraft.entity.passive.PigEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PigEntity.class)
public abstract class PigEntityMixin {
    /** Pigs only accept steering while holding a carrot on a stick. */
    @ModifyExpressionValue(
            method = "getControllingPassenger",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;isHolding(Lnet/minecraft/item/Item;)Z"
            )
    )
    private boolean bytephase$entityControl(boolean original) {
        EntityControl module = BytePhase.modules == null ? null : BytePhase.modules.entityControl();
        return module != null && module.pigs() ? true : original;
    }
}
