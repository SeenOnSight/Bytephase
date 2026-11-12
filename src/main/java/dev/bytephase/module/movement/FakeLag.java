package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.PacketSendBypass;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public final class FakeLag extends Module {
    private final NumberSetting maxPackets = add(new NumberSetting("MaxPackets", 30.0, 5.0, 200.0, 1.0));
    private final NumberSetting maxDelay = add(new NumberSetting("MaxDelay", 20, 1, 100, 1));
    private final NumberSetting correctionPause = add(new NumberSetting("CorrectionPause", 10, 1, 40, 1));
    private final List<Packet<?>> buffer = new ArrayList<>();
    private boolean flushing;
    private ClientConnection bufferConnection;
    private ClientWorld bufferWorld;
    private int ticks;
    private int correctionTicks;

    public FakeLag() {
        super("FakeLag", Category.MOVEMENT, "Delays your movement packets until released");
    }

    @Override
    protected synchronized void onEnable() {
        buffer.clear();
        flushing = false;
        bufferConnection = currentConnection();
        bufferWorld = BytePhase.mc == null ? null : BytePhase.mc.world;
        ticks = 0;
        correctionTicks = 0;
    }

    @Override
    protected synchronized void onDisable() {
        flush();
        bufferConnection = null;
        bufferWorld = null;
    }

    @Override
    public synchronized void onServerCorrection() {
        buffer.clear();
        ticks = 0;
        correctionTicks = correctionPause.asInt();
    }

    @Override
    public synchronized void onTick() {
        if (correctionTicks > 0) correctionTicks--;
        var mc = BytePhase.mc;
        ClientConnection connection = currentConnection();
        if (connection == null || bufferConnection != connection || bufferWorld != mc.world) {
            buffer.clear();
            bufferConnection = connection;
            bufferWorld = mc == null ? null : mc.world;
            ticks = 0;
            return;
        }
        // Stationary players send few movement packets; a packet-count-only
        // limit otherwise leaves their last movement buffered indefinitely.
        if (!buffer.isEmpty() && ++ticks >= maxDelay.asInt()) {
            flush();
        }
    }

    @Override
    public synchronized boolean onPacketSend(Packet<?> packet) {
        if (flushing || correctionTicks > 0 || !(packet instanceof PlayerMoveC2SPacket)) {
            return false;
        }
        ClientConnection connection = currentConnection();
        if (connection == null) {
            buffer.clear();
            bufferConnection = null;
            bufferWorld = null;
            ticks = 0;
            return false;
        }
        if (bufferConnection != connection || bufferWorld != BytePhase.mc.world) {
            buffer.clear();
            bufferConnection = connection;
            bufferWorld = BytePhase.mc.world;
            ticks = 0;
        }
        buffer.add(packet);
        if (buffer.size() >= maxPackets.asInt()) {
            flush();
        }
        return true;
    }

    private synchronized void flush() {
        flushing = true;
        try {
            var mc = BytePhase.mc;
            ClientConnection connection = currentConnection();
            if (connection != null && connection == bufferConnection && mc.world == bufferWorld) {
                for (Packet<?> packet : new ArrayList<>(buffer)) {
                    PacketSendBypass.send(connection, packet);
                }
            }
        } finally {
            buffer.clear();
            bufferConnection = currentConnection();
            bufferWorld = BytePhase.mc == null ? null : BytePhase.mc.world;
            ticks = 0;
            flushing = false;
        }
    }

    private ClientConnection currentConnection() {
        var mc = BytePhase.mc;
        return mc == null || mc.world == null || mc.player == null || mc.player.networkHandler == null
                ? null : mc.player.networkHandler.getConnection();
    }
}
