package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.NoRender;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Inject(method = "addBlockBreakParticles", at = @At("HEAD"), cancellable = true)
    private void bytephase$noBreakParticles(BlockPos pos, BlockState state, CallbackInfo ci) {
        NoRender noRender = BytePhase.modules == null ? null : BytePhase.modules.noRender();
        if (noRender != null && noRender.blockBreakParticles()) {
            ci.cancel();
        }
    }

    @Inject(method = "spawnBlockBreakingParticle", at = @At("HEAD"), cancellable = true)
    private void bytephase$noBreakProgressParticles(BlockPos pos, Direction direction, CallbackInfo ci) {
        NoRender noRender = BytePhase.modules == null ? null : BytePhase.modules.noRender();
        if (noRender != null && noRender.blockBreakParticles()) {
            ci.cancel();
        }
    }
}
