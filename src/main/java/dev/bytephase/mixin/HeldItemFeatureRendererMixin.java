package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.render.FilledItemContext;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
    @Redirect(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/ItemRenderState;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;III)V"))
    private void bytephase$renderHeldItem(ItemRenderState item, MatrixStack matrices, OrderedRenderCommandQueue queue,
                                          int light, int overlay, int outline, ArmedEntityRenderState state,
                                          ItemRenderState unused, ItemStack stack, Arm arm, MatrixStack originalMatrices,
                                          OrderedRenderCommandQueue originalQueue, int originalLight) {
        var esp = BytePhase.modules == null ? null : BytePhase.modules.playerEsp();
        boolean own = BytePhase.mc != null && BytePhase.mc.player != null
                && state instanceof PlayerEntityRenderState player && player.id == BytePhase.mc.player.getId();
        boolean fill = esp != null && (own ? esp.fillOwnHeldItems() : esp.fillHeldItems(state));
        var tint = fill ? new FilledItemContext.Tint(esp.modelFilledTint(state), esp.throughWalls()) : null;
        FilledItemContext.render(tint, () -> item.render(matrices, queue, light, overlay, outline));
    }
}
