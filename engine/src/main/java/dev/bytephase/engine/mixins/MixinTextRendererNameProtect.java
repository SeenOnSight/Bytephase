package dev.bytephase.engine.mixins;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import dev.bytephase.engine.categories.misc.NameProtect;

@Mixin({TextRenderer.class})
public class MixinTextRendererNameProtect {
   @ModifyVariable(
       method = {"prepare(Ljava/lang/String;FFIZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;"},
      at = @At("HEAD"),
       argsOnly = true
   )
    private String bytephase_maskDrawnString(String text) {
       return NameProtect.getSubstring(text);
    }

    @ModifyVariable(
       method = {
          "prepare(Lnet/minecraft/text/OrderedText;FFIZZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;",
          "drawWithOutline(Lnet/minecraft/text/OrderedText;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"
       },
       at = @At("HEAD"),
       argsOnly = true
    )
    private OrderedText bytephase_maskDrawnOrderedText(OrderedText text) {
       return dev.bytephase.module.misc.NameProtect.filter(text);
   }
}
