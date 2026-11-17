package dev.bytephase.engine.categories.misc;

import dev.bytephase.engine.config.ConfigSetting;
import dev.bytephase.engine.config.BooleanSetting;
import dev.bytephase.engine.config.FriendNameStore;
import dev.bytephase.engine.config.StringBase;
import dev.bytephase.engine.utils.ProtectTexts.Builder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;
import dev.bytephase.engine.utils.ProtectTexts;
import ai.auth.Module;
import ai.auth.ModuleRegistry;
import dev.bytephase.engine.utils.YouUtils;
import dev.bytephase.engine.utils.IconsPngType;

public class NameProtect extends Module {
   private final BooleanSetting booleanBase;
   private final Map<String, Integer> hideFriendMap;
   private final BooleanSetting hideFriends;
   private int hideFriendValue;
   private final StringBase stringBase = new StringBase("Alias", "Name to show instead of yours", "You");
   private long lastNanoTime;
   public static final String FRIEND = "Friend";
   public static final String PLAYER = "Player";
   private volatile ProtectTexts profileGame;
   private int hideFriendValueFriend;

   private static TextContent getBytephaseFallback(TextContent bytephaseFallback, String bytephaseFallbackText, String builderText, ProtectTexts protectTexts) {
      if (bytephaseFallback instanceof PlainTextContent plainTextContent) {
         String nameProtectText = plainTextContent.string();
         String formatText2 = formatText(nameProtectText, bytephaseFallbackText, builderText, protectTexts);
         return (TextContent)(formatText2.equals(nameProtectText) ? bytephaseFallback : PlainTextContent.of(formatText2));
      } else if (bytephaseFallback instanceof TranslatableTextContent translatableTextContent) {
         Object[] bytephaseArgs = translatableTextContent.getArgs();
         if (bytephaseArgs != null && bytephaseArgs.length != 0) {
            Object[] objs = null;

            for (int i = 0; i < bytephaseArgs.length; i++) {
               Object obj = bytephaseArgs[i];
               Object obj2 = obj;
               if (obj instanceof Text text) {
                  obj2 = getStyleSiblings(text, bytephaseFallbackText, builderText, protectTexts);
               } else if (obj instanceof String bytephaseFallbackText4) {
                  obj2 = formatText(bytephaseFallbackText4, bytephaseFallbackText, builderText, protectTexts);
               }

               if (obj2 != obj) {
                  if (objs == null) {
                     objs = (Object[])bytephaseArgs.clone();
                  }

                  objs[i] = obj2;
               }
            }

            return (TextContent)(objs == null ? bytephaseFallback : new TranslatableTextContent(translatableTextContent.getKey(), translatableTextContent.getFallback(), objs));
         } else {
            return bytephaseFallback;
         }
      } else {
         return bytephaseFallback;
      }
   }

   public static boolean canFlag(String nameProtectText) {
      return YouUtils.canFlag(nameProtectText, getUsername());
   }

   public static String getString(String stringText) {
      return dev.bytephase.module.misc.NameProtect.filter(stringText);
   }

   public static boolean isFlag() {
      return dev.bytephase.module.misc.NameProtect.active();
   }

   public NameProtect() {
      super("Name Protect", IconsPngType.MISC);
      this.booleanBase = new BooleanSetting("Hide Others", "Also hide other players' names. They show as Player1, Player2...", false);
      this.hideFriends = new BooleanSetting("Hide Friends", "Also hide your friends' names. They show as Friend1, Friend2...", false);
      this.profileGame = ProtectTexts.INSTANCE;
      this.hideFriendValueFriend = -1;
      this.hideFriendValue = -1;
      this.hideFriendMap = new HashMap<>();
      this.addSettings(new ConfigSetting[]{this.stringBase, this.booleanBase, this.hideFriends});
   }

   public String formatYouBase() {
      return dev.bytephase.module.misc.NameProtect.alias();
   }

   public static Text getDisplayName(Text displayName) {
      return dev.bytephase.module.misc.NameProtect.filter(displayName);
   }

   public static boolean isFlag5() {
      return dev.bytephase.module.misc.NameProtect.active();
   }

   private ProtectTexts getProfileGame(MinecraftClient client, int profileGameValue) {
      String username = getUsername();
      var linkedHash = FriendNameStore.getFriendNames();
      var hashSet = new HashSet();

      for (String profileGameText : linkedHash) {
         hashSet.add(profileGameText.toLowerCase(Locale.ROOT));
      }

      Builder builder = new Builder();
      if ((profileGameValue & 2) != 0) {
         for (int i = 0; i < linkedHash.size(); i++) {
            String youText = (String)linkedHash.get(i);
            if (!YouUtils.canFlag(youText, username)) {
               builder.put(youText, "Friend" + (i + 1));
            }
         }
      }

      if ((profileGameValue & 1) != 0) {
         ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
         if (networkHandler != null) {
            for (PlayerListEntry playerListEntry : networkHandler.getPlayerList()) {
               if (playerListEntry != null && playerListEntry.getProfile() != null) {
                  this.handleHideFriend(builder, playerListEntry.getProfile().name(), username, hashSet);
               }
            }
         }

         if (client.world != null) {
            for (AbstractClientPlayerEntity abstractClientPlayerEntity : client.world.getPlayers()) {
               if (abstractClientPlayerEntity != null && abstractClientPlayerEntity.getGameProfile() != null) {
                  this.handleHideFriend(builder, abstractClientPlayerEntity.getGameProfile().name(), username, hashSet);
               }
            }
         }
      }

      return builder.build();
   }

