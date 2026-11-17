package dev.bytephase.engine.utils;
import dev.bytephase.engine.utils.AnchorRoutePlanner;
import dev.bytephase.engine.utils.PlayerCombatState;

public interface RoutedBreachingApi {
   float getRemainingRouteHp();

   double getEstimatedTicksRemaining();

   int getMaxDrop();

   AnchorRoutePlanner.Mode getRouteMode();

   int getTimeUnreachable();

   PlayerCombatState getImmuneFire();
}
