package dev.bytephase.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.bytephase.render.HudLayout;
import dev.bytephase.BytePhase;
import dev.bytephase.module.render.ArmorHud;
import dev.bytephase.module.render.Crosshair;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @ModifyExpressionValue(method = {"render", "renderStatusEffectOverlay"}, at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/MinecraftClient;currentScreen:Lnet/minecraft/client/gui/screen/Screen;"))
    private Screen bytephase$privateScreenForHud(Screen screen) {
        return dev.bytephase.render.capture.StreamProof.screenForRender(screen);
    }

    @WrapMethod(method = "renderChat")
    private void bytephase$moveChat(DrawContext context, RenderTickCounter counter, Operation<Void> original) {
        HudLayout.draw("vanilla:Chat", context, 0, Math.max(0, context.getScaledWindowHeight() - 180), 320, 150, () -> original.call(context, counter));
    }
    @WrapMethod(method = "renderArmor")
    private static void bytephase$moveArmor(DrawContext context, PlayerEntity player, int x, int y, int width, int height, Operation<Void> original) {
        HudLayout.draw("vanilla:Armor bar", context, x, y, 81, 10, () -> original.call(context, player, x, y, width, height));
    }
    @WrapMethod(method = "renderFood")
    private void bytephase$moveFood(DrawContext context, PlayerEntity player, int top, int right, Operation<Void> original) {
        HudLayout.draw("vanilla:Hunger", context, right - 81, top, 81, 10, () -> original.call(context, player, top, right));
    }
    @WrapMethod(method = "renderAirBubbles")
    private void bytephase$moveAir(DrawContext context, PlayerEntity player, int heartRows, int top, int right, Operation<Void> original) {
        HudLayout.draw("vanilla:Air bubbles", context, right - 81, top - 10, 81, 10, () -> original.call(context, player, heartRows, top, right));
    }
    @WrapMethod(method = "renderHealthBar")
    private void bytephase$moveHealth(DrawContext context, PlayerEntity player, int x, int y, int lines, int regenerating,
            float maxHealth, int health, int displayedHealth, int absorption, boolean blinking, Operation<Void> original) {
        HudLayout.draw("vanilla:Health", context, x, y - 10, 81, 20,
            () -> original.call(context, player, x, y, lines, regenerating, maxHealth, health, displayedHealth, absorption, blinking));
    }
    @WrapMethod(method = "renderStatusEffectOverlay")
    private void bytephase$moveEffects(DrawContext context, RenderTickCounter counter, Operation<Void> original) {
        HudLayout.draw("vanilla:Potion icons", context, context.getScaledWindowWidth() - 124, 1, 124, 55, () -> original.call(context, counter));
    }
    @WrapMethod(method = "renderHotbar")
    private void bytephase$moveHotbar(DrawContext context, RenderTickCounter counter, Operation<Void> original) {
        HudLayout.draw("vanilla:Hotbar", context, context.getScaledWindowWidth() / 2f - 91, context.getScaledWindowHeight() - 22, 182, 22, () -> original.call(context, counter));
    }
    @WrapMethod(method = "renderCrosshair")
    private void bytephase$moveCrosshair(DrawContext context, RenderTickCounter counter, Operation<Void> original) {
        HudLayout.draw("vanilla:Crosshair", context, context.getScaledWindowWidth() / 2f - 8, context.getScaledWindowHeight() / 2f - 8, 16, 16, () -> original.call(context, counter));
    }
    @WrapMethod(method = "renderBossBarHud")
    private void bytephase$moveBossBars(DrawContext context, RenderTickCounter counter, Operation<Void> original) {
        HudLayout.draw("vanilla:Boss bars", context, context.getScaledWindowWidth() / 2f - 91, 12, 182, 24, () -> original.call(context, counter));
    }
    @WrapMethod(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V")
    private void bytephase$moveScoreboard(DrawContext context, RenderTickCounter counter, Operation<Void> original) {
        HudLayout.draw("vanilla:Scoreboard", context, context.getScaledWindowWidth() - 124, context.getScaledWindowHeight() / 2f - 45, 124, 90, () -> original.call(context, counter));
    }
    @WrapMethod(method = "renderOverlayMessage")
    private void bytephase$moveActionBar(DrawContext context, RenderTickCounter counter, Operation<Void> original) {
        HudLayout.draw("vanilla:Action bar", context, context.getScaledWindowWidth() / 2f - 100, context.getScaledWindowHeight() - 72, 200, 12, () -> original.call(context, counter));
    }
    @WrapMethod(method = "renderTitleAndSubtitle")
    private void bytephase$moveTitles(DrawContext context, RenderTickCounter counter, Operation<Void> original) {
        HudLayout.draw("vanilla:Titles", context, context.getScaledWindowWidth() / 2f - 120, context.getScaledWindowHeight() / 2f - 25, 240, 50, () -> original.call(context, counter));
    }
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
