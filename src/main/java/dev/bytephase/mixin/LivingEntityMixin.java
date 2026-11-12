package dev.bytephase.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.bytephase.BytePhase;
import dev.bytephase.module.misc.SlowSwing;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @ModifyReturnValue(method = "getHandSwingDuration", at = @At("RETURN"))
    private int bytephase$slowSwing(int original) {
        SlowSwing slowSwing = BytePhase.modules == null ? null : BytePhase.modules.slowSwing();
        if (slowSwing == null) {
            return original;
        }
        if (BytePhase.mc == null || BytePhase.mc.player != (Object) this) {
            return original;
        }
        return slowSwing.modify(original);
    }

}
