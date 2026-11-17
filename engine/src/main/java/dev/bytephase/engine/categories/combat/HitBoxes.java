package dev.bytephase.engine.categories.combat;

import dev.bytephase.engine.config.ConfigSetting;
import dev.bytephase.engine.config.BooleanSetting;
import dev.bytephase.engine.config.DoubleSetting;
import ai.auth.Module;
import dev.bytephase.engine.utils.IconsPngType;
public class HitBoxes extends Module {
   private final DoubleSetting expandSetting = new DoubleSetting("Expand", "Extra hit-reach added to each side of entity boxes (blocks)", 0.15, 0.0, 1.0, 0.05);
   private final BooleanSetting playersOnlySetting = new BooleanSetting("Players Only", "Only expand other players' hitboxes", true);

   public boolean isPlayersOnly() {
      return this.playersOnlySetting.isEnabled();
   }

   public float getExpandAmount() {
      return (float)this.expandSetting.getValue();
   }

   public HitBoxes() {
      super("Hit Boxes", IconsPngType.COMBAT);
      this.addSettings(new ConfigSetting[]{this.expandSetting, this.playersOnlySetting});
   }
}
