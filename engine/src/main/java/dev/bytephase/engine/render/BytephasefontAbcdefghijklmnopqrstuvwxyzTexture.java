package dev.bytephase.engine.render;

import dev.bytephase.engine.BytephaseClient;
import dev.bytephase.engine.config.MsdfFontData;
import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.awt.Color;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import dev.bytephase.engine.utils.CardStatusUtilsFlag;

public class BytephasefontAbcdefghijklmnopqrstuvwxyzTexture extends VertexTextureFont {
   private  float abcdefghijklmnopqrstuvwxyzBrownAmount;
   private static float bytephasefontParsedAmount;
   private static final Identifier identifier = Identifier.of("bytephase", "font/msdf_bytephasefont");
   private static BytephasefontAbcdefghijklmnopqrstuvwxyzTexture.Glyph bytephasefontParsedGlyph;
   private static NativeImage readBytephasefontParsed;
   private static final Map<Integer, BytephasefontAbcdefghijklmnopqrstuvwxyzTexture.Glyph> BYTEPHASEFONT_BYTEPHASECLIENT_MAP = new HashMap<>();
   private boolean fontBufferActive;
   private final boolean brownActive;
   private  float brownAmount;
   private static final Map<Long, Float> BYTEPHASEFONT_PNG_MAP = new HashMap<>();
   private static float parsedBytephasefontAmount;
   private static final String QUICK_BROWN_FOX = "The quick brown fox ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz 0123456789";
   private static final BytephasefontAbcdefghijklmnopqrstuvwxyzTexture.Glyph[] bytephasefontBytephaseclientGlyphs = new BytephasefontAbcdefghijklmnopqrstuvwxyzTexture.Glyph[256];
   private static boolean bytephasefontPngFlag;
   private static boolean bytephasefontJsonFlag = false;
   private BufferBuilder bufferBuilder;
   private static boolean bytephasefontParsedActive = false;
   private  float metricsFont;
   private static boolean fontMsdfFlag = false;

   private void handleFontBuffer(String[] strings, float[] fontBufferValues, float[] fontBufferValues2, float fontBufferValue, int fontBufferLimit, Color color) {
      MatrixStack matrixStack = BytephaseClient.fontManager.getMatrixStack();
      Matrix4f positionMatrix = matrixStack.peek().getPositionMatrix();
      float redScaled = color.getRed() / 255.0F;
      float greenScaled = color.getGreen() / 255.0F;
      float blueScaled = color.getBlue() / 255.0F;
      float fontBufferValue2 = color.getAlpha() / 255.0F * BytephaseRoundedUtils.panelOverlayAmount;
      BufferBuilder bufferBuilder = this.fontBufferActive ? this.bufferBuilder : null;

      for (int i = 0; i < fontBufferLimit; i++) {
         String orDefault = CardStatusUtilsFlag.getOrDefault(dev.bytephase.module.misc.NameProtect.filter(strings[i]));
         if (orDefault != null && !orDefault.isEmpty()) {
            updateMsdfTexture();
            if (!fontMsdfFlag) {
               break;
            }

            bufferBuilder = this.getAmountAbcdefghijklmnopqrstuvwxyz(bufferBuilder, positionMatrix, orDefault, fontBufferValues[i], fontBufferValues2 == null ? fontBufferValue : fontBufferValues2[i], redScaled, greenScaled, blueScaled, fontBufferValue2);
         }
      }

      if (this.fontBufferActive) {
         this.bufferBuilder = bufferBuilder;
      } else {
         if (bufferBuilder != null) {
            BytephaseRoundedUtils.handleTextureView(bufferBuilder.end(), identifier);
         }
      }
   }

   public BytephasefontAbcdefghijklmnopqrstuvwxyzTexture(float bytephasefontTextureValue) {
      super(getAsStream(), bytephasefontTextureValue);

      boolean flag;
      try {
         updateBytephasefontParsed();
         float bytephasefontTextureValue2 = super.getAbcdefghijklmnopqrstuvwxyzBytephasefont("The quick brown fox ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz 0123456789");
         float bytephasefontBytephaseclient = getBytephasefontBytephaseclient("The quick brown fox ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz 0123456789");
         this.brownAmount = (
               bytephasefontBytephaseclient > 0.0F ? bytephasefontTextureValue2 / bytephasefontBytephaseclient : super.getAbcdefghijklmnopqrstuvwxyzBrown() / bytephasefontParsedAmount
            )
            * 1.0F;
         this.abcdefghijklmnopqrstuvwxyzBrownAmount = super.getAbcdefghijklmnopqrstuvwxyzBrown();
         this.metricsFont = this.getMetricsFont();
         flag = !bytephasefontParsedActive;
      } catch (Throwable e) {
         e.printStackTrace();
         flag = false;
      }

      this.brownActive = flag;
   }

