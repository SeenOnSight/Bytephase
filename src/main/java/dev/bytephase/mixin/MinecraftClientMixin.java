package dev.bytephase.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.bytephase.BytePhase;
import dev.bytephase.util.AttackLimiter;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Unique
    private static final AtomicInteger bytephase$screenThreadWarnings = new AtomicInteger();

    /** Wrap before HEAD hooks: imported-screen adaptation must also run on the client thread. */
    @WrapMethod(method = "setScreen")
    private void bytephase$screenOnClientThread(Screen screen, Operation<Void> original) {
        MinecraftClient client = (MinecraftClient)(Object)this;
        if (!client.isOnThread()) {
            if (bytephase$screenThreadWarnings.getAndIncrement() < 16) {
                BytePhase.LOGGER.warn("Queuing screen change for {} from thread {}",
                    screen == null ? "<close>" : screen.getClass().getName(),
                    Thread.currentThread().getName(), new IllegalStateException("Off-thread screen request"));
            }
            // Never publish currentScreen or run init()/removed() on the requesting worker.
            client.execute(() -> client.setScreen(screen));
            return;
        }
        original.call(screen);
    }

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private net.minecraft.client.gui.screen.Screen bytephase$nativeImportedScreens(net.minecraft.client.gui.screen.Screen screen) {
        return dev.bytephase.ui.NativeImportedUi.replace(screen);
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void bytephase$enforceNativeScreens(net.minecraft.client.gui.screen.Screen requested, CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.currentScreen != null && client.currentScreen.getClass().getName().startsWith("dev.bytephase.engine."))
            client.setScreen(dev.bytephase.ui.NativeImportedUi.replace(client.currentScreen));
    }
    @Inject(method = "tick", at = @At("HEAD"))
    private void bytephase$beginAttackTick(CallbackInfo ci) {
        AttackLimiter.beginTick();
    }
}