   public static String getUsername() {
      try {
         MinecraftClient instance = MinecraftClient.getInstance();
         if (instance != null && instance.getSession() != null) {
            String username = instance.getSession().getUsername();
            return username != null && !username.isEmpty() ? username : null;
         } else {
            return null;
         }
      } catch (Exception e) {
         return null;
      }
   }

   private static NameProtect getNameProtect() {
      return (NameProtect)ModuleRegistry.getModule(NameProtect.class);
   }

   ProtectTexts getHideFriend() {
      int hideFriendValue = this.getBase();
      if (hideFriendValue == 0) {
         return ProtectTexts.INSTANCE;
      } else {
         MinecraftClient instance = MinecraftClient.getInstance();
         if (instance != null && instance.isOnThread()) {
            long nanoTime = System.nanoTime();
            int playersNetwork = getPlayersNetwork(instance);
            if (hideFriendValue != this.hideFriendValueFriend || playersNetwork != this.hideFriendValue || nanoTime - this.lastNanoTime >= 500000000L) {
               this.hideFriendValueFriend = hideFriendValue;
               this.hideFriendValue = playersNetwork;
               this.lastNanoTime = nanoTime;
               this.profileGame = this.getProfileGame(instance, hideFriendValue);
            }
         }

         return this.profileGame;
      }
   }

   public static String formatYou() {
      return dev.bytephase.module.misc.NameProtect.alias();
   }

   static Text getStyleSiblings(Text styleSiblings, String styleSiblingsText, String builderText, ProtectTexts protectTexts) {
      TextContent content = styleSiblings.getContent();
      TextContent bytephaseFallback = getBytephaseFallback(content, styleSiblingsText, builderText, protectTexts);
      var siblings = styleSiblings.getSiblings();
      Text[] texts = null;

      for (int i = 0; i < siblings.size(); i++) {
         Text text2 = (Text)siblings.get(i);
         Text styleSiblings2 = getStyleSiblings(text2, styleSiblingsText, builderText, protectTexts);
         if (styleSiblings2 != text2 && texts == null) {
            texts = siblings.toArray(new Text[0]);
         }

         if (texts != null) {
            texts[i] = styleSiblings2;
         }
      }

      if (bytephaseFallback == content && texts == null) {
         return styleSiblings;
      } else {
         MutableText setStyle = MutableText.of(bytephaseFallback).setStyle(styleSiblings.getStyle());

         for (Text text3 : texts != null ? List.of(texts) : siblings) {
            setStyle.append(text3);
         }

         return setStyle;
      }
   }

   private static String formatText(String nameProtectText, String asciiLowerText, String builderText, ProtectTexts protectTexts) {
      return YouUtils.formatAsciiLower(protectTexts.getString(nameProtectText), asciiLowerText, builderText);
   }

   private int getBase() {
      return (this.booleanBase.isEnabled() ? 1 : 0) | (this.hideFriends.isEnabled() ? 2 : 0);
   }

   private static int getPlayersNetwork(MinecraftClient client) {
      ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
      int playersNetworkValue = networkHandler == null ? 0 : networkHandler.getPlayerList().size();
      int playersNetworkValue2 = client.world == null ? 0 : client.world.getPlayers().size();
      return playersNetworkValue * 65537 + playersNetworkValue2;
   }

   private void handleHideFriend(Builder builder, String friendText, String username, Set<String> set) {
      if (ProtectTexts.canFlag(friendText) && !YouUtils.canFlag(friendText, username) && !builder.has(friendText)) {
         String lowerCase = friendText.toLowerCase(Locale.ROOT);
         if (!set.contains(lowerCase)) {
            Integer hidefriendmap = this.hideFriendMap.get(lowerCase);
            if (hidefriendmap == null) {
               if (this.hideFriendMap.size() >= 4096) {
                  this.hideFriendMap.clear();
               }

               hidefriendmap = this.hideFriendMap.size() + 1;
               this.hideFriendMap.put(lowerCase, hidefriendmap);
            }

            builder.put(friendText, "Player" + hidefriendmap);
         }
      }
   }

   public static String getSubstring(String substringText) {
      return dev.bytephase.module.misc.NameProtect.filter(substringText);
   }
}
