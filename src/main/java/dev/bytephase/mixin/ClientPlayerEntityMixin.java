package dev.bytephase.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import dev.bytephase.BytePhase;
import dev.bytephase.module.combat.WTap;
import dev.bytephase.module.movement.SafeWalk;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @Shadow
    public Input input;

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
