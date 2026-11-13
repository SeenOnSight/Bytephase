package dev.bytephase.pvp.api;

public interface CombatServices {
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
