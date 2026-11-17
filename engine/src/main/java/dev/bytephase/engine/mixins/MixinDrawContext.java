package dev.bytephase.engine.mixins;

import dev.bytephase.engine.render.MatrixWindowUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.categories.misc.NameProtect;

@Mixin({DrawContext.class})
public class MixinDrawContext {
   @Inject(
      method = {"enableScissor(IIII)V"},
      at = {@At("HEAD")}
   )
   private void bytephase_enableScissor(int bytephaseEnableValue, int bytephaseEnableValue2, int bytephaseEnableValue3, int bytephaseEnableValue4, CallbackInfo callbackInfo) {
      MatrixWindowUtils.handleDequeWindow(bytephaseEnableValue, bytephaseEnableValue2, bytephaseEnableValue3, bytephaseEnableValue4);
   }

   @Inject(
      method = {"disableScissor()V"},
      at = {@At("HEAD")}
   )
   private void bytephase_disableScissor(CallbackInfo callbackInfo) {
      MatrixWindowUtils.updateDeque();
   }

   @ModifyVariable(
      method = {"drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V"},
      at = @At("HEAD"),
      argsOnly = true,
      index = 2
   )
   private Text bytephase_maskDrawnName(Text displayName) {
      return NameProtect.getDisplayName(displayName);
   }
}
