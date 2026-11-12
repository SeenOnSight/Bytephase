package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.render.FilledItemContext;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Redirect(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/ItemRenderState;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;III)V"))
    private void bytephase$renderOwnItem(ItemRenderState item, MatrixStack matrices, OrderedRenderCommandQueue queue,
                                         int light, int overlay, int outline, LivingEntity entity, ItemStack stack,
                                         ItemDisplayContext display, MatrixStack originalMatrices,
                                         OrderedRenderCommandQueue originalQueue, int originalLight) {
        var esp = BytePhase.modules == null ? null : BytePhase.modules.playerEsp();
        boolean fill = esp != null && esp.fillOwnHeldItems() && BytePhase.mc != null && entity == BytePhase.mc.player;
        EntityRenderState state = new EntityRenderState();
        state.entityType = EntityType.PLAYER;
        var tint = fill ? new FilledItemContext.Tint(esp.modelFilledTint(state), esp.throughWalls()) : null;
        FilledItemContext.render(tint, () -> item.render(matrices, queue, light, overlay, outline));
    }
}
