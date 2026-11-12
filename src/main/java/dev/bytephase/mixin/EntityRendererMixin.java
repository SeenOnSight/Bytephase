package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.Nametags;
import dev.bytephase.module.render.PlayerEsp;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void bytephase$modelGlowColor(Entity entity, EntityRenderState state, float tickProgress,
                                          CallbackInfo ci) {
        PlayerEsp esp = BytePhase.modules == null ? null : BytePhase.modules.playerEsp();
        if (esp != null && entity instanceof LivingEntity living && esp.shouldModelGlow(living)) {
            state.outlineColor = esp.modelGlowColor(living);
        }
        // PlayerEntityRenderer overrides renderLabelIfPresent, so hiding only
        // in the base renderer leaves the player's vanilla name visible.
        Nametags nametags = BytePhase.modules == null ? null : BytePhase.modules.nametags();
        if (nametags != null && nametags.shouldHide(entity)) state.displayName = null;
    }

}
