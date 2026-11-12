package dev.bytephase.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
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

    @Invoker("sendSequencedPacket")
    void bytephase$sendSequencedPacket(ClientWorld world, SequencedPacketCreator packetCreator);
}
