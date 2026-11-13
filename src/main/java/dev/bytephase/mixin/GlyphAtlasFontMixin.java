package dev.bytephase.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.bytephase.render.ClientFonts;
import java.util.function.Supplier;
import net.minecraft.client.font.GlyphAtlasTexture;
import net.minecraft.client.font.TextRenderLayerSet;
import net.minecraft.client.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlyphAtlasTexture.class)
public abstract class GlyphAtlasFontMixin extends AbstractTexture {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void bytephase$smoothAtlas(Supplier<String> label, TextRenderLayerSet layers,
            boolean hasColor, CallbackInfo ci) {
        if (ClientFonts.smoothAtlas(label.get())) {
            sampler = RenderSystem.getSamplerCache().getRepeated(FilterMode.LINEAR);
        }
    }
}
