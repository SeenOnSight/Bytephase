package dev.bytephase.pvp.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public interface AccessorBlockOverlayMultiPlayerGameMode {
    @Accessor("destroyProgress")
    float pvp$blockOverlayDestroyProgress();

    @Accessor("destroyBlockPos")
    BlockPos pvp$blockOverlayDestroyPos();
}
