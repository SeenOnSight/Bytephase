package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.util.CameraAim;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mouse handling runs once per rendered frame, after the player's own look
 * input has been applied. Steering the aim here (rather than in the 20 TPS tick)
 * keeps the assisted camera motion as smooth as the mouse itself.
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void bytephase$cameraAim(CallbackInfo ci) {
        CameraAim.frame(BytePhase.mc == null ? null : BytePhase.mc.player);
    }
}
