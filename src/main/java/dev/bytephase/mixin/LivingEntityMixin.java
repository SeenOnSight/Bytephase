package dev.bytephase.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.bytephase.BytePhase;
import dev.bytephase.module.misc.SlowSwing;
import dev.bytephase.module.movement.HighJump;
import dev.bytephase.module.movement.LongJump;
import dev.bytephase.module.movement.NoJumpDelay;
import dev.bytephase.module.movement.Step;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    private int jumpingCooldown;

    @Inject(method = "getStepHeight", at = @At("RETURN"), cancellable = true)
    private void bytephase$stepHeight(CallbackInfoReturnable<Float> cir) {
        Step step = BytePhase.modules == null ? null : BytePhase.modules.step();
        if (step != null && BytePhase.mc != null && BytePhase.mc.player == (Object) this) {
            cir.setReturnValue(step.getStepHeight());
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void bytephase$noJumpDelay(CallbackInfo ci) {
        NoJumpDelay module = BytePhase.modules == null ? null : BytePhase.modules.noJumpDelay();
        if (module == null) {
            return;
        }
        if (BytePhase.mc == null || BytePhase.mc.player == null || (Object) this != BytePhase.mc.player) {
            return;
        }
        this.jumpingCooldown = 0;
    }

    @ModifyReturnValue(method = "getHandSwingDuration", at = @At("RETURN"))
    private int bytephase$slowSwing(int original) {
        SlowSwing slowSwing = BytePhase.modules == null ? null : BytePhase.modules.slowSwing();
        if (slowSwing == null) {
            return original;
        }
        if (BytePhase.mc == null || BytePhase.mc.player != (Object) this) {
            return original;
        }
        return slowSwing.modify(original);
    }

    @Inject(method = "jump", at = @At("RETURN"))
    private void bytephase$highJump(CallbackInfo ci) {
        HighJump module = BytePhase.modules == null ? null : BytePhase.modules.highJump();
        if (module == null) {
            return;
        }
        if (BytePhase.mc == null || BytePhase.mc.player == null || (Object) this != BytePhase.mc.player) {
            return;
        }
        Entity self = (Entity) (Object) this;
        Vec3d v = self.getVelocity();
        self.setVelocityClient(new Vec3d(v.x, v.y * module.getBoost(), v.z));
    }

    @Inject(method = "jump", at = @At("RETURN"))
    private void bytephase$longJump(CallbackInfo ci) {
        LongJump module = BytePhase.modules == null ? null : BytePhase.modules.longJump();
        if (module == null) {
            return;
        }
        if (BytePhase.mc == null || BytePhase.mc.player == null || (Object) this != BytePhase.mc.player) {
            return;
        }
        if (!BytePhase.mc.options.forwardKey.isPressed()) {
            return;
        }
        Entity self = (Entity) (Object) this;
        Vec3d v = self.getVelocity();
        double multiplier = 1.0 + module.getBoost();
        self.setVelocityClient(new Vec3d(v.x * multiplier, v.y, v.z * multiplier));
    }
}