   @Override
   public float getAbcdefghijklmnopqrstuvwxyzBrownBrownActive() {
      return this.brownActive ? 0.716F * this.brownAmount : super.getAbcdefghijklmnopqrstuvwxyzBrownBrownActive();
   }

   @Override
   public void handleFlag(String[] texts, float[] bytephasefontTextureValues, int bytephasefontTextureValue, float vertexTextureValue, Color color) {
      if (!this.brownActive) {
         super.handleFlag(texts, bytephasefontTextureValues, bytephasefontTextureValue, vertexTextureValue, color);
      } else {
         this.handleFontBuffer(texts, bytephasefontTextureValues, null, vertexTextureValue, bytephasefontTextureValue, color);
      }
   }

   @Override
   public void handleForStringFloat(String bytephasefontTextureText, float bytephasefontTextureValue, float f13, Color color) {
      this.handleFontBuffer(bytephasefontTextureText, bytephasefontTextureValue, f13, color);
   }

   public void updateBufferBuilder() {
      BufferBuilder bufferBuilder = this.bufferBuilder;
      this.fontBufferActive = false;
      this.bufferBuilder = null;
      if (bufferBuilder != null) {
         BytephaseRoundedUtils.handleTextureView(bufferBuilder.end(), identifier);
      }
   }

   @Override
   public void handleFlag(String[] strings, float[] bytephasefontTextureValues, float[] bytephasefontTextureValues2, int bytephasefontTextureValue, Color color) {
      if (!this.brownActive) {
         super.handleFlag(strings, bytephasefontTextureValues, bytephasefontTextureValues2, bytephasefontTextureValue, color);
      } else {
         this.handleFontBuffer(strings, bytephasefontTextureValues, bytephasefontTextureValues2, 0.0F, bytephasefontTextureValue, color);
      }
   }

   public boolean isFlagBuffer() {
      if (this.brownActive && !this.fontBufferActive) {
         this.fontBufferActive = true;
         this.bufferBuilder = null;
         return true;
      } else {
         return false;
      }
   }

   @Override
   public int getAbcdefghijklmnopqrstuvwxyzBrown(int brownValue, int boxHeight) {
      return !this.brownActive
         ? super.getAbcdefghijklmnopqrstuvwxyzBrown(brownValue, boxHeight)
         : Math.round(brownValue + boxHeight / 2.0F - (this.metricsFont - 0.716F * this.brownAmount / 2.0F));
   }

   private static long getTime(int bytephasefontTextureValue, int charAt) {
      return (long)bytephasefontTextureValue << 32 | charAt & 4294967295L;
   }

   @Override
   public float getAbcdefghijklmnopqrstuvwxyzBytephasefont(String bytephasefontText) {
      bytephasefontText = CardStatusUtilsFlag.getOrDefault(dev.bytephase.module.misc.NameProtect.filter(bytephasefontText));
      if (!this.brownActive) {
         return super.getAbcdefghijklmnopqrstuvwxyzBytephasefont(bytephasefontText);
      } else if (bytephasefontText != null && !bytephasefontText.isEmpty()) {
         float bytephasefontTotal = 0.0F;
         int bytephasefontValue = -1;

         for (int i = 0; i < bytephasefontText.length(); i++) {
            char charAt = bytephasefontText.charAt(i);
            BytephasefontAbcdefghijklmnopqrstuvwxyzTexture.Glyph bytephasefontBytephaseclient = getBytephasefontBytephaseclient(charAt);
            if (bytephasefontBytephaseclient == null) {
               bytephasefontValue = charAt;
            } else {
               if (bytephasefontPngFlag && bytephasefontValue != -1) {
                  Float bytephasefont_png_map = BYTEPHASEFONT_PNG_MAP.get(getTime(bytephasefontValue, charAt));
                  if (bytephasefont_png_map != null) {
                     bytephasefontTotal += bytephasefont_png_map * this.brownAmount;
                  }
               }

               bytephasefontTotal += bytephasefontBytephaseclient.bytephasefontAmount * this.brownAmount;
               bytephasefontValue = charAt;
            }
         }

         return bytephasefontTotal;
      } else {
         return 0.0F;
      }
   }

