package dev.bytephase.mixin;

import dev.bytephase.render.FilledItemContext;
import dev.bytephase.render.ModelFilledLayer;
import java.util.List;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BatchingRenderCommandQueue.class)
public abstract class ItemLayerRenderStateMixin {
    @Inject(method = "submitItem", at = @At("HEAD"), cancellable = true)
    private void bytephase$fillItem(MatrixStack matrices, ItemDisplayContext display, int light, int overlay, int outline,
                                    int[] tints, List<BakedQuad> quads, RenderLayer layer, ItemRenderState.Glint glint,
                                    CallbackInfo ci) {
        var tint = FilledItemContext.current();
        if (tint == null || quads.isEmpty()) {
            return;
        }
        // The atlas alpha preserves transparent pixels (e.g. a sword silhouette).
        // Group by atlas without relying on item model layer internals.
        var groups = new java.util.LinkedHashMap<net.minecraft.util.Identifier, java.util.List<BakedQuad>>();
        for (BakedQuad quad : quads) {
            groups.computeIfAbsent(quad.sprite().getAtlasId(), ignored -> new java.util.ArrayList<>()).add(quad);
        }
        var queue = (BatchingRenderCommandQueue) (Object) this;
        groups.forEach((atlas, group) -> queue.submitCustom(matrices, ModelFilledLayer.item(atlas, tint.throughWalls()),
                (entry, vertices) -> {
                    for (BakedQuad quad : group) {
                        var normal = quad.face().getFloatVector();
                        for (int i = 0; i < 4; i++) {
                            var pos = quad.getPosition(i);
                            long uv = quad.getTexcoords(i);
                            vertices.vertex(entry, pos.x(), pos.y(), pos.z()).color(tint.color())
                                    .texture(Vector2f.getX(uv), Vector2f.getY(uv)).overlay(overlay)
                                    .light(0xF000F0).normal(entry, normal.x(), normal.y(), normal.z());
                        }
                    }
                }));
        ci.cancel();
    }
}
