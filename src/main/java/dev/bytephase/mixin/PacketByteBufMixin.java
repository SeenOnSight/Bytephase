package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.misc.AntiPacketKick;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.PacketByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PacketByteBuf.class)
public abstract class PacketByteBufMixin {
    @ModifyArg(
            method = "readNbt(Lio/netty/buffer/ByteBuf;)Lnet/minecraft/nbt/NbtCompound;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/PacketByteBuf;readNbt(Lio/netty/buffer/ByteBuf;Lnet/minecraft/nbt/NbtSizeTracker;)Lnet/minecraft/nbt/NbtElement;"
            )
    )
    private static NbtSizeTracker bytephase$unlimitedNbt(NbtSizeTracker sizeTracker) {
        AntiPacketKick antiPacketKick = BytePhase.modules == null ? null : BytePhase.modules.antiPacketKick();
        if (antiPacketKick != null && antiPacketKick.unlimitedNbt()) {
            return NbtSizeTracker.ofUnlimitedBytes();
        }
        return sizeTracker;
    }
}
