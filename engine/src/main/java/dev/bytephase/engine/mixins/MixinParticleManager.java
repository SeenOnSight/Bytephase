package dev.bytephase.engine.mixins;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.bytephase.engine.categories.visuals.NoExplosions;
import dev.bytephase.engine.categories.visuals.NoFarmLag;
import dev.bytephase.engine.categories.visuals.Particles;
import ai.auth.ModuleRegistry;

@Mixin({ParticleManager.class})
public class MixinParticleManager {
   @Inject(
      method = {"addEmitter(Lnet/minecraft/entity/Entity;Lnet/minecraft/particle/ParticleEffect;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephase$hideVanillaCrits2(Entity entity, ParticleEffect particleEffect, CallbackInfo callbackInfo) {
      if (bytephase$isVanillaCrit(particleEffect)) {
         callbackInfo.cancel();
      }
   }

   @Inject(
      method = {"addEmitter(Lnet/minecraft/entity/Entity;Lnet/minecraft/particle/ParticleEffect;I)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephase$hideVanillaCrits3(Entity entity, ParticleEffect particleEffect, int bytephasehideVanillaValue, CallbackInfo callbackInfo) {
      if (bytephase$isVanillaCrit(particleEffect)) {
         callbackInfo.cancel();
      }
   }

   @Unique
   private static boolean bytephase$isVanillaCrit(ParticleEffect particleEffect) {
      if (particleEffect == null) {
         return false;
      } else if (particleEffect.getType() != ParticleTypes.CRIT && particleEffect.getType() != ParticleTypes.ENCHANTED_HIT) {
         return false;
      } else {
         Particles particles = (Particles)ModuleRegistry.getModule(Particles.class);
         return particles != null && particles.isBase();
      }
   }

   @Inject(
      method = {"addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAddParticle(
      ParticleEffect particleEffect, double particleValue, double particleValue2, double particleValue3, double particleValue4, double particleValue5, double particleValue6, CallbackInfoReturnable<Particle> callbackInfoReturnable
   ) {
      NoFarmLag farmLag = NoFarmLag.getNoFarmLag();
      if (farmLag != null && farmLag.isBase() && particleEffect.getType() == ParticleTypes.NOTE) {
         callbackInfoReturnable.setReturnValue(null);
      } else if (farmLag == null
         || !farmLag.isExplosions()
         || particleEffect.getType() != ParticleTypes.EXPLOSION && particleEffect.getType() != ParticleTypes.EXPLOSION_EMITTER) {
         NoExplosions module = (NoExplosions)ModuleRegistry.getModule(NoExplosions.class);
         if (module != null && module.isEnabled()) {
            if (particleEffect.getType() == ParticleTypes.EXPLOSION || particleEffect.getType() == ParticleTypes.EXPLOSION_EMITTER) {
               if (module.isBase()) {
                  callbackInfoReturnable.setReturnValue(null);
                  return;
               }

               if (module.isOtherExplosions()) {
                  callbackInfoReturnable.setReturnValue(null);
               }
            }
         }
      } else {
         callbackInfoReturnable.setReturnValue(null);
      }
   }
}
