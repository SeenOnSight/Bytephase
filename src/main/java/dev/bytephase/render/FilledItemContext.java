package dev.bytephase.render;

/** Scoped to synchronous item command submission on the render thread. */
public final class FilledItemContext {
    public record Tint(int color, boolean throughWalls) { }
    private static final ThreadLocal<Tint> CURRENT = new ThreadLocal<>();

    private FilledItemContext() { }

    public static Tint current() { return CURRENT.get(); }

    public static void render(Tint tint, Runnable render) {
        Tint previous = CURRENT.get();
        try {
            if (tint == null) CURRENT.remove(); else CURRENT.set(tint);
            render.run();
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
}
