package dev.bytephase.engine.render;

import dev.bytephase.engine.world.BlockStateType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.ShapeIndexBuffer;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import dev.bytephase.engine.utils.RebuildFms;

public final class HashOpenSchem implements AutoCloseable {
   private final LongOpenHashSet longOpenHashSet;
   private final List<SchemBytephase> samplerGhostItems;
   private final Long2ObjectOpenHashMap<SchemBytephase> long2ObjectOpenHashMap = new Long2ObjectOpenHashMap();
   private ByteBuffer memAlloc;
   private static final BlockRenderLayer[] ghostSamplerBlockRenderLayer = BlockRenderLayer.values();
   private final LongOpenHashSet arrayHashLongOpenHashSet;
   private final List<GpuBufferSlice> transformsDynamicItems;
   private final List<GpuBufferSlice> transformsDynamicGpuBufferSl;
   private final BlockBuffer blockBuffer;
   private final RebuildFms rebuildFms;
   private final LongOpenHashSet rebuildFmsLongOpenHashSet;
   private GpuBuffer buffer;
   private GpuBuffer bufferBufferAlloc;
   private static final Direction[] offsetArrayDirections = Direction.values();
   private static final Vector4f VECTOR4F = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
   private final Long2ByteOpenHashMap long2ByteOpenHashMap = new Long2ByteOpenHashMap();
   private final LongArrayList longArrayList;
   private static final Vector3f VECTOR3F = new Vector3f();
   private final List<GpuBufferSlice> dynamicTextureGpuBufferSlice;
   private long lastRebuildTime;
   private static final Matrix4f MATRIX4F = new Matrix4f();
   private boolean fmsRebuildActive;
   private final Long2ObjectOpenHashMap<BlockState> blockStatesByPos = new Long2ObjectOpenHashMap();
   private long fmsRebuildValue;
   private final List<int[]> arrayHashIntArrayList;

