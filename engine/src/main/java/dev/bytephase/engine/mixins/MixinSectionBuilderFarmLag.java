package dev.bytephase.engine.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.chunk.SectionBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.bytephase.engine.categories.visuals.NoFarmLag;

@Mixin({SectionBuilder.class})
public class MixinSectionBuilderFarmLag {
   @WrapOperation(
      method = {"build(Lnet/minecraft/util/math/ChunkSectionPos;Lnet/minecraft/client/render/chunk/ChunkRendererRegion;Lcom/mojang/blaze3d/systems/VertexSorter;Lnet/minecraft/client/render/chunk/BlockBufferAllocatorStorage;)Lnet/minecraft/client/render/chunk/SectionBuilder$RenderData;"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/block/BlockState;getRenderType()Lnet/minecraft/block/BlockRenderType;"
      )}
   )
   private BlockRenderType bytephase$noFarmLagRedstone(BlockState blockState, Operation<BlockRenderType> operation) {
      return NoFarmLag.canRedstoneTorch(blockState) ? BlockRenderType.INVISIBLE : (BlockRenderType)operation.call(new Object[]{blockState});
   }
}
