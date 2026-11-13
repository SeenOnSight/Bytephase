package dev.bytephase.mixin;

import dev.bytephase.module.render.Performance;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
        at = @At("HEAD"), cancellable = true)
    private void bytephase$skipParticleCreation(ParticleEffect effect, double x, double y, double z,
            double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> cir) {
        if (!Performance.canCreateParticle(x, y, z)) cir.setReturnValue(null);
    }

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V",
        at = @At("HEAD"), cancellable = true)
    private void bytephase$limitParticles(Particle particle, CallbackInfo ci) {
        if (!Performance.allowParticle(particle)) ci.cancel();
    }
}
