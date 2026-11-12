package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

/**
 * Stops the server from seeing that you started sprinting, which is what makes
 * sprinting and sprint-jumping drain the hunger bar. Your own client still
 * sprints normally; only the notification packet is withheld.
 */
public final class AntiHunger extends Module {
    private final BooleanSetting sprint = add(new BooleanSetting("Sprint", true));

    public AntiHunger() {
        super("AntiHunger", Category.MOVEMENT, "Stops sprinting from draining hunger");
    }

    @Override
    protected void onEnable() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.getNetworkHandler() == null) {
            return;
        }
        // The client is already sprinting, so it will not send another start
        // packet. Tell the server to stop sprinting once.
        if (mc.player.isSprinting()) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(
                    mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        }
    }

    @Override
    protected void onDisable() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.getNetworkHandler() == null) {
            return;
        }
        // Restore the state the server would have had without the module.
        if (mc.player.isSprinting()) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(
                    mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
        }
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        return isEnabled() && sprint.getValue() && packet instanceof ClientCommandC2SPacket command
                && command.getMode() == ClientCommandC2SPacket.Mode.START_SPRINTING;
    }

}
