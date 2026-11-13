package dev.bytephase.mixin;

import dev.bytephase.render.capture.StreamProof;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps client-only module messages and coordinates out of the capture frame. */
@Mixin(ChatHud.class)
public abstract class StreamProofChatHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void bytephase$privateChat(CallbackInfo ci) {
        if (StreamProof.isCleanFrame()) ci.cancel();
    }
}
