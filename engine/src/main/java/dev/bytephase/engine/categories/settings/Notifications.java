package dev.bytephase.engine.categories.settings;

import dev.bytephase.engine.config.ConfigSetting;
import dev.bytephase.engine.config.BooleanSetting;
import ai.auth.Module;
import dev.bytephase.engine.utils.IconsPngType;
public class Notifications extends Module {
   private static Notifications singleton;
   private final BooleanSetting booleanBase;
   private final BooleanSetting toastNotifications = new BooleanSetting("Toast Notifications", "Show pop-up notifications", false);

   public static boolean isInstanceBase() {
      var modules = dev.bytephase.BytePhase.modules;
      var notifications = modules == null ? null : modules.get(dev.bytephase.module.misc.Notifications.class);
      return notifications != null && notifications.isEnabled();
   }

   public static boolean isInstanceBaseSingleton() {
      return singleton == null || singleton.booleanBase.isEnabled();
   }

   public Notifications() {
      super("Notifications", IconsPngType.SETTING);
      this.booleanBase = new BooleanSetting("Module Toggle Toasts", "Notify when a module is switched on or off", true);
      this.addSettings(new ConfigSetting[]{this.toastNotifications, this.booleanBase});
      singleton = this;
   }
}
