package dev.bytephase.pvp.mixin;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import java.util.function.BooleanSupplier;
import dev.bytephase.pvp.event.impl.PacketReceiveEvent;
import dev.bytephase.pvp.event.impl.PacketReceiveNettyEvent;
import dev.bytephase.pvp.event.impl.PacketSendEvent;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Connection.class)
public abstract class MixinClientConnection {
    @Unique
    private final BooleanSupplier pvp$connectionActive = this::isConnected;

    @Shadow
    public abstract boolean isConnected();

    @Shadow
    public abstract PacketFlow getReceiving();

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void pvp$onPacketReceive(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        if (this.getReceiving() == PacketFlow.CLIENTBOUND) {
            if (PacketReceiveNettyEvent.dispatchSync(packet)) {
                ci.cancel();
            } else {
                PacketReceiveEvent.dispatchAsync(packet, this.pvp$connectionActive);
            }
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void pvp$onPacketSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (this.getReceiving() == PacketFlow.CLIENTBOUND) {
            if (PacketSendEvent.dispatchSync(packet)) {
                ci.cancel();
            } else {
                ServerRotation.noteSent(packet);
            }
        }
    }
}
