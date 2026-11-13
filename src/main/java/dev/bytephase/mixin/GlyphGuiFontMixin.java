package dev.bytephase.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.bytephase.render.ClientFonts;
import net.minecraft.client.font.TextDrawable;
import net.minecraft.client.gui.render.state.GlyphGuiElementRenderState;
import net.minecraft.client.texture.TextureSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** GUI text is submitted later, so choose sampling from the glyph's own atlas. */
@Mixin(GlyphGuiElementRenderState.class)
public abstract class GlyphGuiFontMixin {
    @Shadow @Final private TextDrawable renderable;

    @Inject(method = "textureSetup", at = @At("HEAD"), cancellable = true)
    private void bytephase$smoothGlyphs(CallbackInfoReturnable<TextureSetup> cir) {
        var view = renderable.textureView();
        if (ClientFonts.smoothAtlas(view.texture().getLabel())) {
            cir.setReturnValue(TextureSetup.withLightmap(view,
                RenderSystem.getSamplerCache().get(FilterMode.LINEAR)));
        }
    }
}
