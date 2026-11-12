package dev.bytephase.module.misc;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import net.minecraft.network.packet.Packet;

public final class PacketLogger extends Module {
    public PacketLogger() {
        super("PacketLogger", Category.MISC, "Prints outgoing packets to the log");
    }

    public void log(Packet<?> packet) {
        BytePhase.LOGGER.info("[C2S] {}", packet.getClass().getSimpleName());
    }
}
