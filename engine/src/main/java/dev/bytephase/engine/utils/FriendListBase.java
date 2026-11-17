package dev.bytephase.engine.utils;

import dev.bytephase.engine.config.ConfigSetting;
import dev.bytephase.engine.config.FriendNameStore;
import java.util.List;
import dev.bytephase.engine.utils.MinmaxEnumType;

public class FriendListBase extends ConfigSetting {
   public FriendListBase(String friendText, String description) {
      super(friendText, description);
   }

   public void handle(String friendText) {
      FriendNameStore.toggleFriend(friendText);
   }

   public void handleForString(String friendText) {
      FriendNameStore.removeFriend(friendText);
   }

   @Override
   public MinmaxEnumType getSettingType() {
      return MinmaxEnumType.FRIEND_LIST;
   }

   public boolean canFlag(String friendText) {
      return FriendNameStore.containsFriendIgnoreCase(friendText);
   }

   public List<String> getItems() {
      return FriendNameStore.getFriendNames();
   }

   public void handle3(String friendText) {
      FriendNameStore.addFriend(friendText);
   }

   public void update() {
      FriendNameStore.clearFriends();
   }
}
