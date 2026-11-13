package dev.bytephase.mixin;

import dev.bytephase.render.ClientFonts;
import dev.bytephase.render.font.DistanceFieldSource;
import net.minecraft.client.font.Font;
import net.minecraft.client.font.TrueTypeFontLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TrueTypeFontLoader.class)
public abstract class DistanceFieldFontLoaderMixin {
    @Shadow @Final private Identifier location;

    @Inject(method = "load", at = @At("RETURN"))
    private void bytephase$distanceFieldProvider(ResourceManager resources, CallbackInfoReturnable<Font> cir) {
        if (ClientFonts.bundledFont(location) && cir.getReturnValue() instanceof DistanceFieldSource source) {
            source.bytephase$enableDistanceFields();
        }
    }
}
