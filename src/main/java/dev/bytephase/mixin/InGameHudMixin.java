package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.ArmorHud;
import dev.bytephase.module.render.Crosshair;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void bytephase$hideVanillaCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Crosshair crosshair = BytePhase.modules == null ? null : BytePhase.modules.crosshair();
        if (crosshair != null && crosshair.hideVanilla()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private static void bytephase$hideVanillaArmor(DrawContext context, PlayerEntity player,
                                                   int x, int y, int width, int height, CallbackInfo ci) {
        ArmorHud armorHud = BytePhase.modules == null ? null : BytePhase.modules.armorHud();
        if (armorHud != null && armorHud.hideVanilla()) {
            ci.cancel();
        }
    }
}
