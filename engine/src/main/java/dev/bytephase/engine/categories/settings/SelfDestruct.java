package dev.bytephase.engine.categories.settings;

import dev.bytephase.engine.config.ConfigSetting;
import dev.bytephase.engine.config.BooleanSetting;
import dev.bytephase.engine.gui.ButtonBase;
import dev.bytephase.engine.gui.DisabledEnabled;
import dev.bytephase.engine.categories.bots.AutoBuilder;
import ai.auth.Module;
import ai.auth.ModuleRegistry;
import dev.bytephase.engine.utils.PanicDestructUtils;
import dev.bytephase.engine.utils.LoadingModsUtils;
import dev.bytephase.engine.utils.BytephaseclientAttribUtils;
import dev.bytephase.engine.utils.DirExtractionUtils;
import dev.bytephase.engine.utils.IconsPngType;

public class SelfDestruct extends Module {
   private final BooleanSetting booleanBase;
   private final BooleanSetting scrubLogTraces = new BooleanSetting(
      "Scrub Log Traces",
      "Also rewrite .minecraft/logs/latest.log to remove this client's traces (mod list, mixin and console lines) for a screenshare. Your own local file only \u2014 no network, nothing else touched.",
      true
   );

   public SelfDestruct() {
      super("Self Destruct", IconsPngType.SETTING);
      this.addSettings(new ConfigSetting[]{this.scrubLogTraces});
      this.booleanBase = new BooleanSetting(
         "Delete Jar On Exit",
         "Hide the client jar from the mods folder now, then delete it from disk when Minecraft closes (crash-free). WARNING: the client is GONE afterwards and must be reinstalled \u2014 turn this off to keep the relaunch-restore behaviour instead.",
         true
      );
      this.addSettings(new ConfigSetting[]{this.booleanBase});
      this.addSettings(
         new ConfigSetting[]{
            new ButtonBase(
                  "Self Destruct",
                  "Instantly hide the whole client for a screenshare: disable-all + purge from memory, scrub logs, and (by default) delete the jar on close.",
                  "Self Destruct",
                  this::updateSelfdestructBytephase
               )
               .getFlagButtonActive()
         }
      );
   }

   @Override
   public boolean isHiddenModule() {
      return true;
   }

   @Override
   public void toggle() {
      this.updateSelfdestructBytephase();
   }

   private void updateSelfdestructBytephase() {
      if (!DirExtractionUtils.isFlag()) {
         DirExtractionUtils.updateFlag();
         if (mc.currentScreen != null) {
            mc.setScreen(null);
         }

         try (AutoCloseable disabledEnabled = DisabledEnabled.getInstance().getDisabledEnabled()) {
            if (ModuleRegistry.getModule(AutoBuilder.class) instanceof AutoBuilder autoBuilder) {
               autoBuilder.leaveMultiplayerSession();
            }

            dev.bytephase.module.engine.BytephaseIntegration.disableAll();
         } catch (Exception e) {
         }

         if (this.scrubLogTraces.isEnabled()) {
            LoadingModsUtils.isLatestLogs();
         }

         if (this.booleanBase.isEnabled()) {
            BytephaseclientAttribUtils.update();
         }

         Thread thread = new Thread(() -> {
            for (int i = 1; i <= 4; i++) {
               System.gc();

               try {
                  Thread.sleep(100L * i);
               } catch (InterruptedException e2) {
                  Thread.currentThread().interrupt();
                  return;
               }
            }

            System.gc();
         }, "bytephase-selfdestruct-gc");
         thread.setDaemon(true);
         thread.start();
      }
   }
}
