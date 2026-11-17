package dev.bytephase.engine.mixins;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.bytephase.engine.categories.visuals.NoFarmLag;

@Mixin({Block.class})
public class MixinBlockFarmLag {
   @Inject(
      method = {"shouldDrawSide(Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Direction;)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void bytephase$noFarmLagUncull(BlockState currentState, BlockState neighborState, Direction direction, CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
      if (NoFarmLag.canRedstoneTorch(neighborState)) {
         callbackInfoReturnable.setReturnValue(true);
      }
   }
}
