package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.NoRender;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    /** Vanilla draws the block texture over the whole screen while the camera is inside a block. */
    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
    private static void bytephase$noBlockOverlay(Sprite sprite, MatrixStack matrices,
                                                VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        NoRender noRender = BytePhase.modules == null ? null : BytePhase.modules.noRender();
        if (noRender != null && noRender.blockOverlay()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void bytephase$noFireOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                Sprite sprite, CallbackInfo ci) {
        NoRender noRender = BytePhase.modules == null ? null : BytePhase.modules.noRender();
        if (noRender != null && noRender.fireOverlay()) {
            ci.cancel();
        }
    }
}
