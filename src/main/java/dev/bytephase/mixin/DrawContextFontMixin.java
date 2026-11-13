package dev.bytephase.mixin;

import dev.bytephase.render.ClientFonts;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DrawContext.class)
public abstract class DrawContextFontMixin {
    @Inject(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)V",
        at = @At("HEAD"), cancellable = true)
    private void bytephase$stringFont(TextRenderer renderer, String text, int x, int y,
            int color, boolean shadow, CallbackInfo ci) {
        if (ClientFonts.active() && text != null) {
            ((DrawContext) (Object) this).drawText(renderer, ClientFonts.text(text), x, y, color, shadow);
            ci.cancel();
        }
    }

    @ModifyVariable(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V",
        at = @At("HEAD"), argsOnly = true)
    private Text bytephase$textFont(Text text) { return ClientFonts.text(text); }

    @ModifyVariable(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V",
        at = @At("HEAD"), argsOnly = true)
    private OrderedText bytephase$orderedFont(OrderedText text) { return ClientFonts.ordered(text); }
}
