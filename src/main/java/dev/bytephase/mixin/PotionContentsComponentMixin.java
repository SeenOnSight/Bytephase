package dev.bytephase.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.bytephase.BytePhase;
import dev.bytephase.module.render.OldPotions;
import net.minecraft.component.type.PotionContentsComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PotionContentsComponent.class)
public abstract class PotionContentsComponentMixin {
    @ModifyReturnValue(method = "getColor", at = @At("RETURN"))
    private int bytephase$oldPotionColor(int original) {
        OldPotions oldPotions = BytePhase.modules == null ? null : BytePhase.modules.oldPotions();
        if (oldPotions == null || !oldPotions.isEnabled()) {
            return original;
        }
        PotionContentsComponent contents = (PotionContentsComponent) (Object) this;
        return PotionContentsComponent.mixColors(contents.getEffects())
                .orElse(PotionContentsComponent.EFFECTLESS_COLOR);
    }
}
