package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.BetterChat;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @ModifyConstant(method = "init", constant = @Constant(intValue = 256))
    private int bytephase$chatMessageLength(int original) {
        BetterChat betterChat = BytePhase.modules == null ? null : BytePhase.modules.betterChat();
        return betterChat == null || !betterChat.isEnabled() ? original : betterChat.messageLengthLimit();
    }
}
