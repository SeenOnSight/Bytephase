package dev.bytephase.engine.categories.configs;

import dev.bytephase.engine.config.BooleanSetting;
import dev.bytephase.engine.config.ConfigListSetting;
import dev.bytephase.engine.config.ConfigSyncUtils;
import dev.bytephase.engine.config.ModuleConfigSerializer;
import dev.bytephase.engine.config.PresetFileStore;
import ai.auth.Module;
import dev.bytephase.engine.utils.IconsPngType;
public class Configs extends Module {
   private long timeMillis;
   public final BooleanSetting booleanBase = new BooleanSetting("Autosave", "Keep the active config in sync with your live setup automatically.", false);

   @Override
   public boolean isHiddenModule() {
      return true;
   }

   public Configs() {
      super("Configs", IconsPngType.CONFIGS);
      this.timeMillis = 0L;
      this.addSetting(new ConfigListSetting("Configs", "Manage local, community and downloaded configs."));
      this.addSetting(this.booleanBase);
      this.booleanBase.hideFromList();
      this.setEnabled(true);
   }

   @Override
   protected void onReset() {
      if (this.booleanBase.isEnabled()) {
         long timeMillis = System.currentTimeMillis();
         if (timeMillis - this.timeMillis >= 8000L) {
            String readString = PresetFileStore.readActivePresetName();
            if (readString != null) {
               this.timeMillis = timeMillis;

               try {
                  if (!ModuleConfigSerializer.updateSavedConfig(readString)) {
                     return;
                  }

                  if (ConfigSyncUtils.isSignedIn()) {
                     ConfigSyncUtils.uploadPrivateConfig(readString, result -> {});
                  }
               } catch (Exception e) {
               }
            }
         }
      }
   }
}
