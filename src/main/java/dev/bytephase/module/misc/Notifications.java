package dev.bytephase.module.misc;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.ui.Theme;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayDeque;
import java.util.Iterator;

/** Small on-screen toasts whenever a module is switched on or off. */
public final class Notifications extends Module {
    private static final int MAX_VISIBLE = 5;
    private static final long FADE_NANOS = 400_000_000L;

    private final NumberSetting duration = add(new NumberSetting("Duration", 2.0, 0.5, 6.0, 0.5));
    private final ArrayDeque<Toast> toasts = new ArrayDeque<>();

    private record Toast(String name, boolean on, long createdNanos) {
    }

    public Notifications() {
        super("Notifications", Category.MISC, "Shows a toast when a module is toggled");
    }

    @Override
    protected void onDisable() {
        toasts.clear();
    }

    /** Called by {@link Module#setEnabled} after every real state change. */
    public static void onToggled(Module module) {
        if (BytePhase.modules == null || BytePhase.config == null || BytePhase.config.isLoading()) {
            return;
        }
        Notifications notifications = BytePhase.modules.get(Notifications.class);
        if (notifications == null || !notifications.isEnabled() || module == notifications) {
            return;
        }
        notifications.toasts.addLast(new Toast(module.getName(), module.isEnabled(), System.nanoTime()));
        while (notifications.toasts.size() > MAX_VISIBLE) {
            notifications.toasts.removeFirst();
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var mc = BytePhase.mc;
        if (mc == null || toasts.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        long lifetime = (long) (duration.getValue() * 1_000_000_000L);
        int y = context.getScaledWindowHeight() - 44;
        for (Iterator<Toast> it = toasts.descendingIterator(); it.hasNext(); ) {
            Toast toast = it.next();
            long age = now - toast.createdNanos();
            if (age > lifetime) {
                it.remove();
                continue;
            }
            // Fade out over the last stretch; Minecraft treats alpha < 4 as opaque.
            long remaining = lifetime - age;
            int alpha = remaining >= FADE_NANOS ? 255 : (int) (255 * remaining / FADE_NANOS);
            if (alpha < 8) {
                continue;
            }
            String state = toast.on() ? "enabled" : "disabled";
            String text = toast.name() + " " + state;
            int width = mc.textRenderer.getWidth(text) + 12;
            int x = context.getScaledWindowWidth() - width - 6;
            int accent = toast.on() ? Theme.ACCENT : Theme.OFF;
            context.fill(x, y, x + width, y + 14, ((alpha * 160 / 255) << 24));
            context.fill(x, y, x + 2, y + 14, (alpha << 24) | (accent & 0xFFFFFF));
            context.drawText(mc.textRenderer, text, x + 7, y + 3, (alpha << 24) | 0xFFFFFF, false);
            y -= 16;
        }
    }
}
