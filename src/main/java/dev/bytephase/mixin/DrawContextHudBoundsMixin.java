package dev.bytephase.mixin;

import dev.bytephase.render.HudLayout;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.util.Identifier;
import net.minecraft.client.texture.TextureSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DrawContext.class)
public abstract class DrawContextHudBoundsMixin {
    @Inject(method = "drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V", at = @At("HEAD"))
    private void bytephase$hudGuiTexture(RenderPipeline pipeline, Identifier texture, int x, int y, int width, int height, CallbackInfo ci) {
        if (HudLayout.recording()) HudLayout.record((DrawContext)(Object)this, x, y, width, height);
    }
    @Inject(method = "drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIII)V", at = @At("HEAD"))
    private void bytephase$hudTintedTexture(RenderPipeline pipeline, Identifier texture, int x, int y, int width, int height, int color, CallbackInfo ci) {
        if (HudLayout.recording()) HudLayout.record((DrawContext)(Object)this, x, y, width, height);
    }
    @Inject(method = "drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIIIII)V", at = @At("HEAD"))
    private void bytephase$hudTexture(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v,
            int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, CallbackInfo ci) {
        if (HudLayout.recording()) HudLayout.record((DrawContext)(Object)this, x, y, width, height);
    }
    @Inject(method = "fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/texture/TextureSetup;IIIIILjava/lang/Integer;)V", at = @At("HEAD"))
    private void bytephase$hudFill(RenderPipeline pipeline, TextureSetup texture, int x1, int y1, int x2, int y2, int color, Integer color2, CallbackInfo ci) {
        if (HudLayout.recording()) HudLayout.record((DrawContext)(Object)this, Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
    }
    @Inject(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)V", at = @At("HEAD"))
    private void bytephase$hudString(TextRenderer renderer, String text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        if (HudLayout.measuring() && text != null) HudLayout.record((DrawContext)(Object)this, x, y, renderer.getWidth(text), renderer.fontHeight + 1);
        else if (text != null && !text.isEmpty()) HudLayout.markDrawn();
    }
    @Inject(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V", at = @At("HEAD"))
    private void bytephase$hudText(TextRenderer renderer, Text text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        if (HudLayout.measuring() && text != null) HudLayout.record((DrawContext)(Object)this, x, y, renderer.getWidth(text), renderer.fontHeight + 1);
        else if (text != null) HudLayout.markDrawn();
    }
    @Inject(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V", at = @At("HEAD"))
    private void bytephase$hudOrdered(TextRenderer renderer, OrderedText text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        if (HudLayout.measuring() && text != null) HudLayout.record((DrawContext)(Object)this, x, y, renderer.getWidth(text), renderer.fontHeight + 1);
        else if (text != null) HudLayout.markDrawn();
    }
    @Inject(method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;III)V", at = @At("HEAD"))
    private void bytephase$hudItem(LivingEntity entity, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        if (HudLayout.recording() && !stack.isEmpty()) HudLayout.record((DrawContext)(Object)this, x, y, 16, 16);
    }
}
