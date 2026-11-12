package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.ViewClip;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "clipToSpace", at = @At("RETURN"), cancellable = true)
    private void bytephase$viewClip(float desired, CallbackInfoReturnable<Float> cir) {
        ViewClip viewClip = BytePhase.modules == null ? null : BytePhase.modules.viewClip();
        if (viewClip == null || !viewClip.isEnabled()) {
            return;
        }
        float resolved = viewClip.resolve(desired, cir.getReturnValueF());
        if (resolved != cir.getReturnValueF()) {
            cir.setReturnValue(resolved);
        }
    }
}
