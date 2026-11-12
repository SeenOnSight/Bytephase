package dev.bytephase.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.bytephase.BytePhase;
import dev.bytephase.module.movement.IceSpeed;
import dev.bytephase.module.movement.NoSlow;
import dev.bytephase.module.movement.Slippy;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Block.class)
public abstract class BlockMixin {
    @ModifyReturnValue(method = "getSlipperiness", at = @At("RETURN"))
    private float bytephase$slippy(float original) {
        Block self = (Block) (Object) this;
        IceSpeed iceSpeed = BytePhase.modules == null ? null : BytePhase.modules.iceSpeed();
        if (iceSpeed != null && iceSpeed.appliesTo(self)) {
            return iceSpeed.friction();
        }
        Slippy slippy = BytePhase.modules == null ? null : BytePhase.modules.slippy();
        if (slippy == null) {
            return original;
        }
        return slippy.friction();
    }

    @ModifyReturnValue(method = "getVelocityMultiplier", at = @At("RETURN"))
    private float bytephase$noSlowSoulSand(float original) {
        NoSlow noSlow = BytePhase.modules == null ? null : BytePhase.modules.noSlow();
        if (noSlow != null && noSlow.soulSand() && (Block) (Object) this == Blocks.SOUL_SAND) {
            return 1.0f;
        }
        return original;
    }
}
