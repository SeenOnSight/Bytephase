package dev.bytephase.engine.gui;

import dev.bytephase.engine.BytephaseClient;
import dev.bytephase.engine.render.BytephaseRoundedUtils;
import dev.bytephase.engine.render.MatrixAmountUtils;
import dev.bytephase.engine.render.MatrixEspUtils;
import dev.bytephase.engine.render.MatrixStackUtilsAmount;
import dev.bytephase.engine.render.VertexConsumerUtilsAmount;
import dev.bytephase.engine.render.VertexTexturePeek;
import java.awt.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import dev.bytephase.engine.utils.QuintBounceType;

public class MatrixBytephase {
   private float bytephaseAmount;
   private boolean introsoundActive;
   private final MatrixStack matrixStack;
   private boolean bytephaseVertexActive;
   private float paletteWeightE;
   private final float[] introsoundTable;
   private static boolean scissorMatrixActive = false;
   private final float[] bytephaseMatrixTable;
   private long lastNanoTime;
   private int lastPaletteColorRgb;
   private float matrixAmount;
   private float bytephaseVertexAmount;
   private static final String[] bytephaseMatrixTexts = new String[]{"C", "O", "R", "Z"};
   private float matrixBytephaseAmount;
   private float paletteWeightB;
   private static final String BYTEPHASE = "BYTEPHASE";
   private boolean bytephaseActive = false;
   private VertexTexturePeek vertexTexture;
   private static final float[] matrixBytephaseTable = new float[]{0.06F, 0.95F, 0.425F, 0.575F};
   private static final float[] table = new float[]{0.04F, 0.97F, 0.58F, 0.58F};
   private static final float[] BLOB_SPREAD_FACTORS = new float[]{0.46F, 0.52F, 0.28F, 0.28F};
   private final float[] blobColorRgb;
   private static final float[] BLOB_RADIUS_FACTORS = new float[]{0.045F, 0.038F, 0.065F, 0.095F};
   private static final float[] BLOB_DRIFT_AMPLITUDES = new float[]{0.034F, 0.034F, 0.026F, 0.026F};
   private final float[] blobWeights;
   private float paletteWeightC;
   private static final float[] BLOB_DRIFT_PERIOD_SECONDS = new float[]{16.0F, 18.0F, 14.0F, 15.0F};
   private boolean matrixBytephaseActive;
   private float paletteWeightD;
   private static final float[] introsoundAmounts = new float[]{19.0F, 15.0F, 17.0F, 13.0F};
   private static final float[] introsoundBytephaseclientAmounts = new float[]{0.0F, 2.1F, 0.6F, 3.7F};
   private boolean bytephaseFlag;
   private boolean scissorRendered = false;
   private boolean bytephaseMatrixActive;
   private boolean paletteInitialized;
   private static final Color OFF_WHITE = new Color(-920587, true);
   private static final SoundEvent soundEvent = SoundEvent.of(Identifier.of("bytephase", "introsound"));
   private boolean introsoundInitialized;

