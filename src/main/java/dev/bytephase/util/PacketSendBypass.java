package dev.bytephase.util;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;

/** Sends a packet without re-entering client module packet interceptors. */
public final class PacketSendBypass {
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private PacketSendBypass() {
    }

    public static boolean isActive() {
        Integer depth = DEPTH.get();
        return depth != null && depth > 0;
    }

    public static void send(ClientConnection connection, Packet<?> packet) {
        Integer currentDepth = DEPTH.get();
        int depth = currentDepth == null ? 0 : currentDepth;
        DEPTH.set(depth + 1);
        try {
            connection.send(packet);
        } finally {
            if (depth == 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(depth);
            }
        }
    }
}
