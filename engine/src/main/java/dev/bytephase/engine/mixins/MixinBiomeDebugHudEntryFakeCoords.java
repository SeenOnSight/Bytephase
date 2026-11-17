package dev.bytephase.engine.mixins;

import net.minecraft.client.gui.hud.debug.BiomeDebugHudEntry;
import net.minecraft.client.gui.hud.debug.DebugHudLines;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.categories.misc.FakeCoordinates;

@Mixin({BiomeDebugHudEntry.class})
public abstract class MixinBiomeDebugHudEntryFakeCoords {
   @Inject(
      method = {"render(Lnet/minecraft/client/gui/hud/debug/DebugHudLines;Lnet/minecraft/world/World;Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/chunk/WorldChunk;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephase$hideBiome(DebugHudLines debugHudLines, World world, WorldChunk worldChunk, WorldChunk worldChunk2, CallbackInfo callbackInfo) {
      if (FakeCoordinates.isBaseInstance()) {
         callbackInfo.cancel();
      }
   }
}