   public void renderScissorMatrix(DrawContext context, int scissorMatrixValue, int randomValue, int introsoundFactor, int hashHuntBound, int wrapDegreesBound) {
      if (this.bytephaseActive) {
         BytephaseClient.fontManager.setMatrixStack(this.matrixStack);
         MatrixPeekUtilsRender.handleMatrixStack(this.matrixStack);
         float millis = (float)(System.nanoTime() - this.lastNanoTime) / 1000000.0F;
         if (millis >= 3000.0F) {
            this.bytephaseActive = false;
            this.scissorRendered = true;
            scissorMatrixActive = true;
            this.handleMatrixPeek(scissorMatrixValue, randomValue, introsoundFactor, hashHuntBound, wrapDegreesBound, -16448249);
         } else {
            if (!this.matrixBytephaseActive) {
               MinecraftClient instance = MinecraftClient.getInstance();
               if (instance.player != null) {
                  instance.player.playSound(soundEvent, 1.0F, 1.0F);
               }

               this.matrixBytephaseActive = true;
            }

            float matrixBytephaseLimit;
            if (millis < 700.0F) {
               matrixBytephaseLimit = (float)QuintBounceType.QUART_OUT.getUnaryOperator(millis / 700.0);
            } else if (millis < 2300.0F) {
               matrixBytephaseLimit = 1.0F;
            } else {
               matrixBytephaseLimit = 1.0F - (float)QuintBounceType.QUART_IN.getUnaryOperator((millis - 2300.0F) / 700.0);
            }

            float scissorMatrixValue7 = getAmount(matrixBytephaseLimit);
            this.handleMatrixPeek(scissorMatrixValue, randomValue, introsoundFactor, hashHuntBound, wrapDegreesBound, -16448249);
            context.enableScissor(scissorMatrixValue, randomValue, scissorMatrixValue + introsoundFactor, randomValue + hashHuntBound);

            try {
               this.handleAmountsIntrosound(scissorMatrixValue, randomValue, introsoundFactor, hashHuntBound, wrapDegreesBound, millis);
               this.handleMatrixStack(scissorMatrixValue, randomValue, introsoundFactor, hashHuntBound, millis);
            } finally {
               context.disableScissor();
            }

            float vertexConsumerFactor = getAmount(millis / 2300.0F);
            VertexConsumerUtilsAmount.handle(
               this.matrixStack.peek().getPositionMatrix(), scissorMatrixValue, randomValue, introsoundFactor, hashHuntBound, wrapDegreesBound, vertexConsumerFactor, scissorMatrixValue7 * 0.95F, MatrixEspUtils.releaseRightValue
            );
         }
      }
   }

   private void handleAmountMatrix(float matrixValue, float randomValue, float matrixValue3) {
      float wrapDegreesBound = 1.25F;
      this.handleMatrixPeek(matrixValue, randomValue, 210.0F, 2.5F, wrapDegreesBound, 318767103);
      float matrixBound = 210.0F * matrixValue3;
      if (!(matrixBound <= 0.01F)) {
         float matrixFactor = Math.max(matrixBound, 2.5F);
         float min2 = Math.min(1.0F, matrixBound / 2.5F);
         float matrixDivisor = MatrixPeekUtilsRender.getAmount();
         float matrixValue5 = 9.0F;
         float matrixValue6 = min2 * BytephaseRoundedUtils.panelOverlayAmount;
         Matrix4f positionMatrix = this.matrixStack.peek().getPositionMatrix();
         BytephaseRoundedUtils.handle(
            positionMatrix,
            matrixValue - matrixValue5,
            randomValue - matrixValue5,
            matrixValue + matrixFactor + matrixValue5,
            randomValue + 2.5F + matrixValue5,
            matrixValue / matrixDivisor,
            randomValue / matrixDivisor,
            matrixFactor / matrixDivisor,
            2.5F / matrixDivisor,
            wrapDegreesBound / matrixDivisor,
            this.paletteWeightB,
            this.matrixBytephaseAmount,
            this.paletteWeightE,
            0.28F * matrixValue6,
            7.0F / matrixDivisor,
            1.0F,
            false
         );
         int max2 = Math.max(1, Math.min(24, (int)Math.ceil(matrixFactor / 1.5F)));

         for (int i = 0; i < max2; i++) {
            float matrixFactor3 = (i + 0.5F) / max2;
            float rectRoundedValue = i == 0 ? matrixValue - 1.0F : matrixValue + matrixFactor * i / max2;
            float peekPositionLimit = i == max2 - 1 ? matrixValue + matrixFactor + 1.0F : matrixValue + matrixFactor * (i + 1) / max2;
            BytephaseRoundedUtils.handleRectRounded(
               positionMatrix,
               rectRoundedValue,
               randomValue - 1.0F,
               peekPositionLimit,
               randomValue + 2.5F + 1.0F,
               matrixValue / matrixDivisor,
               randomValue / matrixDivisor,
               matrixFactor / matrixDivisor,
               2.5F / matrixDivisor,
               wrapDegreesBound / matrixDivisor,
               0.0F,
               this.matrixAmount + (this.paletteWeightB - this.matrixAmount) * matrixFactor3,
               this.paletteWeightD + (this.matrixBytephaseAmount - this.paletteWeightD) * matrixFactor3,
               this.paletteWeightC + (this.paletteWeightE - this.paletteWeightC) * matrixFactor3,
               matrixValue6
            );
         }
      }
   }

