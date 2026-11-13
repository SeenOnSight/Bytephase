package dev.bytephase.pvp.api;

import java.util.List;

public interface CombatModule {
    String id();
    String name();
    String description();
    List<CombatSetting> settings();
    boolean isEnabled();
    void setEnabled(boolean enabled);
    boolean showInHud();
    default boolean usesProjection() { return true; }
}
