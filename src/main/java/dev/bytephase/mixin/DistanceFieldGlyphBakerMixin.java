package dev.bytephase.mixin;

import dev.bytephase.render.font.DistanceFieldGlyph;
import dev.bytephase.render.font.DistanceFieldLayers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.font.BakedGlyphImpl;
import net.minecraft.client.font.GlyphAtlasTexture;
import net.minecraft.client.font.GlyphBaker;
import net.minecraft.client.font.GlyphMetrics;
import net.minecraft.client.font.UploadableGlyph;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Distance fields and ordinary fallback bitmaps have separate atlas storage. */
@Mixin(GlyphBaker.class)
public abstract class DistanceFieldGlyphBakerMixin {
    @Shadow @Final private TextureManager textureManager;
    @Shadow @Final private Identifier fontId;
    @Unique private final List<GlyphAtlasTexture> bytephase$distanceAtlases = new ArrayList<>();

    @Unique private Identifier bytephase$atlasId(int index) {
        return fontId.withSuffixedPath("/sdf/" + index);
    }

    @Inject(method = "bake", at = @At("HEAD"), cancellable = true)
    private void bytephase$bakeDistanceField(GlyphMetrics metrics, UploadableGlyph glyph,
            CallbackInfoReturnable<BakedGlyphImpl> cir) {
        if (!(glyph instanceof DistanceFieldGlyph.Bitmap)) return;
        for (GlyphAtlasTexture atlas : bytephase$distanceAtlases) {
            BakedGlyphImpl baked = atlas.bake(metrics, glyph);
            if (baked != null) { cir.setReturnValue(baked); return; }
        }
        Identifier id = bytephase$atlasId(bytephase$distanceAtlases.size());
        GlyphAtlasTexture atlas = new GlyphAtlasTexture(id::toString, DistanceFieldLayers.create(id), false);
        textureManager.registerTexture(id, atlas);
        bytephase$distanceAtlases.add(atlas);
        cir.setReturnValue(atlas.bake(metrics, glyph));
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void bytephase$clearDistanceFields(CallbackInfo ci) {
        for (int i = 0; i < bytephase$distanceAtlases.size(); i++) {
            textureManager.destroyTexture(bytephase$atlasId(i));
        }
        bytephase$distanceAtlases.clear();
    }
}
