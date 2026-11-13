package dev.bytephase.pvp.mixin;

import dev.bytephase.pvp.marlow.MarlowCrystal;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value={Level.class})
public abstract class MarlowLevelMixin {
    @ModifyVariable(method={"getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"}, at=@At(value="HEAD"), argsOnly=true)
    private Predicate<? super Entity> bytephase$marlow$hideKeptCrystals(Predicate<? super Entity> predicate) {
        if (!((Level)(Object)this).isClientSide() || !MarlowCrystal.active()) {
            return predicate;
        }
        return MarlowCrystal.get().keptCrystals().hide(predicate);
    }
}

