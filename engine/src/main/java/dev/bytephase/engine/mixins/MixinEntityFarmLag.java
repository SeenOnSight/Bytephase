package dev.bytephase.engine.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.bytephase.engine.categories.visuals.NoFarmLag;

@Mixin({Entity.class})
public class MixinEntityFarmLag {
   @WrapWithCondition(
      method = {"onSwimmingStart()V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/World;addParticleClient(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"
      )}
   )
   private boolean bytephase$noFarmLagSplash(World world, ParticleEffect particleEffect, double bytephasenoFarmValue, double bytephasenoFarmValue2, double bytephasenoFarmValue3, double bytephasenoFarmValue4, double bytephasenoFarmValue5, double bytephasenoFarmValue6) {
      if (!world.isClient()) {
         return true;
      } else {
         NoFarmLag module = NoFarmLag.getNoFarmLag();
         return module == null || !module.canBase((Entity)((net.minecraft.entity.Entity) (Object) (this)));
      }
   }
}
