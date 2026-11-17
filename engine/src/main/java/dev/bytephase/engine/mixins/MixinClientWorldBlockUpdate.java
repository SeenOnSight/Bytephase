package dev.bytephase.engine.mixins;

import dev.bytephase.engine.render.BlockStateHelper;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.categories.bots.AutoBuilder;
import dev.bytephase.engine.categories.donutsmp.Bloodhound;
import dev.bytephase.engine.categories.donutsmp.ChunkFinder;
import dev.bytephase.engine.categories.donutsmp.ChunkFinderV2;
import dev.bytephase.engine.categories.visuals.HoleESP;
import dev.bytephase.engine.categories.visuals.SpawnerFinder;

@Mixin({ClientWorld.class})
public class MixinClientWorldBlockUpdate {
   @Inject(
      method = {"handleBlockUpdate(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)V"},
      at = {@At("HEAD")}
   )
   private void bytephaseChunkFinderBlockUpdate(BlockPos pos2, BlockState blockState, int bytephaseChunkValue, CallbackInfo callbackInfo) {
      ClientWorld self = (ClientWorld)((net.minecraft.client.world.ClientWorld) (Object) (this));
      BlockState oldState = self.getBlockState(pos2);
      ChunkFinder.onBlockStateChanged(self, pos2, oldState, blockState);
      ChunkFinderV2.onBlockStateChanged(pos2, oldState, blockState);
      Bloodhound.onBlockStateChanged(pos2, oldState, blockState);
      HoleESP.handleScanRender(pos2, oldState, blockState);
      SpawnerFinder.handleInstance(self, pos2, oldState, blockState);
      AutoBuilder.recordBlockStateChange(pos2, oldState, blockState);
      if (oldState != blockState) {
         BlockStateHelper.handleHashOpenForBlockPos(pos2);
      }
   }

   @Inject(
      method = {"processPendingUpdate(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Vec3d;)V"},
      at = {@At("HEAD")}
   )
   private void bytephaseServerBlockAck(BlockPos pos2, BlockState blockState, Vec3d vec, CallbackInfo callbackInfo) {
      AutoBuilder.recordTickedBlockState(pos2, blockState);
   }
}
