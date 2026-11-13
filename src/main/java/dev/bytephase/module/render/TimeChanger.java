package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.world.ClientWorld;

/**
 * Client-side time of day. Server updates continue to update world age and
 * other state while their time-of-day value is replaced locally.
 */
public final class TimeChanger extends Module {
    private final NumberSetting time = add(new NumberSetting("Time", 6000, -20000, 20000, 100));

    private long previousTime;
    private boolean hadPrevious;
    private ClientWorld modifiedWorld;

    public TimeChanger() {
        super("TimeChanger", Category.RENDER, "Changes the client-side time of day");
    }

    public long originalTime(ClientWorld world) {
        return hadPrevious && modifiedWorld == world ? previousTime : world.getLevelProperties().getTimeOfDay();
    }

    @Override
    public void onEnable() {
        var mc = BytePhase.mc;
        hadPrevious = mc != null && mc.world != null;
        if (hadPrevious) {
            modifiedWorld = mc.world;
            previousTime = mc.world.getLevelProperties().getTimeOfDay();
        } else {
            modifiedWorld = null;
        }
    }

    @Override
    public void onDisable() {
        restorePreviousWorld();
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.world == null) {
            return;
        }
        if (modifiedWorld != mc.world) {
            // Restore the world we left, then snapshot the newly joined
            // world's time before forcing the configured value there.
            restorePreviousWorld();
            modifiedWorld = mc.world;
            previousTime = mc.world.getLevelProperties().getTimeOfDay();
            hadPrevious = true;
        }
        mc.world.getLevelProperties().setTimeOfDay(Math.round(time.getValue()));
    }

    /** Called on the client thread when the server updates this world's time. */
    public long overrideTime(ClientWorld world, long serverTime) {
        if (world != modifiedWorld) {
            restorePreviousWorld();
            modifiedWorld = world;
        }
        previousTime = serverTime;
        hadPrevious = true;
        return Math.round(time.getValue());
    }

    private void restorePreviousWorld() {
        if (hadPrevious && modifiedWorld != null) {
            modifiedWorld.getLevelProperties().setTimeOfDay(previousTime);
        }
        hadPrevious = false;
        modifiedWorld = null;
    }
}
