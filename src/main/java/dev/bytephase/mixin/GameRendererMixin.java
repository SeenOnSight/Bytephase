package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.CustomFov;
import dev.bytephase.module.render.NoRender;
import dev.bytephase.module.render.Zoom;
import dev.bytephase.render.RenderResources;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void bytephase$disposeRenderResources(CallbackInfo ci) {
        RenderResources.dispose();
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void bytephase$zoom(Camera camera, float tickDelta, boolean changingFov,
                                CallbackInfoReturnable<Float> cir) {
        Zoom zoom = BytePhase.modules == null ? null : BytePhase.modules.zoom();
        if (zoom != null) {
            cir.setReturnValue(zoom.apply(cir.getReturnValue()));
            return;
        }
        CustomFov customFov = BytePhase.modules == null ? null : BytePhase.modules.customFov();
        if (customFov != null) {
            cir.setReturnValue(customFov.getFov());
        }
    }

    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
    private void bytephase$noTotemAnimation(ItemStack stack, CallbackInfo ci) {
        NoRender noRender = BytePhase.modules == null ? null : BytePhase.modules.noRender();
        if (noRender != null && noRender.totemAnimation() && stack.isOf(Items.TOTEM_OF_UNDYING)) {
            ci.cancel();
        }
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void bytephase$noHurtCamera(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        NoRender noRender = BytePhase.modules == null ? null : BytePhase.modules.noRender();
        if (noRender != null && noRender.hurtCamera()) {
            ci.cancel();
        }
    }
}
