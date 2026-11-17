package dev.bytephase.engine.utils;

import java.util.function.BooleanSupplier;
import dev.bytephase.engine.categories.combat.AutoInventoryTotem;
import dev.bytephase.engine.utils.AutoVanish;
import dev.bytephase.engine.utils.InventoryPlayer;
import ai.auth.ModuleRegistry;

public final class TotemSupplierAdapter {
   private final BooleanSupplier extraCondition;
   private final BooleanSupplier enabledCondition;
   private final Object owner;

   public void runTotemTick() {
      this.runTotemTickWithHandSwap(false);
   }

   public void runTotemTickWithHandSwap(boolean supplierFlag) {
      if (AutoInventoryTotem.acquireExternalControl(this.owner, this.enabledCondition, this.extraCondition)) {
         if (supplierFlag) {
            AutoInventoryTotem.requestHandSwapWindow(this.owner, 3);
         }

         AutoInventoryTotem autoInventoryTotem = findTotemModule();
         if (autoInventoryTotem != null) {
            autoInventoryTotem.forwardTick();
         }
      }
   }

   public void onAutoVanish(AutoVanish autoVanish) {
      if (autoVanish != null && AutoInventoryTotem.isExternalOwner(this.owner)) {
         AutoInventoryTotem autoInventoryTotem = findTotemModule();
         if (autoInventoryTotem != null) {
            autoInventoryTotem.forwardIncomingPacket(autoVanish);
         }
      }
   }

   static BooleanSupplier wrapWithFallback(BooleanSupplier booleanSupplier, boolean booleanSupplierFlag) {
      return () -> {
         if (booleanSupplier == null) {
            return booleanSupplierFlag;
         } else {
            try {
               return booleanSupplier.getAsBoolean();
            } catch (Throwable e) {
               return booleanSupplierFlag;
            }
         }
      };
   }

   public void runSupplierAction() {
      AutoInventoryTotem.releaseExternalControl(this.owner);
   }

   public TotemSupplierAdapter(Object obj, BooleanSupplier booleanSupplier) {
      this(obj, booleanSupplier, null);
   }

   public void onInventoryPlayer(InventoryPlayer inventoryPlayer) {
      if (inventoryPlayer != null && AutoInventoryTotem.isExternalOwner(this.owner)) {
         AutoInventoryTotem autoInventoryTotem = findTotemModule();
         if (autoInventoryTotem != null) {
            autoInventoryTotem.forwardOutgoingPacket(inventoryPlayer);
         }
      }
   }

   public TotemSupplierAdapter(Object object, BooleanSupplier booleanSupplier, BooleanSupplier booleanSupplier2) {
      this.owner = object;
      this.enabledCondition = wrapWithFallback(booleanSupplier, true);
      this.extraCondition = booleanSupplier2 == null ? null : wrapWithFallback(booleanSupplier2, false);
   }

   private static AutoInventoryTotem findTotemModule() {
      return ModuleRegistry.getModule(AutoInventoryTotem.class) instanceof AutoInventoryTotem autoInventoryTotem ? autoInventoryTotem : null;
   }

   public boolean isOwnerActive() {
      return AutoInventoryTotem.isExternalOwner(this.owner);
   }
}
