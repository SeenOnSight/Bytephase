package dev.bytephase.engine.categories.visuals;

import dev.bytephase.engine.config.ConfigSetting;
import dev.bytephase.engine.config.BooleanSetting;
import ai.auth.Module;
import dev.bytephase.engine.utils.IconsPngType;
public class NoExplosions extends Module {
   private final BooleanSetting booleanBase;
   private final BooleanSetting otherExplosions;
   private final BooleanSetting tntExplosions;
   private final BooleanSetting crystalExplosions = new BooleanSetting("Crystal Explosions", "Remove end crystal explosion effects", true);
   private final BooleanSetting creeperExplosions;

   public boolean isBase() {
      return this.crystalExplosions.isEnabled()
         && this.booleanBase.isEnabled()
         && this.tntExplosions.isEnabled()
         && this.creeperExplosions.isEnabled()
         && this.otherExplosions.isEnabled();
   }

   public NoExplosions() {
      super("No Explosions", IconsPngType.VISUAL);
      this.booleanBase = new BooleanSetting("Anchor Explosions", "Remove respawn anchor explosion effects", true);
      this.tntExplosions = new BooleanSetting("TNT Explosions", "Remove TNT explosion effects", true);
      this.creeperExplosions = new BooleanSetting("Creeper Explosions", "Remove creeper explosion effects", true);
      this.otherExplosions = new BooleanSetting("Other Explosions", "Remove all other explosion effects", true);
      this.addSettings(new ConfigSetting[]{this.crystalExplosions, this.booleanBase, this.tntExplosions, this.creeperExplosions, this.otherExplosions});
   }

   public boolean isOtherExplosions() {
      return this.otherExplosions.isEnabled();
   }
}
