package dev.bytephase.engine.categories.misc;

import dev.bytephase.engine.config.ConfigSetting;
import dev.bytephase.engine.config.RadioSetting;
import ai.auth.Module;
import dev.bytephase.engine.utils.IconsPngType;
public class BytephaseRadio extends Module {
   private final RadioSetting radioBase = new RadioSetting("Stations", "Staff playlists. Pick a station to open it.");

   @Override
   public void syncSettingHandlers() {
      this.radioBase.setSelectedStationKey("");
      super.syncSettingHandlers();
   }

   public BytephaseRadio() {
      super("Bytephase Radio", IconsPngType.MISC);
      this.addSettings(new ConfigSetting[]{this.radioBase});
   }
}
