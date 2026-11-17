package dev.bytephase.engine.utils;

import dev.bytephase.engine.BytephaseClient;
import dev.bytephase.engine.render.VertexTextureFont;

public final class HudFontUtils {
   private HudFontUtils() {
   }

   public static VertexTextureFont getLargeFont() {
      return BytephaseClient.fontManager.getLargeFont();
   }

   public static VertexTextureFont getLargeAltFont() {
      return BytephaseClient.fontManager.getLargeAltFont();
   }

   public static VertexTextureFont getSmallFont() {
      return BytephaseClient.fontManager.getSmallFont();
   }
}
