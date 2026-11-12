package dev.bytephase.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.util.math.BlockPos;

@Mixin(ClientPlayerInteractionManager.class)
public interface ClientPlayerInteractionManagerAccessor {
    @Accessor("currentBreakingPos")
    BlockPos bytephase$breakingPos();

    @Accessor("currentBreakingProgress")
    float bytephase$breakingProgress();

    @Accessor("breakingBlock")
    boolean bytephase$isBreaking();
}
