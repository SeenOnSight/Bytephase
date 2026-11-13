package dev.bytephase.pvp.mixin;

import dev.bytephase.pvp.util.pvp.InputControl;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(KeyboardInput.class)
public abstract class MixinMovementCorrectionKeyboardInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void pvp$shapeInput(CallbackInfo ci) {
        ClientInput input = (ClientInput)(Object)this;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.input == input) {
            Input keys = input.keyPresses;
            Input corrected = InputControl.shape(player, ServerRotation.correctInput(keys));
            if (corrected != keys) {
                input.keyPresses = corrected;
                ((AccessorMovementClientInput)input)
                    .pvp$setMoveVector(
                        new Vec2(impulse(corrected.left(), corrected.right()), impulse(corrected.forward(), corrected.backward())).normalized()
                    );
            }
        }
    }

    private static float impulse(boolean positive, boolean negative) {
        return positive == negative ? 0.0F : (positive ? 1.0F : -1.0F);
    }
}
