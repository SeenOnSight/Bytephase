package dev.bytephase.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.bytephase.BytePhase;
import dev.bytephase.render.RenderProjection;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @WrapOperation(method = "updateChunks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;"))
    private Object bytephase$chunkUpdatePolicy(net.minecraft.client.option.SimpleOption<?> option,
                                               Operation<Object> original) {
        Object value = original.call(option);
        return BytePhase.mc != null && option == BytePhase.mc.options.getChunkBuilderMode()
                ? dev.bytephase.module.render.Performance.chunkMode((net.minecraft.client.render.ChunkBuilderMode) value)
                : value;
    }

    /**
     * Actual 1.21.11 signature: (allocator, tickCounter, renderBlockOutline,
     * camera, viewMatrix, projectionMatrix, cullProjectionMatrix, fog,
     * fogColor, renderSky). The view matrix contains only the camera rotation;
     * projection already includes nausea/view-bob. Geometry is made
     * camera-relative by subtracting {@code cameraPos} in double precision.
     *
     * <p>Use the camera's own interpolation progress rather than the tick
     * counter: GameRenderer may pass a different (frozen) progress when it
     * updates the camera, and entity boxes must interpolate with exactly the
     * same alpha or they drift relative to the camera.</p>
     */
    // Entity distance scaling can be shorter than ESP's advertised view range.
    // Only extend it for selected through-wall targets that intersect the view.
    @WrapOperation(method = "fillEntityRenderStates", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/EntityRenderManager;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z"))
    private boolean bytephase$renderDistantEspTarget(EntityRenderManager manager, Entity entity, Frustum frustum,
                                                       double x, double y, double z, Operation<Boolean> original) {
        if (dev.bytephase.module.render.Performance.cullItem(entity, x, y, z)) return false;
        if (original.call(manager, entity, frustum, x, y, z)) return true;
        var esp = BytePhase.modules == null ? null : BytePhase.modules.playerEsp();
        return esp != null && esp.shouldRenderHiddenModel(entity)
                && frustum.isVisible(entity.getBoundingBox().expand(0.5));
    }

    // Vanilla skips entities in occluded chunk sections before their render
    // states are built. A no-depth model layer cannot recover those entities.
    // Preserve frustum culling and only bypass this section check for selected
    // through-wall model ESP targets.
    @WrapOperation(method = "fillEntityRenderStates", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;isRenderingReady(Lnet/minecraft/util/math/BlockPos;)Z"))
    private boolean bytephase$renderHiddenEspTarget(WorldRenderer renderer, BlockPos pos,
                                                      Operation<Boolean> original, @Local Entity entity) {
        if (original.call(renderer, pos)) return true;
        var esp = BytePhase.modules == null ? null : BytePhase.modules.playerEsp();
        return esp != null && esp.shouldRenderHiddenModel(entity);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void bytephase$captureMatrices(ObjectAllocator allocator, RenderTickCounter tickCounter,
                                           boolean renderBlockOutline, Camera camera,
                                           Matrix4f viewMatrix, Matrix4f projectionMatrix,
                                           Matrix4f cullProjectionMatrix, GpuBufferSlice bufferSlice,
                                           Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        if (BytePhase.modules == null || !BytePhase.modules.needsProjection()) {
            return;
        }
        RenderProjection.onFrame(projectionMatrix, viewMatrix, camera.getRotation(), camera.getCameraPos(),
                camera.getLastTickProgress());
    }
}
