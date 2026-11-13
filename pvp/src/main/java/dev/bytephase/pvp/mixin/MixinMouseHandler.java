package dev.bytephase.pvp.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.bytephase.pvp.event.impl.MouseInputEvent;
import dev.bytephase.pvp.module.ModuleRegistry;
import dev.bytephase.pvp.module.impl.combat.AimAssistModule;
import dev.bytephase.pvp.module.impl.combat.TriggerbotModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void bytephase$pvpMouse(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (MouseInputEvent.dispatch(window, info.button(), action, info.modifiers())) ci.cancel();
    }

    // Vanilla calls turnPlayer each focused, mouse-grabbed frame, even with
    // zero mouse deltas. Apply assistance here once without an input-idle gate.
    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void bytephase$pvpAim(LocalPlayer player, double yaw, double pitch, Operation<Void> original,
                                  @Local(argsOnly = true) double movementTime) {
        var module = ModuleRegistry.get(AimAssistModule.class);
        if (module != null && module.isEnabled() && Minecraft.getInstance().screen == null) {
            double[] nudge = module.nudge(player, yaw, pitch, movementTime);
            yaw += nudge[0];
            pitch += nudge[1];
        }
        original.call(player, yaw, pitch);
    }

    @Inject(method = "turnPlayer", at = @At("TAIL"))
    private void bytephase$pvpTriggerFrame(double movementTime, CallbackInfo ci) {
        var trigger = ModuleRegistry.get(TriggerbotModule.class);
        if (trigger != null && trigger.isEnabled()) trigger.onFrame();
    }
}
