package dev.bytephase.engine.utils;

import dev.bytephase.engine.config.ConfigSetting;
import dev.bytephase.engine.utils.MinmaxEnumType;

public class PaletteBase extends ConfigSetting {
   @Override
   public MinmaxEnumType getSettingType() {
      return MinmaxEnumType.PALETTE;
   }

   public PaletteBase(String settingName, String description) {
      super(settingName, description);
   }
}
