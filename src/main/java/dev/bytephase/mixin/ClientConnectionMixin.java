package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.misc.AntiPacketKick;
import dev.bytephase.module.misc.PacketLogger;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.TimeoutException;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void bytephase$packetHandlers(Packet<?> packet, CallbackInfo ci) {
        if (BytePhase.modules == null) {
            return;
        }
        PacketLogger logger = BytePhase.modules.packetLogger();
        if (logger != null) {
            logger.log(packet);
        }
        if (BytePhase.modules.packetSend(packet)) {
            ci.cancel();
            return;
        }
    }

    /** Runs before the packet reaches its listener, so modules can drop it. */
    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/ClientConnection;handlePacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;)V",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true
    )
    private void bytephase$receive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (BytePhase.modules != null && BytePhase.modules.packetReceive(packet)) {
            ci.cancel();
        }
    }

    @Inject(method = "exceptionCaught", at = @At("HEAD"), cancellable = true)
    private void bytephase$antiPacketKick(ChannelHandlerContext context, Throwable throwable, CallbackInfo ci) {
        AntiPacketKick antiPacketKick = BytePhase.modules == null ? null : BytePhase.modules.antiPacketKick();
        if (antiPacketKick == null || !antiPacketKick.catchExceptions()) {
            return;
        }
        // Drop recoverable packet/decoder exceptions only. Errors include
        // fatal VM conditions (for example OutOfMemoryError); suppressing
        // those here would hide the failure while leaving the connection in
        // an undefined state. Timeouts remain owned by vanilla's disconnect
        // handling as well.
        if (!(throwable instanceof Exception) || throwable instanceof TimeoutException) {
            return;
        }
        if (antiPacketKick.logExceptions()) {
            BytePhase.LOGGER.warn("Caught packet exception", throwable);
        }
        ci.cancel();
    }
}
