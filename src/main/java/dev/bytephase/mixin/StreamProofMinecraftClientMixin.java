package dev.bytephase.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.bytephase.render.capture.StreamProof;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MinecraftClient.class)
public abstract class StreamProofMinecraftClientMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/render/GameRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;Z)V"))
    private void bytephase$privateView(GameRenderer renderer, RenderTickCounter counter,
            boolean tick, Operation<Void> original) {
        StreamProof.render(() -> original.call(renderer, counter, tick));
    }
}
