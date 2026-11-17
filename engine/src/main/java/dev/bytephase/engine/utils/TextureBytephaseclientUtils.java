package dev.bytephase.engine.utils;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import dev.bytephase.engine.utils.WarmBrainrotBase;
import dev.bytephase.engine.utils.BytephaseclientWarm;

public final class TextureBytephaseclientUtils {
   private static final Map<String, Identifier> BYTEPHASECLIENT_MAP = new HashMap<>();

   public static Identifier getBytephaseclientTexture(WarmBrainrotBase warmBrainrotBase, BytephaseclientWarm bytephaseWarm) {
      Identifier bytephase_map = BYTEPHASECLIENT_MAP.get(warmBrainrotBase.formatTungDin());
      if (bytephase_map != null) {
         return bytephase_map;
      } else {
         Identifier bytephaseTexture = Identifier.of("bytephase", "brainrot/" + warmBrainrotBase.formatTungDin());
         NativeImage nativeImage = new NativeImage(bytephaseWarm.bytephaseValue, bytephaseWarm.bytephaseTextureValue, false);
         int[] bytephaseValues = bytephaseWarm.bytephaseValues;

         for (int i = 0; i < bytephaseWarm.bytephaseTextureValue; i++) {
            for (int j = 0; j < bytephaseWarm.bytephaseValue; j++) {
               nativeImage.setColorArgb(j, i, bytephaseValues[i * bytephaseWarm.bytephaseValue + j]);
            }
         }

         String bytephaseTextureText = "bytephase_brainrot_" + warmBrainrotBase.formatTungDin();
         MinecraftClient.getInstance().getTextureManager().registerTexture(bytephaseTexture, new NativeImageBackedTexture(() -> bytephaseTextureText, nativeImage));
         BYTEPHASECLIENT_MAP.put(warmBrainrotBase.formatTungDin(), bytephaseTexture);
         return bytephaseTexture;
      }
   }

   private TextureBytephaseclientUtils() {
   }
}
