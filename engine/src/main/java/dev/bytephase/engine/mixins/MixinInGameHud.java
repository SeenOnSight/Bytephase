package dev.bytephase.engine.mixins;

import dev.bytephase.engine.gui.ScreenSoundsUtils;
import dev.bytephase.engine.gui.TickProgress;
import dev.bytephase.engine.render.ClampMatrixUtils;
import dev.bytephase.engine.render.BytephaseRoundedUtils;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.categories.misc.CustomCrosshair;
import dev.bytephase.engine.categories.movement.Freecam;
import dev.bytephase.engine.utils.HotbarHud;
import dev.bytephase.engine.categories.visuals.ShowHUD;
import ai.auth.Module;
import ai.auth.ModuleRegistry;

@Mixin({InGameHud.class})
public class MixinInGameHud {
   @Unique
   private boolean bytephaseTextLifted;

   @Inject(
      at = {@At("HEAD")},
      method = {"render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"},
      cancellable = true
   )
   private void onRender(DrawContext context, RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
      ShowHUD.updateTotemsHotbar();
      if (ScreenSoundsUtils.isScreenGuiApiOpen()) {
         ClampMatrixUtils.renderFlag(context, renderTickCounter.getTickProgress(false));
         callbackInfo.cancel();
      } else if (ScreenSoundsUtils.isNoScreenOpen()) {
         TickProgress event = new TickProgress(context, renderTickCounter.getTickProgress(false));
         BytephaseRoundedUtils.update();

         try {
            ModuleRegistry.dispatchRender2D(event);
         } finally {
            BytephaseRoundedUtils.updateBufferCloseCount();
         }
      }
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"renderChat(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"},
      cancellable = true
   )
   private void onRenderChat(DrawContext drawContext, RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
      if (ScreenSoundsUtils.isGuiClickApiOpen()) {
         callbackInfo.cancel();
      }
   }

   @Inject(
      at = {@At("HEAD")},
      cancellable = true,
      method = {"renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", "renderBossBarHud(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", "renderOverlayMessage(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", "renderTitleAndSubtitle(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", "renderPlayerList(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"}
   )
   private void onRenderServerHudLayer(DrawContext drawContext, RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
      if (ScreenSoundsUtils.isGuiClickApiOpen()) {
         callbackInfo.cancel();
      }
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"},
      cancellable = true
   )
   private void onRenderCrosshair(DrawContext drawContext, RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
      if (MinecraftClient.getInstance().currentScreen != null || CustomCrosshair.isFlag()) {
         callbackInfo.cancel();
      }
   }

   @ModifyExpressionValue(
      method = {"renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z"
      )}
   )
   private boolean bytephaseFreecamCrosshair(boolean bytephaseFreecamFlag) {
      return bytephaseFreecamFlag || Freecam.isBaseInstance();
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"renderStatusEffectOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"},
      cancellable = true
   )
   private void onRenderStatusEffectOverlay(DrawContext drawContext, RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
      Module m = ModuleRegistry.getModule(ShowHUD.class);
      if (m != null && m.isEnabled()) {
         callbackInfo.cancel();
      }
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"renderVignetteOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/Entity;)V"},
      cancellable = true
   )
   private void onRenderVignetteOverlay(DrawContext drawContext, Entity entity, CallbackInfo callbackInfo) {
      Module m = ModuleRegistry.getModule(ShowHUD.class);
      if (m != null && m.isEnabled()) {
         callbackInfo.cancel();
      }
   }

   @Inject(
      at = {@At("HEAD")},
      cancellable = true,
      require = 0,
      method = {"renderHotbar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"}
   )
   private void bytephaseCustomHotbar(DrawContext context, RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
      if (ShowHUD.isSpectatorHidden() && HotbarHud.canInstanceFlag(context, renderTickCounter)) {
         callbackInfo.cancel();
      }
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"renderStatusBars(Lnet/minecraft/client/gui/DrawContext;)V"},
      cancellable = true,
      require = 0
   )
   private void bytephaseHideStatusBars(DrawContext drawContext, CallbackInfo callbackInfo) {
      if (ShowHUD.isKeybindsLevel()) {
         callbackInfo.cancel();
      }
   }

   @ModifyExpressionValue(
      method = {"renderMainHud(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", "getCurrentBarType()Lnet/minecraft/client/gui/hud/InGameHud$BarType;"},
      require = 0,
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;hasExperienceBar()Z"
      )}
   )
   private boolean bytephaseHideExperienceBar(boolean bytephaseHideFlag) {
      return bytephaseHideFlag && !ShowHUD.isKeybindsLevel();
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"renderHeldItemTooltip(Lnet/minecraft/client/gui/DrawContext;)V"},
      require = 0
   )
   private void bytephaseLiftHeldItemTooltip(DrawContext drawContext, CallbackInfo callbackInfo) {
      this.bytephaseLiftText(drawContext);
   }

   @Inject(
      at = {@At("RETURN")},
      method = {"renderHeldItemTooltip(Lnet/minecraft/client/gui/DrawContext;)V"},
      require = 0
   )
   private void bytephaseDropHeldItemTooltip(DrawContext drawContext, CallbackInfo callbackInfo) {
      this.bytephaseDropText(drawContext);
   }

   @Inject(
      at = {@At("HEAD")},
      require = 0,
      method = {"renderOverlayMessage(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"}
   )
   private void bytephaseLiftOverlayMessage(DrawContext drawContext, RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
      if (!ScreenSoundsUtils.isGuiClickApiOpen()) {
         this.bytephaseLiftText(drawContext);
      }
   }

   @Inject(
      at = {@At("RETURN")},
      require = 0,
      method = {"renderOverlayMessage(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"}
   )
   private void bytephaseDropOverlayMessage(DrawContext drawContext, RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
      this.bytephaseDropText(drawContext);
   }

   @Unique
   private void bytephaseLiftText(DrawContext drawContext) {
      MinecraftClient mc = MinecraftClient.getInstance();
      float lift = ShowHUD.getMax(mc.interactionManager != null && mc.interactionManager.hasStatusBars());
      if (!(lift <= 0.0F)) {
         drawContext.getMatrices().pushMatrix();
         drawContext.getMatrices().translate(0.0F, -lift);
         this.bytephaseTextLifted = true;
      }
   }

   @Unique
   private void bytephaseDropText(DrawContext drawContext) {
      if (this.bytephaseTextLifted) {
         this.bytephaseTextLifted = false;
         drawContext.getMatrices().popMatrix();
      }
   }
}
