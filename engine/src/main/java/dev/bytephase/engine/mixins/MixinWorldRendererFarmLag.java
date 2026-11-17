package dev.bytephase.engine.mixins;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.categories.visuals.NoFarmLag;

@Mixin({WorldRenderer.class})
public class MixinWorldRendererFarmLag {
   @Inject(
      method = {"updateBlock(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;I)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephase$noFarmLagSkipRebuild(BlockView blockView, BlockPos pos2, BlockState state, BlockState blockState, int bytephasenoFarmValue, CallbackInfo callbackInfo) {
      if (state != blockState && state.getBlock() == blockState.getBlock() && NoFarmLag.canRedstoneTorch(blockState)) {
         callbackInfo.cancel();
      }
   }
}
