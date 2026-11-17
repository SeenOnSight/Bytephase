package dev.bytephase.engine.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import dev.bytephase.engine.utils.CdigestChashData;

public final class ShaBuildUtils {
   public static String formatShaBuild(String shaBuildText, CdigestChashData cdigestChashData) {
      String formatText = formatTextForString(shaBuildText);
      String shaBuildText2 = formatText.isEmpty() ? "bytephase-build|" + (cdigestChashData == null ? "" : cdigestChashData.hash()) : "bytephase-team|" + formatText;

      try {
         MessageDigest instance = MessageDigest.getInstance("SHA-256");
         byte[] digest = instance.digest(shaBuildText2.getBytes(StandardCharsets.UTF_8));
         StringBuilder stringBuilder = new StringBuilder(24);

         for (int i = 0; i < 12; i++) {
            stringBuilder.append(Character.forDigit(digest[i] >> 4 & 15, 16)).append(Character.forDigit(digest[i] & 15, 16));
         }

         return stringBuilder.toString();
      } catch (Exception e) {
         return Integer.toHexString(shaBuildText2.hashCode()).toLowerCase(Locale.ROOT);
      }
   }

   public static String formatText(String shaBuildText) {
      return formatTextForString(shaBuildText);
   }

   private ShaBuildUtils() {
   }

   public static String formatTextForString(String shaBuildText) {
      if (shaBuildText == null) {
         return "";
      } else {
         StringBuilder stringBuilder = new StringBuilder(shaBuildText.length());

         for (int i = 0; i < shaBuildText.length(); i++) {
            char upperCase = Character.toUpperCase(shaBuildText.charAt(i));
            if (upperCase >= 'A' && upperCase <= 'Z' || upperCase >= '0' && upperCase <= '9') {
               stringBuilder.append(upperCase);
            }
         }

         return stringBuilder.toString();
      }
   }
}
