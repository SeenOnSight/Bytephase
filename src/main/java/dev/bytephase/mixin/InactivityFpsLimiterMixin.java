package dev.bytephase.mixin;

import dev.bytephase.module.misc.UnfocusedFps;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InactivityFpsLimiter.class)
public abstract class InactivityFpsLimiterMixin {
    @Inject(method = "update", at = @At("RETURN"), cancellable = true)
    private void bytephase$unfocusedFps(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(dev.bytephase.module.render.Performance.cap(UnfocusedFps.cap(cir.getReturnValue())));
    }
}
