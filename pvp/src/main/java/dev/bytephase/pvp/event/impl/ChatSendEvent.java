package dev.bytephase.pvp.event.impl;

import dev.bytephase.pvp.event.CancellableEvent;
import dev.bytephase.pvp.event.CancellableEventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ChatSendEvent extends CancellableEvent {
    public static final CancellableEventType<ChatSendEvent> TYPE = new CancellableEventType<>("ChatSendEvent");
    public String message;
    private static final ChatSendEvent INSTANCE = new ChatSendEvent();

    private ChatSendEvent() {
    }

    public static boolean dispatch(String message) {
        if (!TYPE.hasPreListeners()) {
            return false;
        }

        INSTANCE.message = message;
        INSTANCE.resetCancellation();
        TYPE.dispatch(INSTANCE);
        boolean cancelled = INSTANCE.isCancelled();
        INSTANCE.message = null;
        return cancelled;
    }
}
