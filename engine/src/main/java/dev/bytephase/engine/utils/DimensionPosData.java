package dev.bytephase.engine.utils;

import net.minecraft.util.math.BlockPos;
import dev.bytephase.engine.utils.ChestTrappedType;

public record DimensionPosData(long pos, String dimension, ChestTrappedType type) {
   public BlockPos getLong() {
      return BlockPos.fromLong(this.pos);
   }

   public static DimensionPosData of(BlockPos pos, String dimension, ChestTrappedType type) {
      return new DimensionPosData(pos.asLong(), dimension, type);
   }
}
