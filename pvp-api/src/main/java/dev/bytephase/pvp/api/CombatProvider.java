package dev.bytephase.pvp.api;

import java.util.List;

/** Java-only boundary between the utility client and the combat component. */
public interface CombatProvider {
    List<CombatModule> modules();
    void initialize(CombatServices services);
    void beginTick();
    void endTick();
    default boolean sprintSuppressed() { return false; }
    default List<String> friends() { return List.of(); }
    default boolean addFriend(String name) { return false; }
    default boolean removeFriend(String name) { return false; }
    void renderHud(Object graphics, float tickDelta);
    void renderWorld(float tickDelta);
    default void renderWorldOverlays(float tickDelta) { }
}
