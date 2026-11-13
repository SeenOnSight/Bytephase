package dev.bytephase.pvp.api;

public interface CombatSetting {
    enum Kind { BOOLEAN, NUMBER, MODE, COLOR, KEY }
    String id();
    String name();
    Kind kind();
    String json();
    void json(String value);
    long revision();
    boolean visible();
    default double min() { return 0; }
    default double max() { return 1; }
    default double step() { return 1; }
    default String[] modes() { return new String[0]; }
}
