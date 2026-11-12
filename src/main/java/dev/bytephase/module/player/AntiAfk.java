package dev.bytephase.module.player;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import java.util.Random;
import net.minecraft.client.network.ClientPlayerEntity;

public final class AntiAfk extends Module {
    private final NumberSetting interval = add(new NumberSetting("Interval", 10.0, 3.0, 60.0, 1.0));
    private static final Random RANDOM = new Random();
    private double timer;
    private boolean toggled;
    private boolean previousSneaking;
    private ClientPlayerEntity capturedPlayer;

    public AntiAfk() {
        super("AntiAFK", Category.PLAYER, "Prevents being kicked for idling");
    }

    @Override
    public void onEnable() {
        timer = 0;
        toggled = false;
        var mc = BytePhase.mc;
        capturedPlayer = mc == null ? null : mc.player;
        previousSneaking = capturedPlayer != null && capturedPlayer.isSneaking();
    }

    @Override
    protected void onDisable() {
        restoreCapturedPlayer();
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.currentScreen != null) {
            return;
        }
        if (capturedPlayer != mc.player) {
            // A config can enable this module before the world/player exists,
            // and reconnects replace the ClientPlayerEntity. Never apply the
            // previous player's saved input state to the newly joined player.
            restoreCapturedPlayer();
            capturedPlayer = mc.player;
            previousSneaking = capturedPlayer.isSneaking();
            timer = 0;
            toggled = false;
        }
        timer += 1.0 / 20.0;
        if (timer < interval.getValue()) {
            return;
        }
        timer = 0;
        if (toggled) {
            mc.player.setSneaking(false);
            mc.player.setVelocityClient(new net.minecraft.util.math.Vec3d(
                    mc.player.getVelocity().x, 0.45, mc.player.getVelocity().z));
        } else {
            mc.player.setSneaking(true);
            mc.player.setYaw(mc.player.getYaw() + (RANDOM.nextBoolean() ? 7.0f : -7.0f));
        }
        toggled = !toggled;
    }

    private void restoreCapturedPlayer() {
        if (capturedPlayer != null) {
            capturedPlayer.setSneaking(previousSneaking);
            capturedPlayer = null;
        }
    }
}