   @Override
   public float getAbcdefghijklmnopqrstuvwxyzBrown() {
      return this.brownActive ? this.abcdefghijklmnopqrstuvwxyzBrownAmount : super.getAbcdefghijklmnopqrstuvwxyzBrown();
   }

   private static BytephasefontAbcdefghijklmnopqrstuvwxyzTexture.Glyph getBytephasefontBytephaseclient(int index) {
      BytephasefontAbcdefghijklmnopqrstuvwxyzTexture.Glyph bytephasefontBytephaseclient = index < 256 ? bytephasefontBytephaseclientGlyphs[index] : BYTEPHASEFONT_BYTEPHASECLIENT_MAP.get(index);
      if (bytephasefontBytephaseclient == null && index != 32) {
         bytephasefontBytephaseclient = bytephasefontParsedGlyph;
      }

      return bytephasefontBytephaseclient;
   }

   private static synchronized void updateBytephasefontParsed()  throws java.io.IOException {
      if (!bytephasefontJsonFlag && !bytephasefontParsedActive) {
         try {
            MsdfFontData fromJson4;
            try (InputStream resourceAsStream = BytephaseClient.class.getClassLoader().getResourceAsStream("assets/bytephase/fonts/bytephasefont.json")) {
               if (resourceAsStream == null) {
                  throw new IllegalStateException("bytephasefont.json missing from resources");
               }

               fromJson4 = (MsdfFontData)new Gson().fromJson(new InputStreamReader(resourceAsStream, StandardCharsets.UTF_8), MsdfFontData.class);
            }

            if (fromJson4 != null && fromJson4.class172 != null && fromJson4.class245 != null && fromJson4.parsedBytephasefontItems != null) {
               bytephasefontParsedAmount = fromJson4.class245.parsedBytephasefontAmount;
               parsedBytephasefontAmount = fromJson4.class245.parsedBytephasefontAmount2;
               float parsedBytephasefontAmount2 = fromJson4.class172.parsedBytephasefontAmount;
               float parsedBytephasefontAmount22 = fromJson4.class172.parsedBytephasefontAmount2;

               for (MsdfFontData.Glyph glyph : fromJson4.parsedBytephasefontItems) {
                  BYTEPHASEFONT_BYTEPHASECLIENT_MAP.put(glyph.value, new BytephasefontAbcdefghijklmnopqrstuvwxyzTexture.Glyph(glyph, parsedBytephasefontAmount2, parsedBytephasefontAmount22));
               }

               if (fromJson4.items != null) {
                  for (MsdfFontData.Kerning kerning : fromJson4.items) {
                     BYTEPHASEFONT_PNG_MAP.put(getTime(kerning.value, kerning.value2), kerning.amount);
                  }
               }

               for (int i = 0; i < bytephasefontBytephaseclientGlyphs.length; i++) {
                  bytephasefontBytephaseclientGlyphs[i] = BYTEPHASEFONT_BYTEPHASECLIENT_MAP.get(i);
               }

               bytephasefontParsedGlyph = BYTEPHASEFONT_BYTEPHASECLIENT_MAP.get(63);
               bytephasefontPngFlag = !BYTEPHASEFONT_PNG_MAP.isEmpty();

               try (InputStream resourceAsStream2 = BytephaseClient.class.getClassLoader().getResourceAsStream("assets/bytephase/fonts/bytephasefont.png")) {
                  if (resourceAsStream2 == null) {
                     throw new IllegalStateException("bytephasefont.png missing from resources");
                  }

                  readBytephasefontParsed = NativeImage.read(resourceAsStream2);
               }

               bytephasefontJsonFlag = true;
            } else {
               throw new IllegalStateException("bytephasefont.json parsed empty");
            }
         } catch (Throwable e) {
            bytephasefontParsedActive = true;
            throw e;
         }
      }
   }

