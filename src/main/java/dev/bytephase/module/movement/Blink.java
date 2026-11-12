package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.util.PacketSendBypass;
import dev.bytephase.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * Holds back movement packets so the server keeps seeing your old position.
 * When the buffer is flushed you appear to teleport to the server.
 */
public final class Blink extends Module {
    private static final int MAX_BUFFERED = 240;

    private final NumberSetting pulseDelay = add(new NumberSetting("PulseDelay", 0, 0, 60, 1));
    private final NumberSetting correctionPause = add(new NumberSetting("CorrectionPause", 10, 1, 40, 1));

    private final List<Packet<?>> buffer = new ArrayList<>();
    private int ticks;
    private boolean flushing;
    private ClientConnection bufferConnection;
    private ClientWorld bufferWorld;
    private int correctionTicks;

    public Blink() {
        super("Blink", Category.MOVEMENT, "Suspends your movement packets, then sends them at once");
    }

    @Override
    public synchronized void onEnable() {
        buffer.clear();
        ticks = 0;
        flushing = false;
        bufferConnection = currentConnection();
        bufferWorld = BytePhase.mc == null ? null : BytePhase.mc.world;
        correctionTicks = 0;
    }

    @Override
    public synchronized void onDisable() {
        flush();
        bufferConnection = null;
        bufferWorld = null;
    }

    @Override
    public synchronized void onServerCorrection() {
        // Never replay pre-teleport movement or buffer vanilla's full acknowledgement.
        buffer.clear();
        ticks = 0;
        correctionTicks = correctionPause.asInt();
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
            return false;
        }
        if (bufferConnection != connection || bufferWorld != BytePhase.mc.world) {
            buffer.clear();
            bufferConnection = connection;
            bufferWorld = BytePhase.mc.world;
            ticks = 0;
        }
        // Bound the buffer: an unbounded one grows for as long as the module is
        // left on, and a very large flush is rejected by the server anyway.
        if (buffer.size() >= MAX_BUFFERED) {
            flush();
        }
        buffer.add(packet);
        return true;
    }

    @Override
    public synchronized void onTick() {
        if (correctionTicks > 0) correctionTicks--;
        var mc = BytePhase.mc;
        ClientConnection connection = currentConnection();
        if (mc == null || mc.player == null || connection == null) {
            buffer.clear();
            bufferConnection = null;
            bufferWorld = null;
            ticks = 0;
            return;
        }
        if (bufferConnection != connection || bufferWorld != mc.world) {
            buffer.clear();
            bufferConnection = connection;
            bufferWorld = mc.world;
            ticks = 0;
        }

        int delay = Math.max(0, pulseDelay.asInt());
        if (delay == 0) {
            return;
        }
        if (++ticks >= delay) {
            ticks = 0;
            flush();
        }
    }

    private synchronized void flush() {
        var mc = BytePhase.mc;
        if (buffer.isEmpty()) {
            return;
        }
        List<Packet<?>> pending = new ArrayList<>(buffer);
        buffer.clear();
        ClientConnection connection = currentConnection();
        if (mc == null || connection == null || connection != bufferConnection || mc.world != bufferWorld) {
            return;
        }

        flushing = true;
        try {
            for (Packet<?> packet : pending) {
                PacketSendBypass.send(connection, packet);
            }
        } catch (Exception e) {
            BytePhase.LOGGER.error("Blink failed to flush movement packets", e);
        } finally {
            flushing = false;
        }
    }

    private ClientConnection currentConnection() {
        var mc = BytePhase.mc;
        return mc == null || mc.world == null || mc.player == null || mc.player.networkHandler == null
                ? null : mc.player.networkHandler.getConnection();
    }
}
