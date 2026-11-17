package dev.bytephase.engine.mixins;

import net.minecraft.block.BlockState;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.categories.visuals.NoFarmLag;

@Mixin({RedstoneWireBlock.class})
public class MixinRedstoneWireFarmLag {
   @Inject(
      method = {"randomDisplayTick(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/random/Random;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephase$noFarmLagDustParticles(BlockState blockState, World world, BlockPos pos2, Random random, CallbackInfo callbackInfo) {
      if (world.isClient() && NoFarmLag.canRedstoneTorch(blockState)) {
         callbackInfo.cancel();
      }
   }
}
