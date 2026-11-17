package dev.bytephase.engine.config;
import dev.bytephase.module.combat.CombatIntegration;
import java.util.List;
import net.minecraft.entity.Entity;
/** One friend list for native combat and imported features. */
public final class FriendNameStore {
    private FriendNameStore() { }
    public static List<String> getFriendNames() { return CombatIntegration.friends(); }
    public static boolean addFriend(String name) { return CombatIntegration.addFriend(name); }
    public static boolean removeFriend(String name) { return CombatIntegration.removeFriend(name); }
    public static void clearFriends() { for (String name : getFriendNames()) removeFriend(name); }
    public static void saveFriends() { }
    public static void loadFriends() { }
    public static boolean isEmpty() { return getFriendNames().isEmpty(); }
    public static int getFriendCount() { return getFriendNames().size(); }
    public static boolean isFriend(String name) { return containsFriendIgnoreCase(name); }
    public static boolean canFlag(Entity entity) { return entity != null && isFriend(entity.getName().getString()); }
    public static boolean containsFriendIgnoreCase(String name) {
        return name != null && getFriendNames().stream().anyMatch(friend -> friend.equalsIgnoreCase(name));
    }
    public static void toggleFriend(String name) { if (isFriend(name)) removeFriend(name); else addFriend(name); }
}