   private void updateAmountAmounts() {
      int value = MatrixEspUtils.releaseRightValue & 16777215;
      if (!this.paletteInitialized || value != this.lastPaletteColorRgb) {
         this.paletteInitialized = true;
         this.lastPaletteColorRgb = value;
         int matrixBytephaseValue = 0xFF000000 | value;
         int matrixValue = MatrixAmountUtils.getValue(matrixBytephaseValue, -38.0F, 0.9F, 1.0F);
         this.paletteWeightB = MatrixAmountUtils.getAmount(matrixBytephaseValue);
         this.matrixBytephaseAmount = MatrixAmountUtils.getAmount4(matrixBytephaseValue);
         this.paletteWeightE = MatrixAmountUtils.getAmount6(matrixBytephaseValue);
         this.matrixAmount = MatrixAmountUtils.getAmount(matrixValue);
         this.paletteWeightD = MatrixAmountUtils.getAmount4(matrixValue);
         this.paletteWeightC = MatrixAmountUtils.getAmount6(matrixValue);

         for (int i = 0; i < 6; i++) {
            this.blobColorRgb[i] = 1.0F;
         }

         this.blobWeights[2] = this.getAmounts(2, this.matrixAmount, this.paletteWeightD, this.paletteWeightC);
         this.blobWeights[3] = this.getAmounts(3, this.paletteWeightB, this.matrixBytephaseAmount, this.paletteWeightE);
      }
   }

   private float getAmounts(int matrixBytephaseValue, float base, float base2, float base3) {
      float pow = (float)Math.pow(base, 2.2F);
      float pow2 = (float)Math.pow(base2, 2.2F);
      float pow3 = (float)Math.pow(base3, 2.2F);
      this.blobColorRgb[matrixBytephaseValue * 3] = pow;
      this.blobColorRgb[matrixBytephaseValue * 3 + 1] = pow2;
      this.blobColorRgb[matrixBytephaseValue * 3 + 2] = pow3;
      float matrixBytephaseBound = 0.2126F * pow + 0.7152F * pow2 + 0.0722F * pow3;
      return (float)Math.sqrt(Math.min(1.0F, 0.3F / Math.max(matrixBytephaseBound, 0.001F)));
   }

   private void updateFlagBytephase() {
      this.bytephaseActive = true;
      this.scissorRendered = false;
      this.matrixBytephaseActive = false;
      this.bytephaseMatrixActive = false;
      this.lastNanoTime = System.nanoTime();
   }

   public boolean isFlag() {
      if (scissorMatrixActive) {
         this.scissorRendered = true;
         return false;
      } else if (!this.bytephaseActive && !this.scissorRendered) {
         this.updateFlagBytephase();
         return true;
      } else {
         return this.bytephaseActive;
      }
   }

   private void handleBytephaseMatrix(float bytephaseMatrixValue, float bytephaseMatrixValue2) {
      if (this.bytephaseVertexActive) {
         try {
            for (int i = 0; i < bytephaseMatrixTexts.length; i++) {
               this.vertexTexture.handlePeekEnd(bytephaseMatrixTexts[i], bytephaseMatrixValue + this.bytephaseMatrixTable[i], bytephaseMatrixValue2, -920587, 56.716415F);
            }
         } catch (Throwable e) {
            this.bytephaseFlag = true;
            this.bytephaseMatrixActive = false;
         }
      } else {
         this.matrixStack.push();

         try {
            this.matrixStack.scale(2.4F, 2.4F, 1.0F);
            BytephaseClient.fontRenderer().handleForStringFloat("BYTEPHASE", bytephaseMatrixValue / 2.4F, bytephaseMatrixValue2 / 2.4F, OFF_WHITE);
         } finally {
            this.matrixStack.pop();
         }
      }
   }