   private void handleFontBuffer(String fontBufferText, float fontBufferValue, float f13, Color color) {
      fontBufferText = CardStatusUtilsFlag.getOrDefault(dev.bytephase.module.misc.NameProtect.filter(fontBufferText));
      if (!this.brownActive) {
         super.handleForStringFloat(fontBufferText, fontBufferValue, f13, color);
      } else if (fontBufferText != null && !fontBufferText.isEmpty()) {
         updateMsdfTexture();
         if (fontMsdfFlag) {
            MatrixStack matrixStack = BytephaseClient.fontManager.getMatrixStack();
            Matrix4f positionMatrix = matrixStack.peek().getPositionMatrix();
            float redScaled = color.getRed() / 255.0F;
            float greenScaled = color.getGreen() / 255.0F;
            float blueScaled = color.getBlue() / 255.0F;
            float fontBufferValue3 = color.getAlpha() / 255.0F * BytephaseRoundedUtils.panelOverlayAmount;
            BufferBuilder bufferBuilder = this.getAmountAbcdefghijklmnopqrstuvwxyz(this.fontBufferActive ? this.bufferBuilder : null, positionMatrix, fontBufferText, fontBufferValue, f13, redScaled, greenScaled, blueScaled, fontBufferValue3);
            if (this.fontBufferActive) {
               this.bufferBuilder = bufferBuilder;
            } else {
               if (bufferBuilder != null) {
                  BytephaseRoundedUtils.handleTextureView(bufferBuilder.end(), identifier);
               }
            }
         }
      }
   }

   private BufferBuilder getAmountAbcdefghijklmnopqrstuvwxyz(
      BufferBuilder begin2, Matrix4f matrix4f, String bytephasefontTextureText, float bytephasefontTextureValue, float f13, float redScaled, float greenScaled, float blueScaled, float bytephasefontTextureValue6
   ) {
      float bytephasefontTextureValue7 = f13 + this.metricsFont;
      float bytephasefontTextureTotal = bytephasefontTextureValue;
      int charAt8 = -1;

      for (int i = 0; i < bytephasefontTextureText.length(); i++) {
         char charAt = bytephasefontTextureText.charAt(i);
         BytephasefontAbcdefghijklmnopqrstuvwxyzTexture.Glyph bytephasefontBytephaseclient = getBytephasefontBytephaseclient(charAt);
         if (bytephasefontBytephaseclient == null) {
            charAt8 = charAt;
         } else {
            if (bytephasefontPngFlag && charAt8 != -1) {
               Float bytephasefont_png_map = BYTEPHASEFONT_PNG_MAP.get(getTime(charAt8, charAt));
               if (bytephasefont_png_map != null) {
                  bytephasefontTextureTotal += bytephasefont_png_map * this.brownAmount;
               }
            }

            if (bytephasefontBytephaseclient.isAmount()) {
               if (begin2 == null) {
                  begin2 = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
               }

               float bytephasefontTextureValue9 = bytephasefontTextureValue7 - bytephasefontBytephaseclient.planeTop * this.brownAmount;
               float bytephasefontTextureValue10 = bytephasefontBytephaseclient.planeWidth * this.brownAmount;
               float bytephasefontTextureValue11 = bytephasefontBytephaseclient.planeHeight * this.brownAmount;
               begin2.vertex(matrix4f, bytephasefontTextureTotal, bytephasefontTextureValue9 + bytephasefontTextureValue11, 0.0F).texture(bytephasefontBytephaseclient.textureULeft, bytephasefontBytephaseclient.glyphAmount).color(redScaled, greenScaled, blueScaled, bytephasefontTextureValue6);
               begin2.vertex(matrix4f, bytephasefontTextureTotal + bytephasefontTextureValue10, bytephasefontTextureValue9 + bytephasefontTextureValue11, 0.0F).texture(bytephasefontBytephaseclient.textureURight, bytephasefontBytephaseclient.glyphAmount).color(redScaled, greenScaled, blueScaled, bytephasefontTextureValue6);
               begin2.vertex(matrix4f, bytephasefontTextureTotal + bytephasefontTextureValue10, bytephasefontTextureValue9, 0.0F).texture(bytephasefontBytephaseclient.textureURight, bytephasefontBytephaseclient.textureVTop).color(redScaled, greenScaled, blueScaled, bytephasefontTextureValue6);
               begin2.vertex(matrix4f, bytephasefontTextureTotal, bytephasefontTextureValue9, 0.0F).texture(bytephasefontBytephaseclient.textureULeft, bytephasefontBytephaseclient.textureVTop).color(redScaled, greenScaled, blueScaled, bytephasefontTextureValue6);
            }

            bytephasefontTextureTotal += bytephasefontBytephaseclient.bytephasefontAmount * this.brownAmount;
            charAt8 = charAt;
         }
      }

      return begin2;
   }