   private void handleArrayHash(SchemBytephase schemBytephase, MinecraftClient client) {
      boolean hasChunk = client.world.getChunkManager().getChunk(schemBytephase.schemBytephaseValue >> 4, schemBytephase.arrayHashValue >> 4, ChunkStatus.FULL, false) != null;
      if (!hasChunk) {
         schemBytephase.chunkArrayCount++;
      } else {
         this.longArrayList.clear();
         this.arrayHashLongOpenHashSet.clear();
         boolean arrayHashFlag = this.arrayHashIntArrayList.size() != 1;
         Mutable mutable = new Mutable();

         for (int i = 0; i < this.arrayHashIntArrayList.size(); i++) {
            int[] arrayHashValues = this.arrayHashIntArrayList.get(i);
            int arrayHashValue = Math.max(arrayHashValues[0], schemBytephase.schemBytephaseValue);
            int arrayHashLimit = Math.min(arrayHashValues[3], schemBytephase.schemBytephaseValue + 15);
            int max2 = Math.max(arrayHashValues[1], schemBytephase.matrixStackValue);
            int min2 = Math.min(arrayHashValues[4], schemBytephase.matrixStackValue + 15);
            int max3 = Math.max(arrayHashValues[2], schemBytephase.arrayHashValue);
            int min3 = Math.min(arrayHashValues[5], schemBytephase.arrayHashValue + 15);
            if (arrayHashValue <= arrayHashLimit && max2 <= min2 && max3 <= min3) {
               for (int j = max2; j <= min2; j++) {
                  for (int k = max3; k <= min3; k++) {
                     for (int m = arrayHashValue; m <= arrayHashLimit; m++) {
                        long long2 = BlockPos.asLong(m, j, k);
                        if (!this.blockStatesByPos.containsKey(long2) && (!arrayHashFlag || !this.arrayHashLongOpenHashSet.contains(long2))) {
                           mutable.set(m, j, k);
                           if (!client.world.getBlockState(mutable).isAir()) {
                              this.longArrayList.add(long2);
                              if (arrayHashFlag) {
                                 this.arrayHashLongOpenHashSet.add(long2);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         if (!this.longArrayList.isEmpty()) {
            schemBytephase.longArray = this.longArrayList.toLongArray();
            schemBytephase.arrayHashCount = schemBytephase.arrayHashCount + schemBytephase.longArray.length;
         }
      }
   }

   public boolean canFmsRebuild(
      OpacityColorData opacityColorData, Vec3d vec, List<BlockPos> list, List<BlockState> list2, int fmsRebuildValue, OrderedRenderCommandQueue orderedRenderCommandQueue, float tickProgress
   ) {
      if (this.fmsRebuildActive && !this.long2ObjectOpenHashMap.isEmpty()) {
         MinecraftClient instance = MinecraftClient.getInstance();
         if (instance.world == null) {
            return false;
         } else {
            long time = opacityColorData.getOpacityBytephase();
            if (time != this.lastRebuildTime) {
               this.lastRebuildTime = time;
               this.updateHashOpen();
            }

            this.rebuildFms.handleFmsRebuild(this.long2ObjectOpenHashMap.size());
            this.fmsRebuildValue = SchemBytephase.getTime((int)Math.floor(vec.x), (int)Math.floor(vec.y), (int)Math.floor(vec.z));
            Frustum frustum = this.getFrustum(vec);
            double renderDistance = opacityColorData.renderDistance();
            this.samplerGhostItems.clear();
            ObjectIterator iterator = this.long2ObjectOpenHashMap.values().iterator();

            while (iterator.hasNext()) {
               SchemBytephase schemBytephase = (SchemBytephase)iterator.next();
               schemBytephase.fmsRebuildActive = false;
               schemBytephase.distanceSq = schemBytephase.getAmount(vec.x, vec.y, vec.z);
               double fmsRebuildFactor = renderDistance + 13.86;
               if (schemBytephase.distanceSq > fmsRebuildFactor * fmsRebuildFactor) {
                  this.rebuildFms.rebuildFmsCount++;
               } else if (!opacityColorData.canPlayer(schemBytephase.matrixStackValue)) {
                  this.rebuildFms.rebuildFmsCount++;
               } else if (frustum != null && !frustum.isVisible(schemBytephase.box)) {
                  this.rebuildFms.schemCount++;
               } else {
                  schemBytephase.fmsRebuildActive = true;
                  this.samplerGhostItems.add(schemBytephase);
               }
            }

            this.samplerGhostItems.sort((schemBytephase2, schemBytephase3) -> Double.compare(schemBytephase3.distanceSq, schemBytephase2.distanceSq));
            this.rebuildFms.samplerghostitemsSize = this.samplerGhostItems.size();
            this.rebuildFms.fmsRebuildValue = this.longOpenHashSet.size();
            this.handleRebuildFms(opacityColorData, list, list2, fmsRebuildValue, vec);
            this.rebuildFms.totalIntArrayLength = 0;
            this.rebuildFms.totalBufferClosed = 0;

            for (SchemBytephase schemBytephase4 : this.samplerGhostItems) {
               this.rebuildFms.totalIntArrayLength = this.rebuildFms.totalIntArrayLength + schemBytephase4.intArray.length;
               this.rebuildFms.totalBufferClosed = this.rebuildFms.totalBufferClosed + schemBytephase4.bufferCloseValue;
            }

            this.rebuildFms.update();
            ObjectIterator iterator2 = this.long2ObjectOpenHashMap.values().iterator();

            while (iterator2.hasNext()) {
               SchemBytephase next5 = (SchemBytephase)iterator2.next();
               this.rebuildFms.totalChunkArrayRebuilt = this.rebuildFms.totalChunkArrayRebuilt + next5.chunkArrayRebuiltCount;
               this.rebuildFms.rebuildFmsValue = this.rebuildFms.rebuildFmsValue + next5.schemBytephaseCount + next5.visitedChunkCount;
               this.rebuildFms.accumulatedRebuildCount = this.rebuildFms.accumulatedRebuildCount + next5.fmsRebuildCount;
            }

            try {
               this.handleDynamicTexture(opacityColorData, vec);
               return true;
            } catch (Throwable e) {
               this.fmsRebuildActive = false;
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private void handleRebuildFms(OpacityColorData opacityColorData, List<BlockPos> list, List<BlockState> list2, int rebuildFmsValue, Vec3d vec) {
      if (!this.longOpenHashSet.isEmpty() || !this.rebuildFmsLongOpenHashSet.isEmpty()) {
         MinecraftClient instance = MinecraftClient.getInstance();
         ArrayList arrayList = new ArrayList();

         for (SchemBytephase schemBytephase : this.samplerGhostItems) {
            boolean contains = this.longOpenHashSet.contains(schemBytephase.longValue);
            if ((contains || this.rebuildFmsLongOpenHashSet.contains(schemBytephase.longValue))
               && (contains || !schemBytephase.chunkArrayActive || instance.world.getChunkManager().getChunk(schemBytephase.schemBytephaseValue >> 4, schemBytephase.arrayHashValue >> 4, ChunkStatus.FULL, false) != null)) {
               arrayList.add(schemBytephase);
            }
         }

         if (arrayList.size() < opacityColorData.rebuildBudget()) {
            LongIterator iterator = this.longOpenHashSet.iterator();

            while (iterator.hasNext()) {
               long next2 = (Long)iterator.next();
               SchemBytephase long2objectopenhashmap = (SchemBytephase)this.long2ObjectOpenHashMap.get(next2);
               if (long2objectopenhashmap != null && !long2objectopenhashmap.fmsRebuildActive) {
                  long2objectopenhashmap.distanceSq = long2objectopenhashmap.getAmount(vec.x, vec.y, vec.z);
                  arrayList.add(long2objectopenhashmap);
               }
            }
         }

         if (!arrayList.isEmpty()) {
            arrayList.sort((obj, obj2) -> Double.compare(((dev.bytephase.engine.render.SchemBytephase) obj).distanceSq, ((dev.bytephase.engine.render.SchemBytephase) obj2).distanceSq));
            BlockRenderViewHelper blockRenderViewHelper = new BlockRenderViewHelper(instance.world, this.blockStatesByPos, opacityColorData);
            BlockBuffer.Context context = new BlockBuffer.Context(
               list,
               list2,
               this.long2ByteOpenHashMap,
               blockRenderViewHelper,
               opacityColorData,
               rebuildFmsValue,
               opacityColorData.correctColor(),
               opacityColorData.missingColor(),
               opacityColorData.wrongBlockColor(),
               opacityColorData.wrongStateColor(),
               opacityColorData.extraColor()
            );
            int rebuildBudget = opacityColorData.rebuildBudget();
            long nanoTime = System.nanoTime();
            long rebuildFmsValue3 = nanoTime + 8333333L;

            for (SchemBytephase schemBytephase2 : (Iterable<dev.bytephase.engine.render.SchemBytephase>) (arrayList)) {
               if (rebuildBudget-- <= 0 || this.rebuildFms.fmsRebuildCount > 0 && System.nanoTime() > rebuildFmsValue3) {
                  break;
               }

               if (this.rebuildFmsLongOpenHashSet.contains(schemBytephase2.longValue)) {
                  this.handleChunkArray(schemBytephase2, list, list2, instance, opacityColorData);
                  this.rebuildFms.rebuildCallCount++;
               }

               this.blockBuffer.handleBufferClose(schemBytephase2, context);
               schemBytephase2.hashOpenActive = false;
               this.longOpenHashSet.remove(schemBytephase2.longValue);
               if (schemBytephase2.chunkArrayCount == 0) {
                  this.rebuildFmsLongOpenHashSet.remove(schemBytephase2.longValue);
               }

               this.rebuildFms.fmsRebuildCount++;
            }

            this.rebuildFms.fmsRebuildDeadline = System.nanoTime() - nanoTime;
         }
      }
   }

   private Frustum getFrustum(Vec3d vec) {
      return FrustumUtils.getFrustum();
   }

   private void handleBufferAlloc(CommandEncoder commandEncoder, OpacityColorData opacityColorData) {
      if (this.memAlloc == null) {
         this.memAlloc = MemoryUtil.memAlloc(32);
         this.buffer = RenderSystem.getDevice().createBuffer(() -> "bytephase_schem_fill_ubo", 136, 32L);
         this.bufferBufferAlloc = RenderSystem.getDevice().createBuffer(() -> "bytephase_schem_fill_ubo_occ", 136, 32L);
      }

      if (opacityColorData.isLitematicaStyle()) {
         this.handleAllocMem(commandEncoder, this.buffer, 1.0F, 0.0F, 1.0F, 1.0F);
         this.handleAllocMem(commandEncoder, this.bufferBufferAlloc, 1.0F, 0.0F, 1.0F, 1.0F);
      } else {
         float offsetArray = this.getOffsetArray(opacityColorData);
         float hideInterior = opacityColorData.getHideInterior(offsetArray);
         float interiorFade = opacityColorData.getInteriorFade(offsetArray);
         float hideCamera = opacityColorData.getHideCamera();
         this.handleAllocMem(commandEncoder, this.buffer, opacityColorData.ghostOpacity(), hideInterior, interiorFade, hideCamera);
         this.handleAllocMem(commandEncoder, this.bufferBufferAlloc, opacityColorData.occludedOpacity(), 0.0F, 0.0F, 1.0F);
      }
   }

   private void handleGhostSampler(
      CommandEncoder commandEncoder,
      Framebuffer framebuffer,
      OpacityColorData opacityColorData,
      Vec3d vec,
      RenderPipeline renderPipeline,
      GpuTextureView gpuTextureView,
      GpuSampler gpuSampler,
      GpuTextureView gpuTextureView2,
      GpuSampler gpuSampler2,
      float ghostSamplerValue,
      boolean ghostSamplerFlag
   ) {
      if (!(ghostSamplerValue <= 0.003F)) {
         GpuBuffer gpuBuffer = ghostSamplerFlag ? this.bufferBufferAlloc : this.buffer;
         if (gpuBuffer != null) {
            try (RenderPass renderPass = commandEncoder.createRenderPass(
                  () -> "bytephase:schem_ghost", framebuffer.getColorAttachmentView(), OptionalInt.empty(), framebuffer.getDepthAttachmentView(), OptionalDouble.empty()
               )) {
               renderPass.setPipeline(renderPipeline);
               RenderSystem.bindDefaultUniforms(renderPass);
               renderPass.bindTexture("Sampler0", gpuTextureView, gpuSampler);
               renderPass.setUniform("BytephaseGhostData", gpuBuffer);

               for (BlockRenderLayer blockRenderLayer : ghostSamplerBlockRenderLayer) {
                  if (!blockRenderLayer.isTranslucent()) {
                     this.handleGhostSampler(renderPass, blockRenderLayer);
                  }
               }

               for (BlockRenderLayer ghostSamplerBlockRenderLayerItem : ghostSamplerBlockRenderLayer) {
                  if (ghostSamplerBlockRenderLayerItem.isTranslucent()) {
                     this.handleGhostSampler(renderPass, ghostSamplerBlockRenderLayerItem);
                  }
               }

               if (gpuTextureView2 != null && gpuSampler2 != null) {
                  boolean ghostSamplerFlag2 = false;

                  for (SchemBytephase schemBytephase : this.samplerGhostItems) {
                     if (schemBytephase.bytephaseSchemGhostChest != null && !schemBytephase.bytephaseSchemGhostChest.isBuffer()) {
                        ghostSamplerFlag2 = true;
                        break;
                     }
                  }

                  if (ghostSamplerFlag2) {
                     renderPass.bindTexture("Sampler0", gpuTextureView2, gpuSampler2);

                     for (int i = 0; i < this.samplerGhostItems.size(); i++) {
                        BufferGpu bytephaseSchemGhostChest = this.samplerGhostItems.get(i).bytephaseSchemGhostChest;
                        if (bytephaseSchemGhostChest != null && !bytephaseSchemGhostChest.isBuffer()) {
                           this.handleTransformsDynamic(renderPass, i, false);
                           this.handleBufferFms(renderPass, bytephaseSchemGhostChest);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public void updateHashOpen() {
      ObjectIterator iterator = this.long2ObjectOpenHashMap.values().iterator();

      while (iterator.hasNext()) {
         SchemBytephase schemBytephase = (SchemBytephase)iterator.next();
         schemBytephase.hashOpenActive = true;
         this.rebuildFmsLongOpenHashSet.add(schemBytephase.longValue);
         this.longOpenHashSet.add(schemBytephase.longValue);
      }
   }

   public void handle(int hashOpenValue, int hashOpenValue2, int hashOpenValue3) {
      if (this.canHashOpen(hashOpenValue, hashOpenValue2, hashOpenValue3)) {
         this.handleHashOpen(SchemBytephase.getTime(hashOpenValue, hashOpenValue2, hashOpenValue3));
         if ((hashOpenValue & 15) == 0) {
            this.handleHashOpen(SchemBytephase.getTime(hashOpenValue - 1, hashOpenValue2, hashOpenValue3));
         }

         if ((hashOpenValue & 15) == 15) {
            this.handleHashOpen(SchemBytephase.getTime(hashOpenValue + 1, hashOpenValue2, hashOpenValue3));
         }

         if ((hashOpenValue2 & 15) == 0) {
            this.handleHashOpen(SchemBytephase.getTime(hashOpenValue, hashOpenValue2 - 1, hashOpenValue3));
         }

         if ((hashOpenValue2 & 15) == 15) {
            this.handleHashOpen(SchemBytephase.getTime(hashOpenValue, hashOpenValue2 + 1, hashOpenValue3));
         }

         if ((hashOpenValue3 & 15) == 0) {
            this.handleHashOpen(SchemBytephase.getTime(hashOpenValue, hashOpenValue2, hashOpenValue3 - 1));
         }

         if ((hashOpenValue3 & 15) == 15) {
            this.handleHashOpen(SchemBytephase.getTime(hashOpenValue, hashOpenValue2, hashOpenValue3 + 1));
         }
      }
   }

   private void handleTransformsDynamic(RenderPass renderPass, int index, boolean transformsDynamicFlag) {
      List list = transformsDynamicFlag && index < this.transformsDynamicGpuBufferSl.size() ? this.transformsDynamicGpuBufferSl : this.dynamicTextureGpuBufferSlice;
      renderPass.setUniform("DynamicTransforms", (GpuBufferSlice)list.get(index));
   }

   public void handleHashOpen(List<BlockPos> list, List<BlockState> list2, List<int[]> wordListed) {
      this.updateHashOpenLong2ObjectOpenHashMap();
      this.long2ByteOpenHashMap.clear();
      this.blockStatesByPos.clear();
      this.rebuildFmsLongOpenHashSet.clear();
      this.longOpenHashSet.clear();
      this.arrayHashIntArrayList.clear();
      this.arrayHashIntArrayList.addAll(wordListed);
      this.blockStatesByPos.ensureCapacity(list.size());
      Long2ObjectOpenHashMap long2ObjectOpenHashMap = new Long2ObjectOpenHashMap();

      for (int i = 0; i < list.size(); i++) {
         BlockPos position = (BlockPos)list.get(i);
         BlockState blockState = (BlockState)list2.get(i);
         if (blockState != null) {
            this.blockStatesByPos.put(position.asLong(), blockState);
            long hashOpenValue = SchemBytephase.getTime(position.getX(), position.getY(), position.getZ());
            SchemBytephase long2objectopenhashmap = (SchemBytephase)this.long2ObjectOpenHashMap.get(hashOpenValue);
            if (long2objectopenhashmap == null) {
               SchemBytephase schemBytephase = new SchemBytephase(hashOpenValue, position.getX() >> 4 << 4, position.getY() >> 4 << 4, position.getZ() >> 4 << 4);
               this.long2ObjectOpenHashMap.put(hashOpenValue, schemBytephase);
            }

            ((IntArrayList)long2ObjectOpenHashMap.computeIfAbsent(hashOpenValue, hashOpenValue2 -> new IntArrayList())).add(i);
         }
      }

      int hashOpenValue3 = 262144;

      for (int[] hashOpenValues : this.arrayHashIntArrayList) {
         long hashOpenFactor = (long)(hashOpenValues[3] >> 4) - (hashOpenValues[0] >> 4) + 1L;
         long hashOpenFactor2 = (long)(hashOpenValues[4] >> 4) - (hashOpenValues[1] >> 4) + 1L;
         long hashOpenFactor3 = (long)(hashOpenValues[5] >> 4) - (hashOpenValues[2] >> 4) + 1L;
         if (hashOpenFactor * hashOpenFactor2 * hashOpenFactor3 <= 262144L) {
            for (int j = hashOpenValues[0] >> 4; j <= hashOpenValues[3] >> 4; j++) {
               for (int m = hashOpenValues[1] >> 4; m <= hashOpenValues[4] >> 4; m++) {
                  for (int p = hashOpenValues[2] >> 4; p <= hashOpenValues[5] >> 4; p++) {
                     long long2 = BlockPos.asLong(j, m, p);
                     if (!this.long2ObjectOpenHashMap.containsKey(long2)) {
                        this.long2ObjectOpenHashMap.put(long2, new SchemBytephase(long2, j << 4, m << 4, p << 4));
                     }
                  }
               }
            }
         }
      }

      ObjectIterator iterator = this.long2ObjectOpenHashMap.values().iterator();

      while (iterator.hasNext()) {
         SchemBytephase next2 = (SchemBytephase)iterator.next();
         IntArrayList intArrayList = (IntArrayList)long2ObjectOpenHashMap.get(next2.longValue);
         next2.hashOpenTable = intArrayList == null ? new int[0] : intArrayList.toIntArray();
         next2.unpackExtraTable = new byte[next2.hashOpenTable.length];
         Arrays.fill(next2.unpackExtraTable, (byte)BlockStateType.UNKNOWN.ordinal());
         next2.hashOpenActive = true;
         this.rebuildFmsLongOpenHashSet.add(next2.longValue);
         this.longOpenHashSet.add(next2.longValue);
      }
   }

   public HashOpenSchem() {
      this.blockBuffer = new BlockBuffer();
      this.rebuildFms = new RebuildFms();
      this.rebuildFmsLongOpenHashSet = new LongOpenHashSet();
      this.longOpenHashSet = new LongOpenHashSet();
      this.samplerGhostItems = new ArrayList<>();
      this.dynamicTextureGpuBufferSlice = new ArrayList<>();
      this.transformsDynamicGpuBufferSl = new ArrayList<>();
      this.transformsDynamicItems = new ArrayList<>();
      this.arrayHashIntArrayList = new ArrayList<>();
      this.longArrayList = new LongArrayList();
      this.arrayHashLongOpenHashSet = new LongOpenHashSet();
      this.lastRebuildTime = Long.MIN_VALUE;
      this.fmsRebuildActive = true;
   }

   public void updateClearTransforms() {
      this.updateHashOpenLong2ObjectOpenHashMap();
      this.long2ByteOpenHashMap.clear();
      this.blockStatesByPos.clear();
      this.rebuildFmsLongOpenHashSet.clear();
      this.longOpenHashSet.clear();
      this.samplerGhostItems.clear();
      this.dynamicTextureGpuBufferSlice.clear();
      this.transformsDynamicGpuBufferSl.clear();
      this.transformsDynamicItems.clear();
      this.arrayHashIntArrayList.clear();
      this.lastRebuildTime = Long.MIN_VALUE;
      this.fmsRebuildActive = true;
   }

   private float getOffsetArray(OpacityColorData opacityColorData) {
      SchemBytephase long2objectopenhashmap = (SchemBytephase)this.long2ObjectOpenHashMap.get(this.fmsRebuildValue);
      if (long2objectopenhashmap == null) {
         return 1.0F;
      } else {
         int offsetArrayTotal = long2objectopenhashmap.hashOpenTable.length;
         int offsetArrayCounter = 1;

         for (Direction direction : offsetArrayDirections) {
            SchemBytephase long2objectopenhashmap2 = (SchemBytephase)this.long2ObjectOpenHashMap
               .get(
                  SchemBytephase.getTime(
                     long2objectopenhashmap.schemBytephaseValue + direction.getOffsetX() * 16 + 8, long2objectopenhashmap.matrixStackValue + direction.getOffsetY() * 16 + 8, long2objectopenhashmap.arrayHashValue + direction.getOffsetZ() * 16 + 8
                  )
               );
            if (long2objectopenhashmap2 != null) {
               offsetArrayTotal += long2objectopenhashmap2.hashOpenTable.length;
               offsetArrayCounter++;
            }
         }

         float offsetArrayValue = (float)offsetArrayTotal / (offsetArrayCounter * 4096);
         return 1.0F + Math.min(1.0F, offsetArrayValue * 2.2F);
      }
   }

   private void handleGhostSampler(RenderPass renderPass, BlockRenderLayer blockRenderLayer) {
      for (int i = 0; i < this.samplerGhostItems.size(); i++) {
         BufferGpu bufferGpu = this.samplerGhostItems.get(i).transformsDynamicBufferGpus[blockRenderLayer.ordinal()];
         if (bufferGpu != null && !bufferGpu.isBuffer()) {
            this.handleTransformsDynamic(renderPass, i, false);
            this.handleBufferFms(renderPass, bufferGpu);
         }
      }
   }

   private void handleBufferFms(RenderPass renderPass, BufferGpu bufferGpu) {
      renderPass.setVertexBuffer(0, bufferGpu.getBuffer());
      ShapeIndexBuffer sequentialBuffer = RenderSystem.getSequentialBuffer(bufferGpu.mode);
      renderPass.setIndexBuffer(sequentialBuffer.getIndexBuffer(bufferGpu.indexCount), sequentialBuffer.getIndexType());
      renderPass.drawIndexed(0, 0, bufferGpu.indexCount, 1);
      this.rebuildFms.bufferFmsCount++;
   }

   private void handleAllocMem(CommandEncoder commandEncoder, GpuBuffer gpuBuffer, float allocMemValue, float hideInterior, float interiorFade, float hideCamera) {
      this.memAlloc.clear();
      this.memAlloc.putFloat(1.0F).putFloat(1.0F).putFloat(1.0F).putFloat(allocMemValue);
      this.memAlloc.putFloat(hideInterior).putFloat(interiorFade).putFloat(hideCamera).putFloat(0.06F);
      this.memAlloc.flip();
      commandEncoder.writeToBuffer(gpuBuffer.slice(0L, 32L), this.memAlloc);
   }

   private static RenderPipeline getRegister(BlockRenderLayer blockRenderLayer) {
      return switch (blockRenderLayer) {
         case SOLID -> BuilderSchemUtils.registerSamplerTransforms;
         case CUTOUT -> BuilderSchemUtils.builderSchemRenderPipeline;
         case TRANSLUCENT, TRIPWIRE -> BuilderSchemUtils.samplerTransformsRenderPipel;
      };
   }

   private void handleSamplerTransforms(
      CommandEncoder commandEncoder,
      Framebuffer framebuffer,
      OpacityColorData opacityColorData,
      MinecraftClient client,
      GpuTextureView gpuTextureView,
      GpuSampler gpuSampler,
      GpuTextureView gpuTextureView2,
      GpuSampler gpuSampler2
   ) {
      boolean samplerTransformsFlag = false;

      for (SchemBytephase schemBytephase : this.samplerGhostItems) {
         for (BufferGpu bufferGpu : schemBytephase.transformsDynamicBufferGpus) {
            if (bufferGpu != null && !bufferGpu.isBuffer()) {
               samplerTransformsFlag = true;
               break;
            }
         }

         if (samplerTransformsFlag || schemBytephase.bytephaseSchemGhostChest != null && !schemBytephase.bytephaseSchemGhostChest.isBuffer()) {
            samplerTransformsFlag = true;
            break;
         }
      }

      boolean litematicaTranslucent = opacityColorData.litematicaTranslucent();
      if (samplerTransformsFlag && opacityColorData.ghostOpacity() > 0.003F) {
         GpuTextureView glTextureView = client.gameRenderer.getLightmapTextureManager().getGlTextureView();
         GpuSampler samplercache = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

         try (RenderPass renderPass = commandEncoder.createRenderPass(
               () -> "bytephase:schem_lit", framebuffer.getColorAttachmentView(), OptionalInt.empty(), framebuffer.getDepthAttachmentView(), OptionalDouble.empty()
            )) {
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture("Sampler2", glTextureView, samplercache);
            renderPass.bindTexture("Sampler0", gpuTextureView, gpuSampler);

            for (BlockRenderLayer blockRenderLayer : ghostSamplerBlockRenderLayer) {
               renderPass.setPipeline(litematicaTranslucent ? BuilderSchemUtils.samplerTransformsRenderPipel : getRegister(blockRenderLayer));

               for (int i = 0; i < this.samplerGhostItems.size(); i++) {
                  BufferGpu bufferGpu2 = this.samplerGhostItems.get(i).transformsDynamicBufferGpus[blockRenderLayer.ordinal()];
                  if (bufferGpu2 != null && !bufferGpu2.isBuffer()) {
                     renderPass.setUniform("DynamicTransforms", this.transformsDynamicItems.get(i));
                     this.handleBufferFms(renderPass, bufferGpu2);
                  }
               }
            }

            if (gpuTextureView2 != null && gpuSampler2 != null) {
               boolean samplerTransformsFlag2 = false;

               for (int j = 0; j < this.samplerGhostItems.size(); j++) {
                  BufferGpu bytephaseSchemGhostChest = this.samplerGhostItems.get(j).bytephaseSchemGhostChest;
                  if (bytephaseSchemGhostChest != null && !bytephaseSchemGhostChest.isBuffer()) {
                     if (!samplerTransformsFlag2) {
                        renderPass.setPipeline(litematicaTranslucent ? BuilderSchemUtils.samplerTransformsRenderPipel : BuilderSchemUtils.registerSamplerTransforms);
                        renderPass.bindTexture("Sampler0", gpuTextureView2, gpuSampler2);
                        samplerTransformsFlag2 = true;
                     }

                     renderPass.setUniform("DynamicTransforms", this.transformsDynamicItems.get(j));
                     this.handleBufferFms(renderPass, bytephaseSchemGhostChest);
                  }
               }
            }
         }
      }

      boolean missingThroughWalls = opacityColorData.missingThroughWalls();
      if (opacityColorData.outlines()) {
         this.handleGhostBytephase(commandEncoder, framebuffer, opacityColorData, null, missingThroughWalls ? BuilderSchemUtils.renderPipeline : GhostBuilderUtils.RENDER_PIPELINE, false, true);
      }

      this.handleGhostBytephase(commandEncoder, framebuffer, opacityColorData, null, missingThroughWalls ? BuilderSchemUtils.register : GhostBuilderUtils.BYTEPHASE_SCHEM, false, false);
   }

   private boolean canHashOpen(int hashOpenValue, int hashOpenValue2, int hashOpenValue3) {
      return this.canFlag(hashOpenValue, hashOpenValue2, hashOpenValue3)
         || this.blockStatesByPos.containsKey(BlockPos.asLong(hashOpenValue, hashOpenValue2, hashOpenValue3))
         || this.blockStatesByPos.containsKey(BlockPos.asLong(hashOpenValue + 1, hashOpenValue2, hashOpenValue3))
         || this.blockStatesByPos.containsKey(BlockPos.asLong(hashOpenValue - 1, hashOpenValue2, hashOpenValue3))
         || this.blockStatesByPos.containsKey(BlockPos.asLong(hashOpenValue, hashOpenValue2 + 1, hashOpenValue3))
         || this.blockStatesByPos.containsKey(BlockPos.asLong(hashOpenValue, hashOpenValue2 - 1, hashOpenValue3))
         || this.blockStatesByPos.containsKey(BlockPos.asLong(hashOpenValue, hashOpenValue2, hashOpenValue3 + 1))
         || this.blockStatesByPos.containsKey(BlockPos.asLong(hashOpenValue, hashOpenValue2, hashOpenValue3 - 1));
   }

   private boolean canFlag(int hashOpenLimit, int hashOpenLimit2, int hashOpenLimit3) {
      for (int i = 0; i < this.arrayHashIntArrayList.size(); i++) {
         int[] hashOpenValues = this.arrayHashIntArrayList.get(i);
         if (hashOpenLimit >= hashOpenValues[0] && hashOpenLimit <= hashOpenValues[3] && hashOpenLimit2 >= hashOpenValues[1] && hashOpenLimit2 <= hashOpenValues[4] && hashOpenLimit3 >= hashOpenValues[2] && hashOpenLimit3 <= hashOpenValues[5]) {
            return true;
         }
      }

      return false;
   }

   public RebuildFms getRebuildFms() {
      return this.rebuildFms;
   }

   private void handleHashOpen(long hashOpenValue) {
      if (this.long2ObjectOpenHashMap.containsKey(hashOpenValue)) {
         this.rebuildFmsLongOpenHashSet.add(hashOpenValue);
         this.longOpenHashSet.add(hashOpenValue);
      }
   }

   public void handle(List<BlockPos> list, List<BlockState> list2) {
      this.handleHashOpen(list, list2, List.of());
   }

   private void handleDynamicTexture(OpacityColorData opacityColorData, Vec3d vec) {
      MinecraftClient instance = MinecraftClient.getInstance();
      CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
      Framebuffer framebuffer = instance.getFramebuffer();
      this.samplerGhostItems.removeIf(SchemBytephase::isSchemBytephase);
      this.rebuildFms.samplerghostitemsSize = this.samplerGhostItems.size();
      this.dynamicTextureGpuBufferSlice.clear();
      this.transformsDynamicGpuBufferSl.clear();
      this.transformsDynamicItems.clear();
      boolean litematicaStyle = opacityColorData.isLitematicaStyle();
      Vector4f vector4f = new Vector4f(1.0F, 1.0F, 1.0F, opacityColorData.litematicaTranslucent() ? Math.max(0.0F, Math.min(1.0F, opacityColorData.ghostOpacity())) : 1.0F);
      Vector3f vector3f = new Vector3f();
      boolean missingThroughWalls = opacityColorData.missingThroughWalls();
      Vector4f vector4f2 = new Vector4f(1.0F, 1.0F, 1.0F, Math.max(0.0F, Math.min(1.0F, opacityColorData.occludedOpacity())));

      for (SchemBytephase schemBytephase : this.samplerGhostItems) {
         Matrix4f matrix4f = new Matrix4f(RenderSystem.getModelViewMatrix());
         matrix4f.translate((float)(schemBytephase.schemBytephaseValue - vec.x), (float)(schemBytephase.matrixStackValue - vec.y), (float)(schemBytephase.arrayHashValue - vec.z));
         this.dynamicTextureGpuBufferSlice.add(RenderSystem.getDynamicUniforms().write(matrix4f, VECTOR4F, VECTOR3F, MATRIX4F));
         if (missingThroughWalls) {
            this.transformsDynamicGpuBufferSl.add(RenderSystem.getDynamicUniforms().write(matrix4f, vector4f2, VECTOR3F, MATRIX4F));
         }

         if (litematicaStyle) {
            vector3f.set((float)(schemBytephase.schemBytephaseValue - vec.x), (float)(schemBytephase.matrixStackValue - vec.y), (float)(schemBytephase.arrayHashValue - vec.z));
            this.transformsDynamicItems.add(RenderSystem.getDynamicUniforms().write(RenderSystem.getModelViewMatrix(), vector4f, vector3f, MATRIX4F));
         }
      }

      this.handleBufferAlloc(commandEncoder, opacityColorData);
      AbstractTexture texture = instance.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
      GpuTextureView glTextureView = texture.getGlTextureView();
      GpuSampler sampler = texture.getSampler();
      GpuTextureView gpuTextureView = null;
      GpuSampler gpuSampler = null;

      try {
         AbstractTexture texture2 = instance.getTextureManager().getTexture(TexturedRenderLayers.CHEST_ATLAS_TEXTURE);
         if (texture2 != null) {
            gpuTextureView = texture2.getGlTextureView();
            gpuSampler = texture2.getSampler();
         }
      } catch (Throwable e) {
      }

      if (litematicaStyle) {
         this.handleSamplerTransforms(commandEncoder, framebuffer, opacityColorData, instance, glTextureView, sampler, gpuTextureView, gpuSampler);
      } else {
         if (opacityColorData.missingThroughWalls()) {
            this.handleGhostSampler(commandEncoder, framebuffer, opacityColorData, vec, GhostBuilderUtils.GHOST_OCCLUDED_PIPELINE, glTextureView, sampler, gpuTextureView, gpuSampler, opacityColorData.occludedOpacity(), true);
            this.handleGhostBytephase(commandEncoder, framebuffer, opacityColorData, vec, GhostBuilderUtils.OVERLAY_QUADS_OCCLUDED_PIPELINE, true, false);
            if (opacityColorData.outlines()) {
               this.handleGhostBytephase(commandEncoder, framebuffer, opacityColorData, vec, GhostBuilderUtils.DEPTH_BIAS, true, true);
            }
         }

         RenderPipeline renderPipeline = GhostBuilderUtils.GHOST_BYTEPHASE;
         this.handleGhostSampler(commandEncoder, framebuffer, opacityColorData, vec, renderPipeline, glTextureView, sampler, gpuTextureView, gpuSampler, opacityColorData.ghostOpacity(), false);
         this.handleGhostBytephase(commandEncoder, framebuffer, opacityColorData, vec, GhostBuilderUtils.BYTEPHASE_SCHEM, false, false);
         if (opacityColorData.outlines()) {
            this.handleGhostBytephase(commandEncoder, framebuffer, opacityColorData, vec, GhostBuilderUtils.RENDER_PIPELINE, false, true);
         }
      }
   }

   private void handleChunkArray(SchemBytephase schemBytephase, List<BlockPos> list, List<BlockState> list2, MinecraftClient client, OpacityColorData opacityColorData) {
      schemBytephase.fmsRebuildCount = schemBytephase.chunkArrayRebuiltCount = schemBytephase.schemBytephaseCount = schemBytephase.visitedChunkCount = schemBytephase.arrayHashCount = schemBytephase.chunkArrayCount = 0;
      boolean litematicaStyle = opacityColorData.isLitematicaStyle();
      boolean hasChunk = client.world.getChunkManager().getChunk(schemBytephase.schemBytephaseValue >> 4, schemBytephase.arrayHashValue >> 4, ChunkStatus.FULL, false) != null;
      schemBytephase.chunkArrayActive = !hasChunk;

      for (int i = 0; i < schemBytephase.hashOpenTable.length; i++) {
         int index = schemBytephase.hashOpenTable[i];
         BlockPos blockPos = (BlockPos)list.get(index);
         BlockState cfg2 = (BlockState)list2.get(index);
         BlockState blockState = hasChunk ? client.world.getBlockState(blockPos) : null;
         BlockStateType blockStateType = litematicaStyle ? BlockStateType.getCorrectAir(cfg2, blockState, hasChunk) : BlockStateType.getCorrectReplaceable(cfg2, blockState, hasChunk);
         schemBytephase.unpackExtraTable[i] = (byte)blockStateType.ordinal();
         this.long2ByteOpenHashMap.put(blockPos.asLong(), (byte)blockStateType.ordinal());
         switch (blockStateType) {
            case CORRECT:
               schemBytephase.fmsRebuildCount++;
               break;
            case MISSING:
               schemBytephase.chunkArrayRebuiltCount++;
               break;
            case WRONG_BLOCK:
               schemBytephase.schemBytephaseCount++;
               break;
            case WRONG_STATE:
               schemBytephase.visitedChunkCount++;
               break;
            case EXTRA:
               schemBytephase.arrayHashCount++;
               break;
            case UNKNOWN:
               schemBytephase.chunkArrayCount++;
         }
      }

      schemBytephase.longArray = SchemBytephase.chunkArrayTable;
      if (litematicaStyle && !this.arrayHashIntArrayList.isEmpty()) {
         this.handleArrayHash(schemBytephase, client);
      }
   }

   private void handleGhostBytephase(
      CommandEncoder commandEncoder, Framebuffer framebuffer, OpacityColorData opacityColorData, Vec3d vec, RenderPipeline renderPipeline, boolean ghostBytephaseFlag, boolean ghostBytephaseFlag2
   ) {
      boolean ghostBytephaseFlag3 = false;

      for (SchemBytephase schemBytephase : this.samplerGhostItems) {
         BufferGpu bufferGpu = ghostBytephaseFlag2 ? schemBytephase.bytephaseSchemOutline : schemBytephase.bytephaseSchemOverlay;
         if (bufferGpu != null && !bufferGpu.isBuffer()) {
            ghostBytephaseFlag3 = true;
            break;
         }
      }

      if (ghostBytephaseFlag3) {
         try (RenderPass renderPass = commandEncoder.createRenderPass(
               () -> "bytephase:schem_overlay", framebuffer.getColorAttachmentView(), OptionalInt.empty(), framebuffer.getDepthAttachmentView(), OptionalDouble.empty()
            )) {
            renderPass.setPipeline(renderPipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            if (!ghostBytephaseFlag2) {
               GpuBuffer gpuBuffer = ghostBytephaseFlag ? this.bufferBufferAlloc : this.buffer;
               if (gpuBuffer != null) {
                  renderPass.setUniform("BytephaseGhostData", gpuBuffer);
               }
            }

            for (int i = 0; i < this.samplerGhostItems.size(); i++) {
               SchemBytephase samplerghostitem = this.samplerGhostItems.get(i);
               BufferGpu bufferGpu2 = ghostBytephaseFlag2 ? samplerghostitem.bytephaseSchemOutline : samplerghostitem.bytephaseSchemOverlay;
               if (bufferGpu2 != null && !bufferGpu2.isBuffer()) {
                  this.handleTransformsDynamic(renderPass, i, ghostBytephaseFlag);
                  this.handleBufferFms(renderPass, bufferGpu2);
               }
            }
         }
      }
   }

   @Override
   public void close() {
      this.updateClearTransforms();
      this.blockBuffer.close();
      if (this.buffer != null) {
         this.buffer.close();
         this.buffer = null;
      }

      if (this.memAlloc != null) {
         MemoryUtil.memFree(this.memAlloc);
         this.memAlloc = null;
      }
   }

   private void updateHashOpenLong2ObjectOpenHashMap() {
      ObjectIterator iterator = this.long2ObjectOpenHashMap.values().iterator();

      while (iterator.hasNext()) {
         SchemBytephase schemBytephase = (SchemBytephase)iterator.next();
         schemBytephase.close();
      }

      this.long2ObjectOpenHashMap.clear();
   }
}
