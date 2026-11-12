package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.misc.AntiSpam;
import dev.bytephase.module.misc.ChatFilter;
import dev.bytephase.module.misc.NameProtect;
import dev.bytephase.module.render.BetterChat;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), argsOnly = true)
    private Text bytephase$nameProtect(Text message) {
        return NameProtect.filter(message);
    }

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Text bytephase$nameProtectSigned(Text message) {
        return NameProtect.filter(message);
    }

    /** Drops filtered and repeated lines before they reach the chat history. */
    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void bytephase$filterChat(Text message, MessageSignatureData signature, MessageIndicator indicator,
                                      CallbackInfo ci) {
        if (ChatFilter.blocks(message) || AntiSpam.blocks(message)) {
            ci.cancel();
        }
    }

    /** The 100-message retention limit is a literal in the private store method. */
    @ModifyConstant(method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V",
            constant = @Constant(intValue = 100))
    private int bytephase$chatHistory(int original) {
        BetterChat betterChat = BytePhase.modules == null ? null : BytePhase.modules.betterChat();
        return betterChat == null || !betterChat.isEnabled() ? original : betterChat.historyLimit();
    }
}
