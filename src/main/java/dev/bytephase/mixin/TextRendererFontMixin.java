package dev.bytephase.mixin;

import dev.bytephase.render.ClientFonts;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextRenderer.class)
public abstract class TextRendererFontMixin {
    @Inject(method = "getWidth(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void bytephase$stringWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (ClientFonts.active() && text != null) {
            cir.setReturnValue(((TextRenderer) (Object) this).getWidth(ClientFonts.text(text)));
        }
    }

    @ModifyVariable(method = "getWidth(Lnet/minecraft/text/StringVisitable;)I", at = @At("HEAD"), argsOnly = true)
    private StringVisitable bytephase$textWidth(StringVisitable text) { return ClientFonts.visitable(text); }

    @ModifyVariable(method = "getWidth(Lnet/minecraft/text/OrderedText;)I", at = @At("HEAD"), argsOnly = true)
    private OrderedText bytephase$orderedWidth(OrderedText text) { return ClientFonts.ordered(text); }

    @Inject(method = "trimToWidth(Ljava/lang/String;I)Ljava/lang/String;",
        at = @At("HEAD"), cancellable = true)
    private void bytephase$trim(String text, int width, CallbackInfoReturnable<String> cir) {
        if (ClientFonts.active()) {
            cir.setReturnValue(((TextRenderer) (Object) this).getTextHandler()
                .trimToWidth(text, width, ClientFonts.style()));
        }
    }

    @ModifyVariable(method = "wrapLines(Lnet/minecraft/text/StringVisitable;I)Ljava/util/List;",
        at = @At("HEAD"), argsOnly = true)
    private StringVisitable bytephase$wrap(StringVisitable text) { return ClientFonts.visitable(text); }
}
