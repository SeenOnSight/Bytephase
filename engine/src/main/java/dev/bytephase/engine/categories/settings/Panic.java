package dev.bytephase.engine.categories.settings;

import dev.bytephase.engine.config.ConfigSetting;
import dev.bytephase.engine.config.ModuleConfigAutosave;
import dev.bytephase.engine.gui.ButtonBase;
import dev.bytephase.engine.gui.DisabledEnabled;
import java.util.List;
import dev.bytephase.engine.categories.bots.AutoBuilder;
import ai.auth.Module;
import ai.auth.ModuleRegistry;
import dev.bytephase.engine.utils.PanicDestructUtils;
import dev.bytephase.engine.utils.IconsPngType;

public class Panic extends Module {
   private static boolean panicActivatedFlag;

   @Override
   public boolean isHiddenModule() {
      return true;
   }

   public Panic() {
      super("Panic", IconsPngType.SETTING);
      this.addSettings(
         new ConfigSetting[]{
            new ButtonBase(
                  "Disable all active modules immediately",
                  "Switch every active module off at once. Also available on a keybind.",
                  "Activate",
                  this::updatePanicActivated
               )
               .getFlagButtonActive()
         }
      );
   }

   public static boolean isActivatedPanic() {
      return panicActivatedFlag;
   }

   public void updatePanicActivated() {
      if (ModuleRegistry.getModule(AutoBuilder.class) instanceof AutoBuilder autoBuilder) {
         autoBuilder.leaveMultiplayerSession();
      }

      panicActivatedFlag = true;
      try { dev.bytephase.module.engine.BytephaseIntegration.disableAll(); }
      finally { panicActivatedFlag = false; }
      ModuleConfigAutosave.updateFlag();
      DisabledEnabled.getInstance().handleDisabledEnabled("Panic", "All active Bytephase modules were disabled", false);
   }

   @Override
   public void toggle() {
      this.updatePanicActivated();
   }
}
