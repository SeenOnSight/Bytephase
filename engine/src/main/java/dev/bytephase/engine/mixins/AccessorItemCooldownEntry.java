package dev.bytephase.engine.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(
   targets = {"net/minecraft/entity/player/ItemCooldownManager$Entry"}
)
public interface AccessorItemCooldownEntry {
   @Accessor("startTick")
   int bytephaseStartTick();

   @Accessor("endTick")
   int bytephaseEndTick();
}
