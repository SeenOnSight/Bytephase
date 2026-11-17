package dev.bytephase.engine.categories.socials;

import dev.bytephase.engine.config.ConfigSetting;
import ai.auth.Module;
import dev.bytephase.engine.utils.FriendListBase;
import dev.bytephase.engine.utils.IconsPngType;
public class Friends extends Module {
   private final FriendListBase friendListBase = new FriendListBase("Friends", "Players your modules will never target.");

   public Friends() {
      super("Friends", IconsPngType.SOCIALS);
      this.addSettings(new ConfigSetting[]{this.friendListBase});
      this.setEnabled(true);
   }

   @Override
   public boolean isHiddenModule() {
      return true;
   }
}
