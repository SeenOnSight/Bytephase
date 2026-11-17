package dev.bytephase.engine.mixins;

import dev.bytephase.engine.gui.Button;
import dev.bytephase.engine.gui.ZoomHud;
import dev.bytephase.engine.render.ClampMatrixUtils;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ai.auth.ModuleRegistry;
import dev.bytephase.engine.utils.PlayerScaffold;

@Mixin({Mouse.class})
public class MixinMouse {
   @Shadow
   private double x;
   @Shadow
   private double y;
   @Shadow
   private double cursorDeltaX;
   @Shadow
   private double cursorDeltaY;
   @Unique
   private double bytephase$cursorDeltaXBefore;
   @Unique
   private double bytephase$cursorDeltaYBefore;

   @Inject(
      method = {"onCursorPos(JDD)V"},
      at = {@At("HEAD")}
   )
   private void onCursorPos(long cursorPosValue, double cursorPosValue2, double cursorPosValue3, CallbackInfo callbackInfo) {
      this.bytephase$cursorDeltaXBefore = this.cursorDeltaX;
      this.bytephase$cursorDeltaYBefore = this.cursorDeltaY;
   }

   @Inject(
      method = {"onCursorPos(JDD)V"},
      at = {@At("RETURN")}
   )
   private void bytephaseAfterCursorPos(long bytephaseAfterValue, double bytephaseAfterValue2, double bytephaseAfterValue3, CallbackInfo callbackInfo) {
      double deltaX = this.cursorDeltaX - this.bytephase$cursorDeltaXBefore;
      double deltaY = this.cursorDeltaY - this.bytephase$cursorDeltaYBefore;
      if (deltaX != 0.0 || deltaY != 0.0) {
         PlayerScaffold event = new PlayerScaffold(deltaX, deltaY);
         ModuleRegistry.dispatchPlayerScaffold(event);
      }
   }

   @Inject(
      method = {"onMouseScroll(JDD)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseScroll(long mouseScrollValue, double mouseScrollValue2, double mouseScrollValue3, CallbackInfo callbackInfo) {
      ZoomHud event = new ZoomHud(mouseScrollValue3);
      ModuleRegistry.dispatchZoomHud(event);
      if (event.isFlag()) {
         callbackInfo.cancel();
      }
   }

   @Inject(
      method = {"onMouseButton(JLnet/minecraft/client/input/MouseInput;I)V"},
      at = {@At("HEAD")}
   )
   private void onMouseButton(long mouseButtonValue, MouseInput mouseInput, int mouseButtonValue2, CallbackInfo callbackInfo) {
      Button event = new Button(mouseInput.button(), mouseButtonValue2);
      ModuleRegistry.dispatchButtonClick(event);
   }

   @ModifyReturnValue(
      method = {"getScaledX(Lnet/minecraft/client/util/Window;)D"},
      at = {@At("RETURN")}
   )
   private double bytephase$gui3dScaledX(double bytephasegui3dScaledValue, Window window) {
      return ClampMatrixUtils.getAmountFlag(bytephasegui3dScaledValue, this.y, window);
   }

   @ModifyReturnValue(
      method = {"getScaledY(Lnet/minecraft/client/util/Window;)D"},
      at = {@At("RETURN")}
   )
   private double bytephase$gui3dScaledY(double bytephasegui3dScaledValue, Window window) {
      return ClampMatrixUtils.getAmountFlagForDoubleWindow(this.x, bytephasegui3dScaledValue, window);
   }
}
