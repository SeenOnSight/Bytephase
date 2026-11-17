package dev.bytephase.engine.utils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class BytephaseLines {
   public static final String BYTEPHASE = " - bytephase";
   static final List<String> WATCHING_ACT_ITEMS = List.of(
      "go have some fun",
      "nobody's watching, go wild",
      "the anticheat went on lunch break",
      "the AC clocked out early",
      "free real estate",
      "the server forgot to lock the door",
      "rules are more like guidelines now",
      "the AC is touching grass",
      "no one's home, make yourself comfy",
      "the referee left the stadium",
      "it's speedrun time",
      "the cameras are unplugged"
   );
   private static String bytephaseLinesText;
   static final List<String> LEGIT_WATCHING_ITEMS = List.of(
      "fun's over, act natural",
      "the AC is back from lunch",
      "behave, it's watching again",
      "hide the evidence",
      "back to being a good citizen",
      "the referee is back",
      "party's over, turn it down",
      "legit mode: activated"
   );
   static final List<String> WATCHING_BUSINESS_ITEMS = List.of("it's awake, play it cool", "it's watching, stay legit", "all eyes on you", "no funny business");

   public static String formatBusinessWatching() {
      return formatText(WATCHING_BUSINESS_ITEMS);
   }

   public static String formatWatchingLegit() {
      return formatText(LEGIT_WATCHING_ITEMS);
   }

   private static String formatText(List<String> list) {
      String enumText = (String)list.get(ThreadLocalRandom.current().nextInt(list.size()));
      if (enumText.equals(bytephaseLinesText)) {
         enumText = (String)list.get((list.indexOf(enumText) + 1) % list.size());
      }

      bytephaseLinesText = enumText;
      return formatBytephase(enumText);
   }

   private BytephaseLines() {
   }

   public static String formatBytephase(String bytephaseText) {
      return bytephaseText.endsWith(" - bytephase") ? bytephaseText : bytephaseText + " - bytephase";
   }

   public static String formatWatchingAct() {
      return formatText(WATCHING_ACT_ITEMS);
   }
}
