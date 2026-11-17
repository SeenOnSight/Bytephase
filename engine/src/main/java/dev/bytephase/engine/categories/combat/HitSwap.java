package dev.bytephase.engine.categories.combat;

import dev.bytephase.engine.config.ConfigSetting;
import dev.bytephase.engine.config.BooleanSetting;
import dev.bytephase.engine.config.EnumSetting;
import dev.bytephase.engine.entity.ThreadClientHandlerConst;
import dev.bytephase.engine.entity.ThreadClientHandlerConst.Result;
import dev.bytephase.engine.inventory.PlayerInventoryApi;
import net.minecraft.entity.Entity;
import dev.bytephase.engine.utils.InventoryPlayer;
import dev.bytephase.engine.utils.InventoryPlayerUtils;
import ai.auth.Module;
import dev.bytephase.engine.utils.DensityBreachType;
import dev.bytephase.engine.utils.IconsPngType;
import dev.bytephase.engine.utils.SwapPreferenceData;

public class HitSwap extends Module implements PlayerInventoryApi, ThreadClientHandlerConst {
   private final EnumSetting preferredMaceSetting;
   private final BooleanSetting axesSetting;
   private final BooleanSetting swordsSetting;
   private final BooleanSetting stunSlam;
   private final BooleanSetting maces = new BooleanSetting("Maces", "When you hit, swap to the mace in your hotbar for that one hit", true);
   private final BooleanSetting smashOnly = new BooleanSetting(
      "Smash Only", "Only use the mace while you are falling far enough to smash (more than 1.5 blocks)", true
   );

   @Override
   public Result resolveAttackIntercept(Object obj, Entity entity) {
      return InventoryPlayerUtils.getPassPlan(obj, entity);
   }

   @Override
   protected void onReset() {
      InventoryPlayerUtils.updateApiDead();
   }

   @Override
   protected void onDisable() {
      InventoryPlayerUtils.handleApiSelected(this);
   }

   @Override
   public int getHandlerPriority() {
      return 100;
   }

   @Override
   public boolean isBreachOwnerActive() {
      throw new AbstractMethodError();
   }

   @Override
   public SwapPreferenceData getSwapPreference() {
      return new SwapPreferenceData(
         this.maces.isEnabled(),
         this.smashOnly.isEnabled(),
         1.5,
         DensityBreachType.getDensityText(this.preferredMaceSetting.getCurrentOption()),
         this.stunSlam.isEnabled(),
         this.axesSetting.isEnabled(),
         this.swordsSetting.isEnabled(),
         true
      );
   }

   @Override
   public int getSwapPriority() {
      return 1;
   }

   public HitSwap() {
      super("Hit Swap", IconsPngType.COMBAT);
      this.preferredMaceSetting = new EnumSetting(
         "Preferred Mace",
         "Which enchanted mace to pick if you have one; falls back to any mace",
         DensityBreachType.DENSITY.getText(),
         DensityBreachType.getTextDensityText()
      );
      this.stunSlam = new BooleanSetting(
         "Stun Slam", "Target blocking while you smash? Axe hit first to drop the shield, then the mace hit one tick later", false
      );
      this.axesSetting = new BooleanSetting("Axes", "Swap to an axe when the target is blocking with a shield, so the shield gets disabled", true);
      this.swordsSetting = new BooleanSetting("Swords", "Also swap to a sword for normal hits (keeps your current weapon's speed)", false);
      this.addSettings(new ConfigSetting[]{this.maces, this.smashOnly, this.preferredMaceSetting, this.stunSlam, this.axesSetting, this.swordsSetting});
   }

   @Override
   protected void onPacketSend(InventoryPlayer inventoryPlayer) {
      InventoryPlayerUtils.handleTimeLiving(inventoryPlayer);
   }
}
