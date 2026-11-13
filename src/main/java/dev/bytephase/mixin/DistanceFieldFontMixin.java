package dev.bytephase.mixin;

import dev.bytephase.render.font.DistanceFieldGlyph;
import dev.bytephase.render.font.DistanceFieldSource;
import net.minecraft.client.font.Glyph;
import net.minecraft.client.font.EmptyGlyph;
import net.minecraft.client.font.TrueTypeFont;
import org.lwjgl.util.freetype.FT_Face;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TrueTypeFont.class)
public abstract class DistanceFieldFontMixin implements DistanceFieldSource {
    @Shadow @Final float oversample;
    @Shadow abstract FT_Face getInfo();
    @Unique private volatile boolean bytephase$distanceFields;

    @Override public void bytephase$enableDistanceFields() { bytephase$distanceFields = true; }

    @Inject(method = "loadGlyph", at = @At("RETURN"), cancellable = true)
    private void bytephase$distanceFieldGlyph(int codePoint, FT_Face face, int glyphIndex,
            CallbackInfoReturnable<Glyph> cir) {
        Glyph original = cir.getReturnValue();
        if (bytephase$distanceFields && original != null && !(original instanceof EmptyGlyph)) {
            cir.setReturnValue(new DistanceFieldGlyph(original.getMetrics(), this::getInfo, glyphIndex, oversample));
        }
    }
}
