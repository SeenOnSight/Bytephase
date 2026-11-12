package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.HideArmor;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void bytephase$hideArmor(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                     BipedEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        var module = BytePhase.modules == null ? null : BytePhase.modules.get(HideArmor.class);
        if (state instanceof PlayerEntityRenderState && module != null && module.isEnabled()) ci.cancel();
    }
}
