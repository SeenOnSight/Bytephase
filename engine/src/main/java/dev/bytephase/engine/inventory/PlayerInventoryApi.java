package dev.bytephase.engine.inventory;

import net.minecraft.item.ItemStack;
import dev.bytephase.engine.utils.SwapPreferenceData;

public interface PlayerInventoryApi {
   boolean isBreachOwnerActive();

   SwapPreferenceData getSwapPreference();

   int getSwapPriority();

   default boolean isMaceSmashReady() {
      return false;
   }

   default boolean isSwapAllowed(Object obj, ItemStack stack) {
      return true;
   }
}
