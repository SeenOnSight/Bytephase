package dev.bytephase.pvp.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.bytephase.pvp.event.impl.PlayerMoveEvent;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {
    @WrapMethod(method = "move")
    private void pvp$onMove(MoverType type, Vec3 movement, Operation<Void> original) {
        if (!PlayerMoveEvent.TYPE.hasListeners()) {
            original.call(type, movement);
        } else {
            PlayerMoveEvent.move(movement, changed -> original.call(type, changed));
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void pvp$walkServerRotation(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer)(Object)this;
        if (Minecraft.getInstance().player == self) {
            ServerRotation.applyForMovement(self);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void pvp$finishServerRotation(CallbackInfo ci) {
        ServerRotation.finishTick((LocalPlayer)(Object)this);
    }

    

    

    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void pvp$swapOutServerRotation(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer)(Object)this;
        if (Minecraft.getInstance().player == self) {
            ServerRotation.afterSend(self);
        }
    }
}
