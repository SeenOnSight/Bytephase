package dev.bytephase.engine.utils;

public class ScaffoldTrade {
   private boolean scaffoldTradeActive = false;

   public boolean isFlag() {
      return this.scaffoldTradeActive;
   }

   public void updateFlag() {
      this.scaffoldTradeActive = true;
   }
}
