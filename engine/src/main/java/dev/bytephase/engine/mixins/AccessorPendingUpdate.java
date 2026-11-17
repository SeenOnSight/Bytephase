package dev.bytephase.engine.mixins;

import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(
   targets = {"net/minecraft/client/network/PendingUpdateManager$PendingUpdate"}
)
public interface AccessorPendingUpdate {
   @Accessor("blockState")
   BlockState bytephaseServerState();

   @Accessor("sequence")
   int bytephaseSequence();
}
