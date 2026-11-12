package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.combat.HitBoxes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @ModifyReturnValue(method = "getTargetingMargin", at = @At("RETURN"))
    private float bytephase$expandTargetingMargin(float original) {
        HitBoxes hitBoxes = BytePhase.modules == null ? null : BytePhase.modules.hitBoxes();
        if (hitBoxes == null || BytePhase.mc == null || BytePhase.mc.player == null) {
            return original;
        }
        if ((Object) this == BytePhase.mc.player || !((Object) this instanceof LivingEntity)) {
            return original;
        }
        // Targeting margin is used by entity raycasts. Expanding the entity's
        // actual AABB also expands collision/movement bounds, trapping the
        // local player and causing other entities to clip into the floor.
        return original + (float) hitBoxes.getExpand();
    }
}