   private void handleMatrixStack(int matrixStackValue, int randomValue, int introsoundFactor, int hashHuntBound, float matrixStackLimit) {
      float panelOverlayLimit;
      float x;
      if (matrixStackLimit >= 2300.0F) {
         float matrixStackFactor = getAmount((matrixStackLimit - 2300.0F) / 320.0F);
         panelOverlayLimit = 1.0F - matrixStackFactor * matrixStackFactor;
         x = 1.0F - 0.029999971F * matrixStackFactor * matrixStackFactor;
      } else if (matrixStackLimit < 220.0F) {
         float panelXScaled = matrixStackLimit / 220.0F;
         panelOverlayLimit = MatrixStackUtilsAmount.getAmountForFloat(panelXScaled / 0.6F);
         x = 0.94F + 0.060000002F * MatrixStackUtilsAmount.getAmount(panelXScaled, 1.6F);
      } else {
         panelOverlayLimit = 1.0F;
         x = 1.0F;
      }

      if (!(panelOverlayLimit <= 0.002F)) {
         if (!this.bytephaseMatrixActive) {
            this.updateBytephaseVertex();
         }

         this.updateAmountAmounts();
         float matrixStackValue6 = 43.5F;
         float x2 = matrixStackValue + introsoundFactor / 2.0F;
         float matrixStackValue7 = MatrixStackUtilsAmount.getAmount(randomValue + (hashHuntBound - matrixStackValue6) / 2.0F);
         float matrixStackValue8 = matrixStackValue7 + 19.0F;
         boolean matrixStackFlag = Math.abs(x - 1.0F) > 5.0E-4F;
         if (matrixStackFlag) {
            float y = matrixStackValue7 + matrixStackValue6 / 2.0F;
            this.matrixStack.push();
            this.matrixStack.translate(x2, y, 0.0F);
            this.matrixStack.scale(x, x, 1.0F);
            this.matrixStack.translate(-x2, -y, 0.0F);
         }

         float panelOverlay = BytephaseRoundedUtils.getPanelOverlay(panelOverlayLimit);

         try {
            this.handleBytephaseMatrix(MatrixStackUtilsAmount.getAmount(x2 - this.bytephaseAmount / 2.0F), matrixStackValue8 - this.bytephaseVertexAmount);
            this.handleAmountMatrix(MatrixStackUtilsAmount.getAmount(x2 - 105.0F), matrixStackValue8 + 22.0F, MatrixStackUtilsAmount.getAmount3(matrixStackLimit / 2300.0F));
         } finally {
            BytephaseRoundedUtils.handlePanelOverlay(panelOverlay);
            if (matrixStackFlag) {
               this.matrixStack.pop();
            }
         }
      }
   }

   public static boolean isScissorMatrixActive() {
      return scissorMatrixActive;
   }

   private void updateBytephaseVertex() {
      this.bytephaseMatrixActive = true;
      this.vertexTexture = this.bytephaseFlag ? null : VertexTexturePeek.getWave();
      this.bytephaseVertexActive = this.vertexTexture != null && this.vertexTexture.isBytephaseclientFlag();
      if (this.bytephaseVertexActive) {
         float bytephaseVertexTotal = 0.0F;

         for (int i = 0; i < bytephaseMatrixTexts.length; i++) {
            this.bytephaseMatrixTable[i] = bytephaseVertexTotal;
            bytephaseVertexTotal += this.vertexTexture.getBytephaseclientAmounts(bytephaseMatrixTexts[i], 56.716415F) + 3.0F;
         }

         this.bytephaseAmount = bytephaseVertexTotal - 3.0F;
         this.bytephaseVertexAmount = this.vertexTexture.getAmountParsedBytephasefontAmount(56.716415F);
      } else {
         this.bytephaseAmount = BytephaseClient.fontRenderer().getAbcdefghijklmnopqrstuvwxyzBytephasefont("BYTEPHASE") * 2.4F;
         this.bytephaseVertexAmount = 27.6F;
      }
   }

   public MatrixBytephase() {
      this.matrixBytephaseActive = false;
      this.matrixStack = new MatrixStack();
      this.bytephaseMatrixTable = new float[bytephaseMatrixTexts.length];
      this.introsoundTable = new float[24];
      this.blobColorRgb = new float[18];
      this.blobWeights = new float[]{1.0F, 1.0F, 1.0F, 1.0F};
   }

