package dev.bytephase.engine.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.hud.debug.PlayerPositionDebugHudEntry;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.bytephase.engine.categories.misc.FakeCoordinates;

@Mixin({PlayerPositionDebugHudEntry.class})
public abstract class MixinPlayerPositionDebugHudEntryFakeCoords {
   @ModifyExpressionValue(
      method = {"render(Lnet/minecraft/client/gui/hud/debug/DebugHudLines;Lnet/minecraft/world/World;Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/chunk/WorldChunk;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/entity/Entity;getX()D"
      )}
   )
   private double bytephase$fakeX(double bytephasefakeXValue) {
      return FakeCoordinates.getX(bytephasefakeXValue);
   }

   @ModifyExpressionValue(
      method = {"render(Lnet/minecraft/client/gui/hud/debug/DebugHudLines;Lnet/minecraft/world/World;Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/chunk/WorldChunk;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/entity/Entity;getZ()D"
      )}
   )
   private double bytephase$fakeZ(double bytephasefakeZValue) {
      return FakeCoordinates.getZ(bytephasefakeZValue);
   }

   @ModifyExpressionValue(
      method = {"render(Lnet/minecraft/client/gui/hud/debug/DebugHudLines;Lnet/minecraft/world/World;Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/chunk/WorldChunk;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/entity/Entity;getBlockPos()Lnet/minecraft/util/math/BlockPos;"
      )}
   )
   private BlockPos bytephase$fakeBlock(BlockPos pos2) {
      return FakeCoordinates.getAdd(pos2);
   }
}
