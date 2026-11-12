package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.movement.NoSlow;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlimeBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SlimeBlock.class)
public abstract class SlimeBlockMixin {
    @Inject(method = "onSteppedOn", at = @At("HEAD"), cancellable = true)
    private void bytephase$noSlow(World world, BlockPos pos, BlockState state, Entity entity, CallbackInfo ci) {
        NoSlow noSlow = BytePhase.modules == null ? null : BytePhase.modules.noSlow();
        if (noSlow != null && noSlow.slimeBlock() && BytePhase.mc != null && entity == BytePhase.mc.player) {
            ci.cancel();
        }
    }
}
