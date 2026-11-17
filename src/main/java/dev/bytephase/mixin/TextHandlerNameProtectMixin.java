package dev.bytephase.mixin;

import dev.bytephase.module.misc.NameProtect;
import net.minecraft.client.font.TextHandler;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Mask before measuring, clipping or splitting a username into separate lines. */
@Mixin(TextHandler.class)
public abstract class TextHandlerNameProtectMixin {
    @ModifyVariable(method = {
            "getWidth(Ljava/lang/String;)F",
            "trimToWidth(Ljava/lang/String;ILnet/minecraft/text/Style;)Ljava/lang/String;",
            "trimToWidthBackwards(Ljava/lang/String;ILnet/minecraft/text/Style;)Ljava/lang/String;",
            "wrapLines(Ljava/lang/String;ILnet/minecraft/text/Style;)Ljava/util/List;",
            "wrapLines(Ljava/lang/String;ILnet/minecraft/text/Style;ZLnet/minecraft/client/font/TextHandler$LineWrappingConsumer;)V"
    }, at = @At("HEAD"), argsOnly = true)
    private String bytephase$protectString(String text) { return NameProtect.filter(text); }

    @ModifyVariable(method = {
            "getWidth(Lnet/minecraft/text/StringVisitable;)F",
            "trimToWidth(Lnet/minecraft/text/StringVisitable;ILnet/minecraft/text/Style;)Lnet/minecraft/text/StringVisitable;",
            "wrapLines(Lnet/minecraft/text/StringVisitable;ILnet/minecraft/text/Style;)Ljava/util/List;",
            "wrapLines(Lnet/minecraft/text/StringVisitable;ILnet/minecraft/text/Style;Ljava/util/function/BiConsumer;)V"
    }, at = @At("HEAD"), argsOnly = true)
    private StringVisitable bytephase$protectStyledText(StringVisitable text) { return NameProtect.filter(text); }

    @ModifyVariable(method = "getWidth(Lnet/minecraft/text/OrderedText;)F",
            at = @At("HEAD"), argsOnly = true)
    private OrderedText bytephase$protectOrderedText(OrderedText text) { return NameProtect.filter(text); }
}
