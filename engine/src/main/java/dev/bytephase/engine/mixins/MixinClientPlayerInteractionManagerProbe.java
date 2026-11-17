package dev.bytephase.engine.mixins;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.bytephase.engine.categories.misc.Fastbreak;
import ai.auth.ModuleRegistry;

@Mixin({ClientPlayerInteractionManager.class})
public abstract class MixinClientPlayerInteractionManagerProbe {
   @Inject(
      method = {"breakBlock(Lnet/minecraft/util/math/BlockPos;)Z"},
      at = {@At("HEAD")}
   )
   private void bytephaseOnBreakBlock(BlockPos pos2, CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
      Fastbreak fb = (Fastbreak)ModuleRegistry.getModule(Fastbreak.class);
      if (fb != null && fb.isEnabled() && fb.isBase()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world != null) {
            BlockState original = mc.world.getBlockState(pos2);
            if (!original.isAir()) {
               fb.handlePaceToggle(pos2.toImmutable(), original);
            }
         }
      }
   }
}