   private static InputStream getAsStream() {
      return BytephaseClient.class.getClassLoader().getResourceAsStream("assets/bytephase/font/font.ttf");
   }

   @Override
   public void handle(String bytephasefontTextureText, float bytephasefontTextureValue, float f13, Color color) {
      this.handleFontBuffer(bytephasefontTextureText, bytephasefontTextureValue, f13, color);
   }

   private static float getBytephasefontBytephaseclient(String bytephasefontBytephaseclientText) {
      float bytephasefontBytephaseclient = 0.0F;
      int bytephasefontBytephaseclientValue = -1;

      for (int i = 0; i < bytephasefontBytephaseclientText.length(); i++) {
         char charAt = bytephasefontBytephaseclientText.charAt(i);
         BytephasefontAbcdefghijklmnopqrstuvwxyzTexture.Glyph bytephasefont_bytephase_map = BYTEPHASEFONT_BYTEPHASECLIENT_MAP.get(Integer.valueOf(charAt));
         if (bytephasefont_bytephase_map == null && charAt != ' ') {
            bytephasefont_bytephase_map = BYTEPHASEFONT_BYTEPHASECLIENT_MAP.get(63);
         }

         if (bytephasefont_bytephase_map == null) {
            bytephasefontBytephaseclientValue = charAt;
         } else {
            if (bytephasefontBytephaseclientValue != -1) {
               Float bytephasefont_png_map = BYTEPHASEFONT_PNG_MAP.get(getTime(bytephasefontBytephaseclientValue, charAt));
               if (bytephasefont_png_map != null) {
                  bytephasefontBytephaseclient += bytephasefont_png_map;
               }
            }

            bytephasefontBytephaseclient += bytephasefont_bytephase_map.bytephasefontAmount;
            bytephasefontBytephaseclientValue = charAt;
         }
      }

      return bytephasefontBytephaseclient;
   }

   private static void updateMsdfTexture() {
      if (!fontMsdfFlag && readBytephasefontParsed != null) {
         NativeImageBackedTexture nativeImageBackedTexture = new NativeImageBackedTexture(() -> "bytephase_msdf_bytephasefont", readBytephasefontParsed);
         MinecraftClient.getInstance().getTextureManager().registerTexture(identifier, nativeImageBackedTexture);
         fontMsdfFlag = true;
      }
   }

   private static final class Glyph {
      final float textureVTop;
      final float bytephasefontAmount;
      final float glyphAmount;
      final float textureULeft;
      final float planeHeight;
      final float textureURight;
      final float planeWidth;
      final float planeTop;

      boolean isAmount() {
         return this.planeWidth > 0.0F && this.planeHeight > 0.0F && (this.textureURight != this.textureULeft || this.glyphAmount != this.textureVTop);
      }

      Glyph(MsdfFontData.Glyph glyph, float glyphDivisor, float glyphDivisor2) {
         this.bytephasefontAmount = glyph.amount;
         MsdfFontData.Bounds class1912 = glyph.class1912;
         if (class1912 != null) {
            this.textureULeft = class1912.amount4 / glyphDivisor;
            this.textureURight = class1912.amount3 / glyphDivisor;
            this.textureVTop = 1.0F - class1912.amount2 / glyphDivisor2;
            this.glyphAmount = 1.0F - class1912.amount / glyphDivisor2;
         } else {
            this.textureULeft = this.textureURight = this.textureVTop = this.glyphAmount = 0.0F;
         }

         MsdfFontData.Bounds class191 = glyph.class191;
         if (class191 != null) {
            this.planeWidth = class191.amount3 - class191.amount4;
            this.planeHeight = class191.amount2 - class191.amount;
            this.planeTop = class191.amount2;
         } else {
            this.planeWidth = this.planeHeight = this.planeTop = 0.0F;
         }
      }
   }
}
