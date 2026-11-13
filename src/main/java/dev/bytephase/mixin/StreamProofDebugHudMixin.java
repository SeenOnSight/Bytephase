package dev.bytephase.mixin;

import dev.bytephase.render.capture.StreamProof;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugHud.class)
public abstract class StreamProofDebugHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void bytephase$privateDebug(CallbackInfo ci) {
        if (StreamProof.isCleanFrame()) ci.cancel();
    }
}
