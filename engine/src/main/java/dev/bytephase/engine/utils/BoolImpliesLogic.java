package dev.bytephase.engine.utils;

public final class BoolImpliesLogic {
   public static boolean doesImply(boolean antecedent, boolean consequent) {
      return !antecedent ? true : consequent;
   }

   private BoolImpliesLogic() {
   }
}
