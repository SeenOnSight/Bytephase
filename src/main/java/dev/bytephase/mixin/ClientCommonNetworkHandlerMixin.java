package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.misc.CoordLogger;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.DisconnectionInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public abstract class ClientCommonNetworkHandlerMixin {
    @Inject(method = "onDisconnected", at = @At("HEAD"))
    private void bytephase$onDisconnected(DisconnectionInfo info, CallbackInfo ci) {
        CoordLogger logger = BytePhase.modules == null ? null : BytePhase.modules.get(CoordLogger.class);
        var client = BytePhase.mc;
        if (logger != null && logger.isEnabled() && client != null) {
            // PacketListener callbacks may run on Netty. Keep player/world and
            // file work on the client thread; CoordLogger caches the last
            // location so this remains valid after disconnect teardown.
            client.execute(() -> {
                if (logger.isEnabled()) {
                    logger.onDisconnect();
                }
            });
        }
    }
}
