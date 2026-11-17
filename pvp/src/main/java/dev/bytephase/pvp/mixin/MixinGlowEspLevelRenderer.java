package dev.bytephase.pvp.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.bytephase.pvp.CombatIntegration;
import dev.bytephase.pvp.event.impl.Render3DEvent;
import dev.bytephase.pvp.module.impl.render.ShaderEspModule;
import dev.bytephase.pvp.util.ShaderOverlay;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.fog.FogRenderer.FogMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uses world render timing for entity masks and shader-pack fallbacks. */
@Mixin(LevelRenderer.class)
public abstract class MixinGlowEspLevelRenderer {
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/debug/DebugRenderer;emitGizmos(Lnet/minecraft/client/renderer/culling/Frustum;DDDF)V"),
            index = 0)
    private Frustum bytephase$dispatchWorld(Frustum frustum) {
        if (!Render3DEvent.TYPE.hasListeners()) return frustum;
        var services = CombatIntegration.services();
        if (services == null || !services.isCleanCapture()) {
            try (var capture = ShaderOverlay.capture()) {
                float delta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
                Render3DEvent.dispatch(delta, frustum);
            }
        }
        return frustum;
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void bytephase$keepGlowState(Entity entity, float partialTick,
            CallbackInfoReturnable<EntityRenderState> cir) {
        ShaderEspModule.extracted(entity, cir.getReturnValue());
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void bytephase$composeGlow(CallbackInfo ci,
            @Local(argsOnly = true) GpuBufferSlice fog,
            @Local(argsOnly = true) Camera camera,
            @Local(argsOnly = true, ordinal = 0) Matrix4f viewRotation,
            @Local(argsOnly = true, ordinal = 1) Matrix4f projection) {
        var services = CombatIntegration.services();
        if (services != null && services.isCleanCapture()) {
            ShaderEspModule.discardFrame();
            ShaderOverlay.discard();
            return;
        }
        if (ShaderOverlay.pending()) {
            GpuBufferSlice onTopFog = camera.getFluidInCamera() == FogType.NONE ? fog
                    : ((AccessorBlockSelectionGameRenderer)Minecraft.getInstance().gameRenderer)
                        .pvp$blockSelectionFogRenderer().getBuffer(FogMode.NONE);
            ShaderOverlay.render(camera, viewRotation, projection, fog, onTopFog);
        }
        ShaderEspModule.afterLevel(camera, viewRotation, projection);
    }
}
