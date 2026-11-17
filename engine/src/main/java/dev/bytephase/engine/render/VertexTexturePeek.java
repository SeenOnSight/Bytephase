package dev.bytephase.engine.render;

import dev.bytephase.engine.BytephaseClient;
import dev.bytephase.engine.config.MsdfFontData;
import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public final class VertexTexturePeek {
   private boolean textureReadActive;
   private float fontScale;
   private final String descriptor;
   private boolean parsedBytephasefontActive;
   private final Identifier bytephase;
   private VertexTexturePeek.Glyph[] bytephaseClass137sParsedCor;
   private  boolean bytephaseFlag;
   private static final Map<String, VertexTexturePeek> INSTANCES = new HashMap<>();
   private final Map<Integer, VertexTexturePeek.Glyph> parsedBytephasefontGlyphMap = new HashMap<>();
   private float parsedBytephasefontAmount;
   private final Map<Long, Float> parsedBytephasefontFloatMap = new HashMap<>();
   private int hashShift;
   private long[] bytephaseTimes;
   private NativeImage readParsedBytephasefont;
   private int parsedBytephasefontValue;
   private float[] bytephaseAmounts;

   public static VertexTexturePeek getIfAbsent(String ifAbsentText) {
      return INSTANCES.computeIfAbsent(ifAbsentText, VertexTexturePeek::new);
   }

   public static VertexTexturePeek getWave() {
      return getIfAbsent("wave");
   }

   private void updateParsedBytephasefont()  throws java.io.IOException {
      String parsedBytephasefontText = "assets/bytephase/fonts/hud/" + this.descriptor + "/";

      MsdfFontData fromJson4;
      try (InputStream asStream = getAsStream(parsedBytephasefontText + "font.json")) {
         if (asStream == null) {
            throw new IllegalStateException("missing " + parsedBytephasefontText + "font.json");
         }

         fromJson4 = (MsdfFontData)new Gson().fromJson(new InputStreamReader(asStream, StandardCharsets.UTF_8), MsdfFontData.class);
      }

      if (fromJson4 != null && fromJson4.class172 != null && fromJson4.class245 != null && fromJson4.parsedBytephasefontItems != null) {
         this.fontScale = fromJson4.class245.parsedBytephasefontAmount;
         this.parsedBytephasefontAmount = fromJson4.class245.parsedBytephasefontAmount2;
         float parsedBytephasefontAmount = fromJson4.class172.parsedBytephasefontAmount;
         float parsedBytephasefontAmount2 = fromJson4.class172.parsedBytephasefontAmount2;

         for (MsdfFontData.Glyph glyph : fromJson4.parsedBytephasefontItems) {
            this.parsedBytephasefontGlyphMap.put(glyph.value, new VertexTexturePeek.Glyph(glyph, parsedBytephasefontAmount, parsedBytephasefontAmount2));
         }

         if (fromJson4.items != null) {
            for (MsdfFontData.Kerning kerning : fromJson4.items) {
               this.parsedBytephasefontFloatMap.put(getTime(kerning.value, kerning.value2), kerning.amount);
            }
         }

         try (InputStream asStream2 = getAsStream(parsedBytephasefontText + "font.png")) {
            if (asStream2 == null) {
               throw new IllegalStateException("missing " + parsedBytephasefontText + "font.png");
            }

            this.readParsedBytephasefont = NativeImage.read(asStream2);
         }

         int parsedBytephasefontValue = -1;

         for (int parsedBytephasefontLimit : this.parsedBytephasefontGlyphMap.keySet()) {
            if (parsedBytephasefontLimit >= 0 && parsedBytephasefontLimit <= 65535 && parsedBytephasefontLimit > parsedBytephasefontValue) {
               parsedBytephasefontValue = parsedBytephasefontLimit;
            }
         }

         VertexTexturePeek.Glyph[] bytephaseClass137s = new VertexTexturePeek.Glyph[parsedBytephasefontValue + 1];

         for (Entry entry : this.parsedBytephasefontGlyphMap.entrySet()) {
            int index = (Integer)entry.getKey();
            if (index >= 0 && index <= 65535) {
               bytephaseClass137s[index] = (VertexTexturePeek.Glyph)entry.getValue();
            }
         }

         this.bytephaseClass137sParsedCor = bytephaseClass137s;
         int value = 0;

         for (long keyItem : this.parsedBytephasefontFloatMap.keySet()) {
            if (keyItem >>> 32 <= 65535L && (keyItem & 4294967295L) <= 65535L) {
               value++;
            }
         }

         int parsedBytephasefontSize = 2;

         while (parsedBytephasefontSize < value * 2) {
            parsedBytephasefontSize <<= 1;
         }

         long[] bytephaseTimes = new long[parsedBytephasefontSize];
         float[] bytephaseAmounts = new float[parsedBytephasefontSize];
         Arrays.fill(bytephaseTimes, Long.MIN_VALUE);
         int value2 = 64 - Integer.numberOfTrailingZeros(parsedBytephasefontSize);

         for (Entry entryItem : this.parsedBytephasefontFloatMap.entrySet()) {
            long parsedBytephasefontValue3 = (Long)entryItem.getKey();
            if (parsedBytephasefontValue3 >>> 32 <= 65535L && (parsedBytephasefontValue3 & 4294967295L) <= 65535L) {
               int index2 = (int)(parsedBytephasefontValue3 * -7046029254386353131L >>> value2);

               while (bytephaseTimes[index2] != Long.MIN_VALUE) {
                  index2 = index2 + 1 & parsedBytephasefontSize - 1;
               }

               bytephaseTimes[index2] = parsedBytephasefontValue3;
               bytephaseAmounts[index2] = (Float)entryItem.getValue();
            }
         }

         this.bytephaseTimes = bytephaseTimes;
         this.bytephaseAmounts = bytephaseAmounts;
         this.hashShift = value2;
         this.parsedBytephasefontValue = value;
         this.parsedBytephasefontActive = true;
      } else {
         throw new IllegalStateException("empty descriptor: " + this.descriptor);
      }
   }

   public static VertexTexturePeek getSuisse() {
      return getIfAbsent("suisse");
   }

   public float getBytephaseclientAmounts(String bytephaseText, float bytephaseValue) {
      if (this.parsedBytephasefontActive && bytephaseText != null && !bytephaseText.isEmpty()) {
         float bytephaseFactor = getAmount(bytephaseValue);
         float bytephaseAmounts = 0.0F;
         int vertexTextureValue = -1;

         for (int i = 0; i < bytephaseText.length(); i++) {
            char charAt = bytephaseText.charAt(i);
            VertexTexturePeek.Glyph bytephase = this.getBytephaseclient(charAt);
            if (bytephase == null) {
               vertexTextureValue = charAt;
            } else {
               if (vertexTextureValue != -1) {
                  int bytephaseTimes = this.getBytephaseclientTimes(getTime(vertexTextureValue, charAt));
                  if (bytephaseTimes >= 0) {
                     bytephaseAmounts += this.bytephaseAmounts[bytephaseTimes] * bytephaseFactor;
                  }
               }

               bytephaseAmounts += bytephase.bytephaseAmount * bytephaseFactor;
               vertexTextureValue = charAt;
            }
         }

         return bytephaseAmounts;
      } else {
         return 0.0F;
      }
   }

   public void handleAmountPosition(String positionText, float positionValue, float positionValue2, int vertexTextureValue, int positionValue4, float bytephaseValue) {
      if (this.isBytephaseclientFlag() && positionText != null && !positionText.isEmpty()) {
         this.updateTextureRead();
         if (this.textureReadActive) {
            float bytephaseAmounts = this.getBytephaseclientAmounts(positionText, bytephaseValue);
            if (bytephaseAmounts <= 0.0F) {
               this.handlePeekEnd(positionText, positionValue, positionValue2, vertexTextureValue, bytephaseValue);
            } else {
               MatrixStack matrixStack = BytephaseClient.fontManager.getMatrixStack();
               Matrix4f positionMatrix = matrixStack.peek().getPositionMatrix();
               float positionFactor = getAmount(bytephaseValue);
               float positionValue6 = positionValue2 + this.parsedBytephasefontAmount * positionFactor;
               float positionTotal = positionValue;
               int charAt7 = -1;
               BufferBuilder bufferBuilder = null;

               for (int i = 0; i < positionText.length(); i++) {
                  char charAt = positionText.charAt(i);
                  VertexTexturePeek.Glyph bytephase = this.getBytephaseclient(charAt);
                  if (bytephase == null) {
                     charAt7 = charAt;
                  } else {
                     if (charAt7 != -1) {
                        int bytephaseTimes = this.getBytephaseclientTimes(getTime(charAt7, charAt));
                        if (bytephaseTimes >= 0) {
                           positionTotal += this.bytephaseAmounts[bytephaseTimes] * positionFactor;
                        }
                     }

                     if (bytephase.isAmount()) {
                        if (bufferBuilder == null) {
                           bufferBuilder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                        }

                        float positionValue8 = positionValue6 - bytephase.glyphOffsetY * positionFactor;
                        float positionValue9 = bytephase.glyphAmount * positionFactor;
                        float positionValue10 = bytephase.positionAmount * positionFactor;
                        int positionValue11 = getValue(vertexTextureValue, positionValue4, (positionTotal - positionValue) / bytephaseAmounts);
                        int positionValue12 = getValue(vertexTextureValue, positionValue4, (positionTotal + positionValue9 - positionValue) / bytephaseAmounts);
                        handleVertexTexture(bufferBuilder, positionMatrix, positionTotal, positionValue8 + positionValue10, bytephase.uvLeft, bytephase.textureAmount, positionValue11);
                        handleVertexTexture(bufferBuilder, positionMatrix, positionTotal + positionValue9, positionValue8 + positionValue10, bytephase.uvRight, bytephase.textureAmount, positionValue12);
                        handleVertexTexture(bufferBuilder, positionMatrix, positionTotal + positionValue9, positionValue8, bytephase.uvRight, bytephase.uvTop, positionValue12);
                        handleVertexTexture(bufferBuilder, positionMatrix, positionTotal, positionValue8, bytephase.uvLeft, bytephase.uvTop, positionValue11);
                     }

                     positionTotal += bytephase.bytephaseAmount * positionFactor;
                     charAt7 = charAt;
                  }
               }

               if (bufferBuilder != null) {
                  BytephaseRoundedUtils.handleTextureView(bufferBuilder.end(), this.bytephase);
               }
            }
         }
      }
   }

   private static void handleVertexTexture(BufferBuilder buffer, Matrix4f matrix, float vertexTextureValue, float vertexTextureValue2, float vertexTextureValue3, float vertexTextureValue4, int vertexTextureValue5) {
      float vertexTextureValue6 = (vertexTextureValue5 >>> 24 & 0xFF) / 255.0F * BytephaseRoundedUtils.panelOverlayAmount;
      buffer.vertex(matrix, vertexTextureValue, vertexTextureValue2, 0.0F).texture(vertexTextureValue3, vertexTextureValue4).color((vertexTextureValue5 >> 16 & 0xFF) / 255.0F, (vertexTextureValue5 >> 8 & 0xFF) / 255.0F, (vertexTextureValue5 & 0xFF) / 255.0F, vertexTextureValue6);
   }

   public float getAmountFlag(char ch, float vertexTextureValue) {
      if (!this.parsedBytephasefontActive) {
         return 0.0F;
      } else {
         VertexTexturePeek.Glyph bytephase = this.getBytephaseclient(ch);
         if (bytephase == null) {
            return 0.0F;
         } else {
            float vertexTextureValue2 = 0.0F;
            return vertexTextureValue2 + bytephase.bytephaseAmount * getAmount(vertexTextureValue);
         }
      }
   }

   public static VertexTexturePeek getRegular() {
      return getIfAbsent("sf_regular");
   }

   private BufferBuilder getAmountTexture(
      BufferBuilder amountTexture, Matrix4f matrix4f, String textureText, int textureValue, int textureLimit, float peekEndValue, float textureValue3, float textureFactor, float textureValue4, float textureValue5, float textureValue6, float textureValue7
   ) {
      float x2 = peekEndValue;
      int vertexTextureValue = -1;

      for (int i = textureValue; i < textureLimit; i++) {
         char charAt = textureText.charAt(i);
         VertexTexturePeek.Glyph bytephase = this.getBytephaseclient(charAt);
         if (bytephase == null) {
            vertexTextureValue = charAt;
         } else {
            if (vertexTextureValue != -1) {
               int bytephaseTimes = this.getBytephaseclientTimes(getTime(vertexTextureValue, charAt));
               if (bytephaseTimes >= 0) {
                  x2 += this.bytephaseAmounts[bytephaseTimes] * textureFactor;
               }
            }

            if (bytephase.isAmount()) {
               if (amountTexture == null) {
                  amountTexture = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
               }

               float textureValue9 = textureValue3 - bytephase.glyphOffsetY * textureFactor;
               float textureValue10 = bytephase.glyphAmount * textureFactor;
               float textureValue11 = bytephase.positionAmount * textureFactor;
               amountTexture.vertex(matrix4f, x2, textureValue9 + textureValue11, 0.0F).texture(bytephase.uvLeft, bytephase.textureAmount).color(textureValue4, textureValue5, textureValue6, textureValue7);
               amountTexture.vertex(matrix4f, x2 + textureValue10, textureValue9 + textureValue11, 0.0F).texture(bytephase.uvRight, bytephase.textureAmount).color(textureValue4, textureValue5, textureValue6, textureValue7);
               amountTexture.vertex(matrix4f, x2 + textureValue10, textureValue9, 0.0F).texture(bytephase.uvRight, bytephase.uvTop).color(textureValue4, textureValue5, textureValue6, textureValue7);
               amountTexture.vertex(matrix4f, x2, textureValue9, 0.0F).texture(bytephase.uvLeft, bytephase.uvTop).color(textureValue4, textureValue5, textureValue6, textureValue7);
            }

            x2 += bytephase.bytephaseAmount * textureFactor;
            vertexTextureValue = charAt;
         }
      }

      return amountTexture;
   }

   private int getBytephaseclientTimes(long bytephaseValue) {
      if (this.parsedBytephasefontValue == 0) {
         return -1;
      } else {
         long[] bytephaseTimes = this.bytephaseTimes;
         int bytephaseValue2 = bytephaseTimes.length - 1;
         int index = (int)(bytephaseValue * -7046029254386353131L >>> this.hashShift);

         while (true) {
            long bytephaseValue3 = bytephaseTimes[index];
            if (bytephaseValue3 == bytephaseValue) {
               return index;
            }

            if (bytephaseValue3 == Long.MIN_VALUE) {
               return -1;
            }

            index = index + 1 & bytephaseValue2;
         }
      }
   }

   private VertexTexturePeek.Glyph getBytephaseclient(int index) {
      return index < this.bytephaseClass137sParsedCor.length ? this.bytephaseClass137sParsedCor[index] : null;
   }

   public static VertexTexturePeek getIcon() {
      return getIfAbsent("icon");
   }

   public void handlePeekEnd(String peekEndText, float[] peekEndValues, int peekEndLimit, float peekEndValue, int peekEndValue2, float vertexTextureValue) {
      if (this.isBytephaseclientFlag() && peekEndText != null && peekEndLimit > 0) {
         this.updateTextureRead();
         if (this.textureReadActive) {
            MatrixStack matrixStack = BytephaseClient.fontManager.getMatrixStack();
            Matrix4f positionMatrix = matrixStack.peek().getPositionMatrix();
            float peekEndFactor = getAmount(vertexTextureValue);
            float peekEndValue4 = (peekEndValue2 >>> 24 & 0xFF) / 255.0F * BytephaseRoundedUtils.panelOverlayAmount;
            float peekEndValue5 = (peekEndValue2 >> 16 & 0xFF) / 255.0F;
            float peekEndValue6 = (peekEndValue2 >> 8 & 0xFF) / 255.0F;
            float peekEndValue7 = (peekEndValue2 & 0xFF) / 255.0F;
            float peekEndValue8 = peekEndValue + this.parsedBytephasefontAmount * peekEndFactor;
            BufferBuilder bufferBuilder = null;

            for (int i = 0; i < peekEndLimit; i++) {
               bufferBuilder = this.getAmountTexture(bufferBuilder, positionMatrix, peekEndText, i, i + 1, peekEndValues[i], peekEndValue8, peekEndFactor, peekEndValue5, peekEndValue6, peekEndValue7, peekEndValue4);
            }

            if (bufferBuilder != null) {
               BytephaseRoundedUtils.handleTextureView(bufferBuilder.end(), this.bytephase);
            }
         }
      }
   }

   private static float getAmount(float vertexTextureValue) {
      return vertexTextureValue * 0.5F;
   }

   private static long getTime(int vertexTextureValue, int charAt) {
      return (long)vertexTextureValue << 32 | charAt & 4294967295L;
   }

   private VertexTexturePeek(String descriptor) {
      this.bytephaseClass137sParsedCor = new VertexTexturePeek.Glyph[0];
      this.bytephaseTimes = new long[0];
      this.bytephaseAmounts = new float[0];
      this.descriptor = descriptor;
      this.bytephase = Identifier.of("bytephase", "font/hud_" + descriptor);

      try {
         this.updateParsedBytephasefont();
      } catch (Throwable e) {
         this.bytephaseFlag = true;
         e.printStackTrace();
      }
   }

   private static int getValue(int vertexTextureValue, int vertexTextureValue2, float vertexTextureFactor) {
      vertexTextureFactor = vertexTextureFactor < 0.0F ? 0.0F : (vertexTextureFactor > 1.0F ? 1.0F : vertexTextureFactor);
      int vertexTextureValue3 = vertexTextureValue >>> 24 & 0xFF;
      int vertexTextureValue4 = vertexTextureValue >> 16 & 0xFF;
      int vertexTextureValue5 = vertexTextureValue >> 8 & 0xFF;
      int vertexTextureValue6 = vertexTextureValue & 0xFF;
      int vertexTextureValue7 = vertexTextureValue2 >>> 24 & 0xFF;
      int vertexTextureValue8 = vertexTextureValue2 >> 16 & 0xFF;
      int vertexTextureValue9 = vertexTextureValue2 >> 8 & 0xFF;
      int vertexTextureValue10 = vertexTextureValue2 & 0xFF;
      return (int)(vertexTextureValue3 + (vertexTextureValue7 - vertexTextureValue3) * vertexTextureFactor) << 24
         | (int)(vertexTextureValue4 + (vertexTextureValue8 - vertexTextureValue4) * vertexTextureFactor) << 16
         | (int)(vertexTextureValue5 + (vertexTextureValue9 - vertexTextureValue5) * vertexTextureFactor) << 8
         | (int)(vertexTextureValue6 + (vertexTextureValue10 - vertexTextureValue6) * vertexTextureFactor);
   }

   private void updateTextureRead() {
      if (!this.textureReadActive && this.readParsedBytephasefont != null) {
         NativeImageBackedTexture nativeImageBackedTexture = new NativeImageBackedTexture(() -> "hud_font_" + this.descriptor, this.readParsedBytephasefont);
         MinecraftClient.getInstance().getTextureManager().registerTexture(this.bytephase, nativeImageBackedTexture);
         this.textureReadActive = true;
      }
   }

   public void handlePeekEnd(String peekEndText, float peekEndValue, float peekEndValue2, int peekEndValue3, float vertexTextureValue) {
      if (this.isBytephaseclientFlag() && peekEndText != null && !peekEndText.isEmpty()) {
         this.updateTextureRead();
         if (this.textureReadActive) {
            MatrixStack matrixStack = BytephaseClient.fontManager.getMatrixStack();
            Matrix4f positionMatrix = matrixStack.peek().getPositionMatrix();
            float peekEndFactor = getAmount(vertexTextureValue);
            float peekEndValue5 = (peekEndValue3 >>> 24 & 0xFF) / 255.0F * BytephaseRoundedUtils.panelOverlayAmount;
            float peekEndValue6 = (peekEndValue3 >> 16 & 0xFF) / 255.0F;
            float peekEndValue7 = (peekEndValue3 >> 8 & 0xFF) / 255.0F;
            float peekEndValue8 = (peekEndValue3 & 0xFF) / 255.0F;
            float peekEndValue9 = peekEndValue2 + this.parsedBytephasefontAmount * peekEndFactor;
            BufferBuilder amountTexture = this.getAmountTexture(null, positionMatrix, peekEndText, 0, peekEndText.length(), peekEndValue, peekEndValue9, peekEndFactor, peekEndValue6, peekEndValue7, peekEndValue8, peekEndValue5);
            if (amountTexture != null) {
               BytephaseRoundedUtils.handleTextureView(amountTexture.end(), this.bytephase);
            }
         }
      }
   }

   public void handlePeekEnd(String[] strings, float[] peekEndValues, float[] peekEndValues2, int[] peekEndValues3, int peekEndLimit, float peekEndValue) {
      if (this.isBytephaseclientFlag()) {
         MatrixStack matrixStack = BytephaseClient.fontManager.getMatrixStack();
         Matrix4f positionMatrix = matrixStack.peek().getPositionMatrix();
         float peekEndFactor = getAmount(peekEndValue);
         BufferBuilder bufferBuilder = null;

         for (int i = 0; i < peekEndLimit; i++) {
            String peekEndText = strings[i];
            if (peekEndText != null && !peekEndText.isEmpty()) {
               this.updateTextureRead();
               if (!this.textureReadActive) {
                  break;
               }

               int peekEndValue2 = peekEndValues3[i];
               float peekEndValue3 = (peekEndValue2 >>> 24 & 0xFF) / 255.0F * BytephaseRoundedUtils.panelOverlayAmount;
               float peekEndValue4 = (peekEndValue2 >> 16 & 0xFF) / 255.0F;
               float peekEndValue5 = (peekEndValue2 >> 8 & 0xFF) / 255.0F;
               float peekEndValue6 = (peekEndValue2 & 0xFF) / 255.0F;
               float peekEndValue7 = peekEndValues2[i] + this.parsedBytephasefontAmount * peekEndFactor;
               bufferBuilder = this.getAmountTexture(bufferBuilder, positionMatrix, peekEndText, 0, peekEndText.length(), peekEndValues[i], peekEndValue7, peekEndFactor, peekEndValue4, peekEndValue5, peekEndValue6, peekEndValue3);
            }
         }

         if (bufferBuilder != null) {
            BytephaseRoundedUtils.handleTextureView(bufferBuilder.end(), this.bytephase);
         }
      }
   }

   public boolean isBytephaseclientFlag() {
      return this.parsedBytephasefontActive && !this.bytephaseFlag;
   }

   public float getAmountForFloat(float vertexTextureValue) {
      return this.fontScale * getAmount(vertexTextureValue);
   }

   private static InputStream getAsStream(String streamText) {
      return BytephaseClient.class.getClassLoader().getResourceAsStream(streamText);
   }

   public static VertexTexturePeek getIconsminced() {
      return getIfAbsent("iconsminced");
   }

   public float getAmountParsedBytephasefontAmount(float vertexTextureValue) {
      return this.parsedBytephasefontAmount * getAmount(vertexTextureValue);
   }

   private static final class Glyph {
      final float uvRight;
      final float positionAmount;
      final float textureAmount;
      final float bytephaseAmount;
      final float glyphAmount;
      final float uvTop;
      final float uvLeft;
      final float glyphOffsetY;

      Glyph(MsdfFontData.Glyph glyph, float glyphDivisor, float glyphDivisor2) {
         this.bytephaseAmount = glyph.amount;
         MsdfFontData.Bounds class1912 = glyph.class1912;
         if (class1912 != null) {
            this.uvLeft = class1912.amount4 / glyphDivisor;
            this.uvRight = class1912.amount3 / glyphDivisor;
            this.uvTop = 1.0F - class1912.amount2 / glyphDivisor2;
            this.textureAmount = 1.0F - class1912.amount / glyphDivisor2;
         } else {
            this.uvLeft = this.uvRight = this.uvTop = this.textureAmount = 0.0F;
         }

         MsdfFontData.Bounds class191 = glyph.class191;
         if (class191 != null) {
            this.glyphAmount = class191.amount3 - class191.amount4;
            this.positionAmount = class191.amount2 - class191.amount;
            this.glyphOffsetY = class191.amount2;
         } else {
            this.glyphAmount = this.positionAmount = this.glyphOffsetY = 0.0F;
         }
      }

      boolean isAmount() {
         return this.glyphAmount > 0.0F && this.positionAmount > 0.0F && (this.uvRight != this.uvLeft || this.textureAmount != this.uvTop);
      }
   }
}
