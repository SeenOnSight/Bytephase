package dev.bytephase.engine.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.hud.debug.LookingAtBlockDebugHudEntry;
import net.minecraft.client.gui.hud.debug.LookingAtFluidDebugHudEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.bytephase.engine.categories.misc.FakeCoordinates;

@Mixin({LookingAtBlockDebugHudEntry.class, LookingAtFluidDebugHudEntry.class})
public abstract class MixinLookingAtDebugHudEntryFakeCoords {
   @ModifyExpressionValue(
      method = {"render(Lnet/minecraft/client/gui/hud/debug/DebugHudLines;Lnet/minecraft/world/World;Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/chunk/WorldChunk;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/util/math/BlockPos;getX()I"
      )}
   )
   private int bytephase$fakeX(int bytephasefakeXValue) {
      return FakeCoordinates.getInstance(bytephasefakeXValue);
   }

   @ModifyExpressionValue(
      method = {"render(Lnet/minecraft/client/gui/hud/debug/DebugHudLines;Lnet/minecraft/world/World;Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/chunk/WorldChunk;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/util/math/BlockPos;getZ()I"
      )}
   )
   private int bytephase$fakeZ(int bytephasefakeZValue) {
      return FakeCoordinates.getInstanceForInt(bytephasefakeZValue);
   }
}
