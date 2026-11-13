package dev.bytephase.pvp.mixin;

import dev.bytephase.pvp.event.impl.KeyInputEvent;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class MixinKeyboardHandler {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void bytephase$pvpKey(long window, int action, KeyEvent input, CallbackInfo ci) {
        if (KeyInputEvent.dispatch(input.key(), input.scancode(), action, input.modifiers())) ci.cancel();
    }
}
