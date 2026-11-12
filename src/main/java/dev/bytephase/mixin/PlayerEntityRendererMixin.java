package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.util.SilentRotation;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns the local player's own model toward the aim modules' rotation.
 *
 * <p>The camera is left alone, so in third person your avatar turns to face the
 * target while the view stays where you pointed it.</p>
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void bytephase$silentRotation(PlayerLikeEntity entity, PlayerEntityRenderState state,
                                          float tickProgress, CallbackInfo ci) {
        if (!SilentRotation.isActive() || BytePhase.mc == null || entity != BytePhase.mc.player) {
            return;
        }
        state.bodyYaw = SilentRotation.yaw();
        state.relativeHeadYaw = 0.0f;
        state.pitch = SilentRotation.pitch();
    }
}
