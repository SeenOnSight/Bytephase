package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.render.ModelFilledLayer;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EndCrystalEntityRenderer.class)
public abstract class EndCrystalEntityRendererMixin {
    @Redirect(method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"))
    private void bytephase$fillCrystal(OrderedRenderCommandQueue queue, Model model, Object state,
                                      MatrixStack matrices, RenderLayer layer, int light, int overlay,
                                      int outline, ModelCommandRenderer.CrumblingOverlayCommand crumbling) {
        var esp = BytePhase.modules == null ? null : BytePhase.modules.playerEsp();
        if (esp != null && esp.fillCrystal((EndCrystalEntityRenderState) state)) {
            queue.submitModel(model, state, matrices, ModelFilledLayer.get(esp.throughWalls()),
                    0xF000F0, overlay, esp.modelFilledTint((EndCrystalEntityRenderState) state), null, outline, crumbling);
        } else {
            queue.submitModel(model, state, matrices, layer, light, overlay, outline, crumbling);
        }
    }
}
