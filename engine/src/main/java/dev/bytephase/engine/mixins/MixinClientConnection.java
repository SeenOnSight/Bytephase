package dev.bytephase.engine.mixins;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.utils.LightPacketCache;
import dev.bytephase.engine.utils.AutoVanish;
import dev.bytephase.engine.utils.ConnectionTimeUtils;
import dev.bytephase.engine.utils.InventoryPlayer;
import ai.auth.ModuleRegistry;

@Mixin({ClientConnection.class})
public class MixinClientConnection {
   @Inject(
      method = {"send(Lnet/minecraft/network/packet/Packet;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onPacketSend(Packet<?> packet, CallbackInfo callbackInfo) {
      InventoryPlayer event = new InventoryPlayer(packet, (ClientConnection)((net.minecraft.network.ClientConnection) (Object) (this)));
      ModuleRegistry.dispatchPacketSend(event);
      ConnectionTimeUtils.Outbound.handlePacket(event);
      if (event.isFlag()) {
         callbackInfo.cancel();
      }
   }

   @Inject(
      method = {"send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephaseOrderOutbound(Packet<?> packet, ChannelFutureListener channelFutureListener, boolean bytephaseOrderFlag, CallbackInfo callbackInfo) {
      if (ConnectionTimeUtils.Outbound.canEventLoop((ClientConnection)((net.minecraft.network.ClientConnection) (Object) (this)), packet, channelFutureListener, bytephaseOrderFlag)) {
         callbackInfo.cancel();
      }
   }

   @Inject(
      method = {"channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onPacketReceive(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo callbackInfo) {
      LightPacketCache.onLightPacket(packet);
      AutoVanish event = new AutoVanish(packet);
      ModuleRegistry.dispatchPacketReceive(event);
      if (event.isFlag()) {
         callbackInfo.cancel();
      }
   }

   @Inject(
      method = {"channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephaseHoldInbound(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo callbackInfo) {
      if (ConnectionTimeUtils.Inbound.canConnectionClient((ClientConnection)((net.minecraft.network.ClientConnection) (Object) (this)), packet)) {
         callbackInfo.cancel();
      }
   }
}
