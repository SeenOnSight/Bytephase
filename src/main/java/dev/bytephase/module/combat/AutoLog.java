package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.module.misc.AutoReconnect;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.network.ClientConnection;
import net.minecraft.text.Text;

public final class AutoLog extends Module {
    private final NumberSetting minHealth = add(new NumberSetting("MinHealth", 6.0, 1.0, 20.0, 0.5));
    private ClientConnection observedConnection;
    private boolean disconnectedForLowHealth;

    public AutoLog() {
        super("AutoLog", Category.COMBAT, "Disconnects you when your health drops below a threshold");
    }

    @Override
    protected void onEnable() {
        observedConnection = currentConnection();
        disconnectedForLowHealth = false;
    }

    @Override
    protected void onDisable() {
        observedConnection = null;
        disconnectedForLowHealth = false;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.getNetworkHandler() == null) {
            return;
        }
        ClientConnection connection = currentConnection();
        if (connection != observedConnection) {
            observedConnection = connection;
            disconnectedForLowHealth = false;
        }
        if (connection == null || disconnectedForLowHealth) {
            return;
        }
        if (mc.player.isDead()) {
            return;
        }
        if (mc.player.getHealth() <= minHealth.asFloat()) {
            // Latch before disconnecting; the network handler can remain
            // available for additional client ticks while the disconnect is
            // being processed.
            disconnectedForLowHealth = true;
            AutoReconnect.suppressNextReconnect();
            connection.disconnect(Text.literal("[BytePhase] AutoLog"));
        }
    }

    private ClientConnection currentConnection() {
        var mc = BytePhase.mc;
        return mc == null || mc.player == null || mc.player.networkHandler == null
                ? null : mc.player.networkHandler.getConnection();
    }
}
