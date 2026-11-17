package dev.bytephase.engine.mixins;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import dev.bytephase.engine.categories.visuals.CustomFlame;

@Mixin({AbstractFireBlock.class})
public class MixinAbstractFireBlock {
   @Redirect(
      method = {"randomDisplayTick(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/random/Random;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/World;addParticleClient(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"
      )
   )
   private void bytephase_suppressFireSmoke(World world, ParticleEffect particleEffect, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
      if (!CustomFlame.isBaseRestyleWorldFire()) {
         world.addParticleClient(particleEffect, x, y, z, velocityX, velocityY, velocityZ);
      }
   }
}
