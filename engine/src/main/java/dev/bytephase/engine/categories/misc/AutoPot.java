package dev.bytephase.engine.categories.misc;

import dev.bytephase.engine.config.ConfigSetting;
import dev.bytephase.engine.config.DoubleSetting;
import dev.bytephase.engine.config.EnumSetting;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Hand;
import ai.auth.Module;
import dev.bytephase.engine.utils.RefillPotion;
import dev.bytephase.engine.utils.EffectStackUtils;
import dev.bytephase.engine.utils.IconsPngType;

public class AutoPot extends Module {
   public final DoubleSetting doubleHelper;
   private final RefillPotion iIiRefillPotion;
   public final EnumSetting enumHelper = new EnumSetting("Mode", "When to throw a potion", "Auto", "Auto", "While Holding");
   public final DoubleSetting pitchIIi;

   public AutoPot() {
      super("Auto Pot", IconsPngType.MISC);
      this.doubleHelper = new DoubleSetting("Health", "Throw once health is at or below this (Auto mode)", 10.0, 0.1, 20.0, 0.1);
      this.pitchIIi = new DoubleSetting("Pitch", "Downward pitch to throw at", 70.0, 50.0, 90.0, 1.0);
      this.iIiRefillPotion = new RefillPotion();
      this.addSettings(new ConfigSetting[]{this.enumHelper, this.doubleHelper, this.pitchIIi});
   }

   @Override
   protected void onReset() {
      if (mc.player != null && mc.currentScreen == null && mc.isWindowFocused()) {
         boolean equals = this.enumHelper.getCurrentOption().equals("Auto");
         if (equals && mc.player.getHealth() > this.doubleHelper.getValue()) {
            this.iIiRefillPotion.updateTime();
         } else if (this.iIiRefillPotion.canFlag(300.0)) {
            int selectedSlot;
            if (equals) {
               selectedSlot = EffectStackUtils.getSplashPotion(0, 9, StatusEffects.INSTANT_HEALTH);
               if (selectedSlot == -1) {
                  return;
               }
            } else {
               selectedSlot = mc.player.getInventory().getSelectedSlot();
            }

            float pitch = mc.player.getPitch();
            int selectedSlot2 = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(selectedSlot);
            mc.player.setPitch((float)this.pitchIIi.getValue() - 1.0F + ThreadLocalRandom.current().nextFloat());
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.setPitch(pitch);
            mc.player.getInventory().setSelectedSlot(selectedSlot2);
            this.iIiRefillPotion.updateTime();
         }
      }
   }
}
