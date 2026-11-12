package dev.bytephase.module.misc;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

public final class AutoReconnect extends Module {
    private static boolean suppressNext;
    private static net.minecraft.network.ClientConnection suppressedConnection;

    private final NumberSetting delay = add(new NumberSetting("DelaySeconds", 5, 1, 60, 1));

    private double timer;
    /** The last server entry seen while connected; the entry is gone by the time the screen shows. */
    private ServerInfo lastServer;

    /**
     * Called by AutoLog before it disconnects. Intentional combat logs should
     * not immediately be undone by AutoReconnect; the suppression lasts until
     * the player has manually joined a server again.
     */
    public static void suppressNextReconnect() {
        suppressNext = true;
        var mc = BytePhase.mc;
        suppressedConnection = mc == null || mc.getNetworkHandler() == null
                ? null : mc.getNetworkHandler().getConnection();
    }

    public AutoReconnect() {
        super("AutoReconnect", Category.MISC, "Rejoins 2b2t after being disconnected");
    }

    @Override
    public void onEnable() {
        timer = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null) {
            return;
        }
        if (suppressNext && mc.player != null && mc.getNetworkHandler() != null
                && mc.getNetworkHandler().getConnection() != suppressedConnection) {
            // The original connection can linger for many ticks while its
            // disconnect is processed. Only a new connection ends suppression.
            suppressNext = false;
            suppressedConnection = null;
        }
        ServerInfo connected = mc.getCurrentServerEntry();
        if (connected != null) {
            lastServer = connected;
        }
        if (!(mc.currentScreen instanceof DisconnectedScreen)) {
            timer = 0;
            return;
        }
        if (suppressNext) {
            return;
        }
        timer += 1.0 / 20.0;
        if (timer < delay.getValue()) {
            return;
        }
        timer = 0;
        ServerInfo server = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry() : lastServer;
        if (server == null) {
            server = new ServerInfo("2b2t", "2b2t.org", ServerInfo.ServerType.OTHER);
        }
        ServerAddress address = ServerAddress.parse(server.address);
        ConnectScreen.connect(mc.currentScreen, mc, address, server, false, null);
    }
}
