package dev.bytephase.engine.mixins;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.categories.visuals.NoExplosions;
import ai.auth.ModuleRegistry;

@Mixin({ClientWorld.class})
public class MixinWorldRenderer {
   @Inject(
      method = {"syncWorldEvent(Lnet/minecraft/entity/Entity;ILnet/minecraft/util/math/BlockPos;I)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onProcessWorldEvent(Entity entity, int worldEventValue, BlockPos pos2, int worldEventValue2, CallbackInfo callbackInfo) {
      NoExplosions module = (NoExplosions)ModuleRegistry.getModule(NoExplosions.class);
      if (module != null && module.isEnabled()) {
         if (worldEventValue == 2003 && module.isOtherExplosions()) {
            callbackInfo.cancel();
         }
      }
   }
}
