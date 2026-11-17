package dev.bytephase.engine.mixins;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import dev.bytephase.engine.categories.bots.AutoTpaHere;
import dev.bytephase.engine.categories.bots.AutoAuctionFlipper;
import dev.bytephase.engine.categories.bots.AutoBuilder;
import dev.bytephase.engine.utils.ConfirmScreenHelper;

@Mixin({ChatHud.class})
public class MixinChatHud {
   @ModifyVariable(
      method = {"addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V"},
      at = @At("HEAD"),
      argsOnly = true,
      index = 1
   )
   private Text bytephase_maskChatName(Text text) {
      if (text != null && AutoBuilder.isLowerCaseAvailable()) {
         AutoBuilder.onChatPurchaseMessage(text.getString());
      }

      if (text != null && AutoAuctionFlipper.hasActiveMessageMatchers()) {
         AutoAuctionFlipper.onChatMessageReceived(text.getString());
      }

      if (text != null && ConfirmScreenHelper.isLowerCase()) {
         ConfirmScreenHelper.setMoneyEnoughText(text.getString());
      }

      if (text != null && AutoTpaHere.isRunning()) {
         AutoTpaHere.recordOverlayMessage(text.getString());
      }

       // Keep the original message for matchers and refreshable chat history.
       return text;
   }
}
