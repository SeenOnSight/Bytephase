package dev.bytephase.engine.mixins;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.categories.donutsmp.FakePay;
import dev.bytephase.engine.categories.donutsmp.FakeStats;
import dev.bytephase.engine.categories.visuals.SpawnerFinder;
import ai.auth.ModuleRegistry;

@Mixin({ClientPlayNetworkHandler.class})
public class MixinClientPlayNetworkHandler {
   @Inject(
      method = {"sendChatCommand(Ljava/lang/String;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephaseFakePay(String bytephaseFakeText, CallbackInfo callbackInfo) {
      FakePay fakePay = (FakePay)ModuleRegistry.getModule(FakePay.class);
      if (fakePay != null && fakePay.isEnabled() && fakePay.interceptPayCommand(bytephaseFakeText)) {
         callbackInfo.cancel();
      } else {
         FakeStats fakeStats = (FakeStats)ModuleRegistry.getModule(FakeStats.class);
         if (fakeStats != null && fakeStats.isEnabled() && fakeStats.interceptBalanceCommand(bytephaseFakeText)) {
            callbackInfo.cancel();
         }
      }
   }

   @Inject(
      method = {"onBlockEntityUpdate(Lnet/minecraft/network/packet/s2c/play/BlockEntityUpdateS2CPacket;)V"},
      at = {@At("TAIL")}
   )
   private void bytephaseSpawnerFinderBlockEntity(BlockEntityUpdateS2CPacket blockEntityUpdateS2CPacket, CallbackInfo callbackInfo) {
      SpawnerFinder.handleInstance(blockEntityUpdateS2CPacket.getPos());
   }
}
