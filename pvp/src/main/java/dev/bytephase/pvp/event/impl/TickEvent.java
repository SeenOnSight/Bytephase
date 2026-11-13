package dev.bytephase.pvp.event.impl;

import dev.bytephase.pvp.event.Event;
import dev.bytephase.pvp.event.EventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class TickEvent extends Event {
    public static final EventType<TickEvent> TYPE = new EventType<>("TickEvent");
    private static final TickEvent INSTANCE = new TickEvent();

    private TickEvent() {
    }

    public static void dispatch() {
        TYPE.dispatch(INSTANCE);
    }

    public static void dispatchPost() {
        TYPE.dispatchPost(INSTANCE);
    }
}
