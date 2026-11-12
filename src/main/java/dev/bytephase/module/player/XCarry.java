package dev.bytephase.module.player;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.PlayerScreenHandler;

public final class XCarry extends Module {
    public XCarry() {
        super("XCarry", Category.PLAYER, "Keeps items in your inventory when you close it");
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (!isEnabled() || !(packet instanceof CloseHandledScreenC2SPacket close)) {
            return false;
        }
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) {
            return false;
        }
        // The packet carries the sync id of the handler being closed. Checking
        // currentScreenHandler is racy because closeHandledScreen resets it
        // while the packet is being sent, which can make a chest close look
        // like a player-inventory close.
        PlayerScreenHandler handler = mc.player.playerScreenHandler;
        if (close.getSyncId() != handler.syncId) {
            return false;
        }
        // Do not desync the server for an ordinary close. Only keep the screen
        // open when there is actually something in the crafting grid to carry.
        for (int slot = PlayerScreenHandler.CRAFTING_INPUT_START;
             slot < PlayerScreenHandler.CRAFTING_INPUT_END; slot++) {
            if (!handler.getSlot(slot).getStack().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
