package dev.bytephase.pvp.api;

public interface CombatServices {
    default int accentColor() { return 0xFFC0C0C0; }
    /** Optional utility-owned aim for an item interaction, expressed as yaw and pitch. */
    default double[] interactionRotation() { return null; }
    default void drawHudWidget(String name, Object graphics, Runnable draw) { draw.run(); }
    default boolean isCleanCapture() { return false; }
    boolean isEating();
    boolean isFreecam();
    boolean sprintHeld();
    double[] project(double x, double y, double z);
    boolean reserveLabel(int x, int y, int width, int height);
    default boolean supportsWorldOverlays() { return false; }
    default void drawWorldBox(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ, int fill, int outline, boolean throughWalls) { }
    default void drawWorldLine(double fromX, double fromY, double fromZ,
            double toX, double toY, double toZ, int color, boolean throughWalls) { }
}
