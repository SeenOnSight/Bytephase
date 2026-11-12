package dev.bytephase.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import dev.bytephase.BytePhase;
import dev.bytephase.module.combat.WTap;
import dev.bytephase.module.movement.NoFall;
import dev.bytephase.module.movement.NoSlow;
import dev.bytephase.module.movement.SafeWalk;
import dev.bytephase.util.SilentRotation;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.PlayerInput;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    /**
     * Two packet-level spoofs share this one wrap: NoFall's on-ground flag and
     * the aim modules' rotation. The rotation is written to the entity only for
     * the duration of the packet build, so the camera keeps the player's own
     * angles while the server receives the aimed ones.
     */
    @WrapMethod(method = "sendMovementPackets")
    private void bytephase$movementPackets(Operation<Void> original) {
        NoFall noFall = BytePhase.modules == null ? null : BytePhase.modules.noFall();
        Entity self = (Entity) (Object) this;
        boolean originalOnGround = self.isOnGround();
        boolean spoofGround = noFall != null && !originalOnGround
                && self.fallDistance > noFall.getThreshold();
        if (spoofGround) {
            self.setOnGround(true);
        }
        boolean spoofRotation = SilentRotation.isActive();
        float yaw = self.getYaw();
        float pitch = self.getPitch();
        if (spoofRotation) {
            self.setYaw(SilentRotation.yaw());
            self.setPitch(SilentRotation.pitch());
        }
        try {
            original.call();
        } finally {
            if (spoofRotation) {
                self.setYaw(yaw);
                self.setPitch(pitch);
            }
            if (spoofGround) {
                self.setOnGround(originalOnGround);
            }
        }
    }

    @Shadow
    public Input input;

    /** Sneak/crawl input is the only source of the walking slowdown here. */
    @ModifyReturnValue(method = "shouldSlowDown", at = @At("RETURN"))
    private boolean bytephase$noSlowSneak(boolean original) {
        NoSlow noSlow = BytePhase.modules == null ? null : BytePhase.modules.noSlow();
        return noSlow != null && noSlow.sneak() ? false : original;
    }

    /** Item use is applied inside applyMovementSpeedFactors; remove the factor. */
    @ModifyExpressionValue(
            method = "applyMovementSpeedFactors",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z")
    )
    private boolean bytephase$noSlowItems(boolean original) {
        NoSlow noSlow = BytePhase.modules == null ? null : BytePhase.modules.noSlow();
        return noSlow != null && noSlow.items() ? false : original;
    }

    @WrapMethod(method = "tickMovement")
    private void bytephase$prepareMovementInput(Operation<Void> original) {
        Input input = this.input;
        if (input == null || input.playerInput == null || BytePhase.modules == null) {
            original.call();
            return;
        }
        PlayerInput savedInput = input.playerInput;
        PlayerInput current = savedInput;

        // Evaluate SafeWalk against the physical movement input first, then
        // apply WTap's temporary mask. Keeping both rewrites in one injection
        // avoids relying on mixin ordering when they target the same point.
        SafeWalk safeWalk = BytePhase.modules.safeWalk();
        if (safeWalk != null && safeWalk.swapSneak() == input && !savedInput.sneak()) {
            current = new PlayerInput(savedInput.forward(), savedInput.backward(), savedInput.left(),
                    savedInput.right(), savedInput.jump(), true, savedInput.sprint());
        }

        WTap wTap = BytePhase.modules.wTap();
        if (wTap != null && wTap.masking()) {
            current = wTap.maskInput(current);
        }

        boolean changed = current != savedInput;
        if (changed) {
            input.playerInput = current;
        }
        try {
            original.call();
        } finally {
            if (changed) {
                input.playerInput = savedInput;
            }
        }
    }
}
