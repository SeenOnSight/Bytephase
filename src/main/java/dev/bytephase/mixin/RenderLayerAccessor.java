package dev.bytephase.mixin;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the package-private {@code RenderLayer.of} factory for custom layers. */
@Mixin(RenderLayer.class)
public interface RenderLayerAccessor {
    @Invoker("of")
    static RenderLayer bytephase$of(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
