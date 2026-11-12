package dev.bytephase.module.player;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.module.misc.AutoReconnect;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.text.Text;

public final class AutoDisconnect extends Module {
    private final NumberSetting health = add(new NumberSetting("Health", 4, 1, 20, 0.5));
    private final BooleanSetting absorption = add(new BooleanSetting("CountAbsorption", true));

    public AutoDisconnect() {
        super("AutoDisconnect", Category.PLAYER, "Disconnects once when health is below the chosen threshold");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc.player == null || mc.getNetworkHandler() == null || !mc.player.isAlive()
                || mc.isInSingleplayer() || mc.player.isSpectator()) return;
        float current = mc.player.getHealth() + (absorption.getValue() ? mc.player.getAbsorptionAmount() : 0);
        if (current > health.getValue()) return;
        AutoReconnect.suppressNextReconnect();
        setEnabled(false);
        mc.getNetworkHandler().getConnection().disconnect(Text.literal("Bytephase: low-health disconnect"));
    }
}
