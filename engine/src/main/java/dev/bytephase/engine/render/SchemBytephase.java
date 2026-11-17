package dev.bytephase.engine.render;

import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

final class SchemBytephase implements AutoCloseable {
   BufferGpu bytephaseSchemOverlay;
   int visitedChunkCount;
   final int matrixStackValue;
   int bufferCloseValue;
   int[] intArray;
   BufferGpu bytephaseSchemGhostChest;
   BufferGpu bytephaseSchemOutline;
   double distanceSq;
   final long longValue;
   private static final int[] schemBytephaseTable = new int[0];
   long[] longArray;
   int chunkArrayCount;
   final int arrayHashValue;
   int[] hashOpenTable;
   final int schemBytephaseValue;
   boolean fmsRebuildActive;
   byte[] unpackExtraTable;
   private static final byte[] table = new byte[0];
   int fmsRebuildCount;
   boolean chunkArrayActive;
   boolean hashOpenActive;
   final Box box;
   final BufferGpu[] transformsDynamicBufferGpus;
   int schemBytephaseCount;
   static final long[] chunkArrayTable = new long[0];
   int chunkArrayRebuiltCount;
   int arrayHashCount;

   BufferGpu getSchemOverlay() {
      if (this.bytephaseSchemOverlay == null) {
         this.bytephaseSchemOverlay = new BufferGpu("bytephase_schem_overlay");
      }

      return this.bytephaseSchemOverlay;
   }

   double getAmount(double schemBytephaseValue, double schemBytephaseValue2, double schemBytephaseValue3) {
      double schemBytephaseFactor = this.schemBytephaseValue + 8.0 - schemBytephaseValue;
      double schemBytephaseFactor2 = this.matrixStackValue + 8.0 - schemBytephaseValue2;
      double schemBytephaseFactor3 = this.arrayHashValue + 8.0 - schemBytephaseValue3;
      return schemBytephaseFactor * schemBytephaseFactor + schemBytephaseFactor2 * schemBytephaseFactor2 + schemBytephaseFactor3 * schemBytephaseFactor3;
   }

   @Override
   public void close() {
      for (int i = 0; i < this.transformsDynamicBufferGpus.length; i++) {
         if (this.transformsDynamicBufferGpus[i] != null) {
            this.transformsDynamicBufferGpus[i].close();
            this.transformsDynamicBufferGpus[i] = null;
         }
      }

      if (this.bytephaseSchemGhostChest != null) {
         this.bytephaseSchemGhostChest.close();
         this.bytephaseSchemGhostChest = null;
      }

      if (this.bytephaseSchemOverlay != null) {
         this.bytephaseSchemOverlay.close();
         this.bytephaseSchemOverlay = null;
      }

      if (this.bytephaseSchemOutline != null) {
         this.bytephaseSchemOutline.close();
         this.bytephaseSchemOutline = null;
      }
   }

   BufferGpu getGhostSchem() {
      if (this.bytephaseSchemGhostChest == null) {
         this.bytephaseSchemGhostChest = new BufferGpu("bytephase_schem_ghost_chest");
      }

      return this.bytephaseSchemGhostChest;
   }

   BufferGpu getGpusTransforms(BlockRenderLayer blockRenderLayer) {
      int ordinal = blockRenderLayer.ordinal();
      BufferGpu gpusTransforms = this.transformsDynamicBufferGpus[ordinal];
      if (gpusTransforms == null) {
         gpusTransforms = new BufferGpu("bytephase_schem_ghost_" + blockRenderLayer.getName());
         this.transformsDynamicBufferGpus[ordinal] = gpusTransforms;
      }

      return gpusTransforms;
   }

   SchemBytephase(long longValue, int value, int value2, int value3) {
      this.hashOpenTable = schemBytephaseTable;
      this.unpackExtraTable = table;
      this.intArray = schemBytephaseTable;
      this.longArray = chunkArrayTable;
      this.hashOpenActive = true;
      this.transformsDynamicBufferGpus = new BufferGpu[BlockRenderLayer.values().length];
      this.longValue = longValue;
      this.schemBytephaseValue = value;
      this.matrixStackValue = value2;
      this.arrayHashValue = value3;
      this.box = new Box(value, value2, value3, value + 16, value2 + 16, value3 + 16);
   }

   static long getTime(int schemBytephaseValue, int schemBytephaseValue2, int schemBytephaseValue3) {
      return BlockPos.asLong(schemBytephaseValue >> 4, schemBytephaseValue2 >> 4, schemBytephaseValue3 >> 4);
   }

   boolean isSchemBytephase() {
      for (BufferGpu bufferGpu : this.transformsDynamicBufferGpus) {
         if (bufferGpu != null && !bufferGpu.isBuffer()) {
            return false;
         }
      }

      return this.bytephaseSchemGhostChest != null && !this.bytephaseSchemGhostChest.isBuffer()
         ? false
         : (this.bytephaseSchemOverlay == null || this.bytephaseSchemOverlay.isBuffer()) && (this.bytephaseSchemOutline == null || this.bytephaseSchemOutline.isBuffer());
   }

   BufferGpu getSchemOutline() {
      if (this.bytephaseSchemOutline == null) {
         this.bytephaseSchemOutline = new BufferGpu("bytephase_schem_outline");
      }

      return this.bytephaseSchemOutline;
   }
}
