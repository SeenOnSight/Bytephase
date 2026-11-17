package dev.bytephase.pvp.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface AccessorBlockSelectionGameRenderer {
    @Accessor("fogRenderer")
    FogRenderer pvp$blockSelectionFogRenderer();

    @Invoker("shouldRenderBlockOutline")
    boolean pvp$shouldRenderBlockOutline();
}
