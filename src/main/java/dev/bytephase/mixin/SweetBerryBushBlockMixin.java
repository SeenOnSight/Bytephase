package dev.bytephase.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.bytephase.BytePhase;
import dev.bytephase.module.movement.NoSlow;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(net.minecraft.block.SweetBerryBushBlock.class)
public abstract class SweetBerryBushBlockMixin {
    @WrapOperation(
            method = "onEntityCollision",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;slowMovement(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Vec3d;)V"
            )
    )
    private void bytephase$noBerrySlow(Entity entity, BlockState state, Vec3d multiplier,
                                       Operation<Void> original) {
        NoSlow noSlow = BytePhase.modules == null ? null : BytePhase.modules.noSlow();
        if (noSlow != null && noSlow.berryBush() && BytePhase.mc != null && entity == BytePhase.mc.player) {
            original.call(entity, state, new Vec3d(1.0, 1.0, 1.0));
            return;
        }
        original.call(entity, state, multiplier);
    }
}