   private void handleMatrixPeek(float matrixPeekValue, float randomValue, float introsoundFactor, float hashHuntBound, float wrapDegreesBound, int matrixPeekValue6) {
      if (!(introsoundFactor <= 0.0F) && !(hashHuntBound <= 0.0F)) {
         float matrixPeekDivisor = MatrixPeekUtilsRender.getAmount();
         BytephaseRoundedUtils.handleRectRounded(
            this.matrixStack.peek().getPositionMatrix(),
            matrixPeekValue - 1.0F,
            randomValue - 1.0F,
            matrixPeekValue + introsoundFactor + 1.0F,
            randomValue + hashHuntBound + 1.0F,
            matrixPeekValue / matrixPeekDivisor,
            randomValue / matrixPeekDivisor,
            introsoundFactor / matrixPeekDivisor,
            hashHuntBound / matrixPeekDivisor,
            wrapDegreesBound / matrixPeekDivisor,
            0.0F,
            (matrixPeekValue6 >> 16 & 0xFF) / 255.0F,
            (matrixPeekValue6 >> 8 & 0xFF) / 255.0F,
            (matrixPeekValue6 & 0xFF) / 255.0F,
            (matrixPeekValue6 >>> 24 & 0xFF) / 255.0F * BytephaseRoundedUtils.panelOverlayAmount
         );
      }
   }

   private void handleAmountsIntrosound(float introsoundValue, float matrixStackValue, float introsoundFactor, float hashHuntBound, float wrapDegreesBound, float millis) {
      if (!this.introsoundActive) {
         float amountForFloat = MatrixStackUtilsAmount.getAmountForFloat(millis / 700.0F);
         float introsoundFactor4 = millis < 2300.0F ? 1.0F : 1.0F - MatrixStackUtilsAmount.getAmount3((millis - 2300.0F) / 700.0F);
         float ambientFramebufferLimit = amountForFloat * introsoundFactor4 * BytephaseRoundedUtils.panelOverlayAmount;
         if (!(ambientFramebufferLimit <= 0.003F)) {
            try {
               if (!this.introsoundInitialized) {
                  this.introsoundInitialized = true;
                  if (!BytephaseRoundedUtils.isValid()) {
                     this.introsoundActive = true;
                     return;
                  }
               }

               this.updateAmountAmounts();
               float amountTime = TwoDidUtils.getAmountTime();
               float seconds = millis / 1000.0F;
               float introsoundFactor5 = 0.86F + 0.13999999F * amountForFloat;

               for (int i = 0; i < 4; i++) {
                  float introsoundFactor6 = hashHuntBound * BLOB_DRIFT_AMPLITUDES[i];
                  this.introsoundTable[i * 4] = introsoundValue
                     + introsoundFactor * matrixBytephaseTable[i]
                     + introsoundFactor6 * (float)Math.sin((float) (Math.PI * 2) * seconds / BLOB_DRIFT_PERIOD_SECONDS[i] + introsoundBytephaseclientAmounts[i]);
                  this.introsoundTable[i * 4 + 1] = matrixStackValue
                     + hashHuntBound * table[i]
                     + introsoundFactor6 * (float)Math.cos((float) (Math.PI * 2) * seconds / introsoundAmounts[i] + introsoundBytephaseclientAmounts[i]);
                  this.introsoundTable[i * 4 + 2] = hashHuntBound * BLOB_SPREAD_FACTORS[i] * introsoundFactor5;
                  this.introsoundTable[i * 4 + 3] = BLOB_RADIUS_FACTORS[i] * this.blobWeights[i] * (i < 2 ? 1.0F : amountTime);
               }

               BytephaseRoundedUtils.handleAmbientFramebuffer(
                  this.matrixStack.peek().getPositionMatrix(), introsoundValue, matrixStackValue, introsoundFactor, hashHuntBound, wrapDegreesBound, 1.0F, ambientFramebufferLimit, this.introsoundTable, this.blobColorRgb, 4
               );
            } catch (Throwable e) {
               this.introsoundActive = true;
            }
         }
      }
   }

   private static float getAmount(float matrixBytephaseLimit) {
      return matrixBytephaseLimit < 0.0F ? 0.0F : (matrixBytephaseLimit > 1.0F ? 1.0F : matrixBytephaseLimit);
   }
}
