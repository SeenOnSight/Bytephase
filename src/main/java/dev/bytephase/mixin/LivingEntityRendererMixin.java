package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.PlayerEsp;
import dev.bytephase.render.ModelWireframeLayer;
import dev.bytephase.render.ModelFilledLayer;
import net.minecraft.client.model.Model;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds a tinted, translucent model surface to Player ESP's Model Chams style. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Unique
    private int bytephase$pendingModelTint = 0;

    @Unique
    private boolean bytephase$pendingHologram = false;

    @Unique
    private boolean bytephase$pendingFilled = false;

    @Unique
    private Identifier bytephase$pendingHologramTexture;

    @Unique
    private boolean bytephase$pendingHologramThroughWalls;

    @Inject(method = "getRenderLayer", at = @At("RETURN"), cancellable = true)
    private void bytephase$chamsLayer(LivingEntityRenderState state, boolean showBody,
                                      boolean translucent, boolean showOutline,
                                      CallbackInfoReturnable<RenderLayer> cir) {
        PlayerEsp esp = BytePhase.modules == null ? null : BytePhase.modules.playerEsp();
        bytephase$pendingFilled = false;
        if (esp != null && esp.isModelFilledStyle() && esp.shouldModelChams(state)) {
            bytephase$pendingFilled = true;
            bytephase$pendingModelTint = esp.modelFilledTint(state);
            bytephase$pendingHologram = false;
            cir.setReturnValue(ModelFilledLayer.get(esp.throughWalls()));
            return;
        }
        if (esp != null && esp.isModelHologramStyle() && esp.shouldModelChams(state)) {
            Identifier texture = ((LivingEntityRenderer) (Object) this).getTexture(state);
            bytephase$pendingModelTint = esp.modelHologramFillTint(state);
            bytephase$pendingHologram = true;
            bytephase$pendingHologramTexture = texture;
            bytephase$pendingHologramThroughWalls = esp.throughWalls();
            cir.setReturnValue(esp.throughWalls() ? ModelWireframeLayer.throughWallSurface(texture)
                    : RenderLayers.entityTranslucent(texture, true));
            return;
        }
        if (esp != null && esp.isModelWireframeStyle() && esp.shouldModelChams(state)) {
            Identifier texture = ((LivingEntityRenderer) (Object) this).getTexture(state);
            bytephase$pendingModelTint = esp.modelWireframeTint(state);
            bytephase$pendingHologram = false;
            cir.setReturnValue(ModelWireframeLayer.get(texture, esp.throughWalls()));
            return;
        }
        if (esp == null || !esp.shouldModelChams(state)) {
            bytephase$pendingModelTint = 0;
            bytephase$pendingHologram = false;
            return;
        }

        Identifier texture = ((LivingEntityRenderer) (Object) this).getTexture(state);
        bytephase$pendingModelTint = esp.modelChamsTint(state);
        bytephase$pendingHologram = false;
        cir.setReturnValue(esp.throughWalls() ? ModelWireframeLayer.throughWallSurface(texture)
                : RenderLayers.entityTranslucent(texture, true));
    }

    @org.spongepowered.asm.mixin.injection.Redirect(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"))
    private void bytephase$submitHologramModel(
            net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
            Model model, Object state, MatrixStack matrices, RenderLayer layer,
            int light, int overlay, int color, Sprite sprite, int outlineColor,
            ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay) {
        boolean hologram = bytephase$pendingHologram;
        bytephase$pendingHologram = false;
        int tint = bytephase$pendingModelTint;
        bytephase$pendingModelTint = 0;
        int resolvedColor = tint == 0 ? color : tint;
        int resolvedLight = bytephase$pendingFilled ? 0xF000F0 : light;
        bytephase$pendingFilled = false;
        queue.submitModel(model, state, matrices, layer, resolvedLight, overlay, resolvedColor, sprite, outlineColor, crumblingOverlay);
        if (hologram) {
            queue.submitModel(model, state, matrices, ModelWireframeLayer.get(
                    bytephase$pendingHologramTexture, bytephase$pendingHologramThroughWalls), light, overlay,
                    0xF0000000 | (resolvedColor & 0xFFFFFF), sprite, outlineColor, crumblingOverlay);
        }
    }
}
