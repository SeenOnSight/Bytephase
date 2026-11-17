package dev.bytephase.engine.utils;

import java.util.List;
import dev.bytephase.engine.utils.AmountLoft;
import dev.bytephase.engine.utils.ForeShinScale;
import dev.bytephase.engine.utils.LoftLatheFlag;
import dev.bytephase.engine.utils.ForeEar;

public final class BytephaseclientWarm {
   public final float boundsMinZ;
   public final float scaleAmount;
   public final int[] bytephaseWarmTable;
   public final float bytephaseWarmAmount;
   public final int partIndex;
   public final float boundsMaxY;
   public final int[] bytephaseValues;
   public final float[] personCustomTable;
   public final String bytephaseWarmText;
   public final String[] bytephaseWarmTexts;
   public final float boundsMaxX;
   public final int bytephaseValue;
   public final int bytephaseTextureValue;
   public final int bytephaseWarmValue;
   public final int listSize;
   public final float[][] vectorEmitFloats;
   public final int personCustomValue;
   public final float boundsMaxZ;

   public boolean canFlag(int bytephaseWarmValue, int vectorEmitValue) {
      int bytephaseWarmValue2 = bytephaseWarmValue;

      while (bytephaseWarmValue2 >= 0) {
         if (bytephaseWarmValue2 == vectorEmitValue) {
            return true;
         }

         bytephaseWarmValue2 = this.bytephaseWarmTable[bytephaseWarmValue2];
      }

      return false;
   }

   private static AmountLoft getAmount(float[] bytephaseWarmValues, int index, AmountLoft amountLoft) {
      amountLoft.matrixM0F = bytephaseWarmValues[index];
      amountLoft.matrixMFloat = bytephaseWarmValues[index + 1];
      amountLoft.matrixM02 = bytephaseWarmValues[index + 2];
      amountLoft.translateX = bytephaseWarmValues[index + 3];
      amountLoft.matrixM10 = bytephaseWarmValues[index + 4];
      amountLoft.matrixM11 = bytephaseWarmValues[index + 5];
      amountLoft.matrixM12 = bytephaseWarmValues[index + 6];
      amountLoft.translateY = bytephaseWarmValues[index + 7];
      amountLoft.matrixM20 = bytephaseWarmValues[index + 8];
      amountLoft.loftAmount = bytephaseWarmValues[index + 9];
      amountLoft.matrixM22 = bytephaseWarmValues[index + 10];
      amountLoft.translateZ = bytephaseWarmValues[index + 11];
      return amountLoft;
   }

   public float getMax() {
      if (this.personCustomValue < 0) {
         return 0.0F;
      } else {
         float bytephaseWarmValue = this.personCustomTable[3 * this.personCustomValue + 1];
         float bytephaseWarmBound = 0.0F;

         for (int i = 0; i < this.listSize; i++) {
            if (this.canFlag(i, this.personCustomValue)) {
               float[] bytephaseWarmValues = this.vectorEmitFloats[i];

               for (int j = 0; j < bytephaseWarmValues.length; j += 9) {
                  bytephaseWarmBound = Math.max(bytephaseWarmBound, bytephaseWarmValue - bytephaseWarmValues[j + 1]);
               }
            }
         }

         return bytephaseWarmBound;
      }
   }

   public void handleAmounts(ForeShinScale foreShinScale, float[] bytephaseWarmValues) {
      AmountLoft amountLoft = new AmountLoft();
      AmountLoft amountLoft2 = new AmountLoft();

      for (int i = 0; i < this.listSize; i++) {
         float bytephaseWarmValue = this.personCustomTable[3 * i];
         float bytephaseWarmValue2 = this.personCustomTable[3 * i + 1];
         float bytephaseWarmValue3 = this.personCustomTable[3 * i + 2];
         amountLoft2.getAmount()
            .getAmountTranslateX(bytephaseWarmValue + foreShinScale.offsetX[i], bytephaseWarmValue2 + foreShinScale.foreShinTable[i], bytephaseWarmValue3 + foreShinScale.offsetZ[i])
            .getAmount3(foreShinScale.table[i])
            .getAmount(foreShinScale.rotationZ[i])
            .getAmountForFloat(foreShinScale.rotationX[i])
            .getAmountAmountLoft(foreShinScale.scaleX[i], foreShinScale.scaleY[i], foreShinScale.scaleZ[i])
            .getAmountTranslateX(-bytephaseWarmValue, -bytephaseWarmValue2, -bytephaseWarmValue3);
         int bytephaseWarmValue4 = this.bytephaseWarmTable[i];
         if (bytephaseWarmValue4 < 0) {
            amountLoft.getAmount(amountLoft2);
         } else {
            getAmount(bytephaseWarmValues, bytephaseWarmValue4 * 21, amountLoft).getAmountForAmountLoft(amountLoft2);
         }

         amountLoft.handleAmount(bytephaseWarmValues, i * 21);
         amountLoft.handleAmountForFloatArrayInt(bytephaseWarmValues, i * 21 + 12);
      }
   }

   public int getTexts(String bytephaseWarmText) {
      for (int i = 0; i < this.listSize; i++) {
         if (this.bytephaseWarmTexts[i].equals(bytephaseWarmText)) {
            return i;
         }
      }

      return -1;
   }

   BytephaseclientWarm(String text, LoftLatheFlag loftLatheFlag, int[] bytephaseWarmValues, List<ForeEar> list, ForeEar foreEar) {
      this.bytephaseWarmText = text;
      this.bytephaseValue = loftLatheFlag.atlasWidth;
      this.bytephaseTextureValue = loftLatheFlag.atlasHeight;
      this.bytephaseValues = bytephaseWarmValues != null ? bytephaseWarmValues : loftLatheFlag.loftLatheTable;
      this.bytephaseWarmValue = loftLatheFlag.getMax();
      this.listSize = list.size();
      this.bytephaseWarmTexts = new String[this.listSize];
      this.bytephaseWarmTable = new int[this.listSize];
      this.personCustomTable = new float[3 * this.listSize];
      this.vectorEmitFloats = new float[this.listSize][];
      int value = 0;
      float amount = Float.MAX_VALUE;
      float wrapDegreesBound = Float.MAX_VALUE;
      float maxValue3 = Float.MAX_VALUE;
      float maxValue4 = -Float.MAX_VALUE;
      float pilotStatusBound = -Float.MAX_VALUE;
      float maxValue6 = -Float.MAX_VALUE;

      for (int i = 0; i < this.listSize; i++) {
         ForeEar atla = (ForeEar)list.get(i);
         this.bytephaseWarmTexts[i] = atla.foreEarText;
         this.bytephaseWarmTable[i] = atla.foreEar == null ? -1 : atla.foreEar.boneIndex;
         if (this.bytephaseWarmTable[i] >= i) {
            throw new IllegalStateException("bone " + atla.foreEarText + " declared before its parent");
         }

         this.personCustomTable[3 * i] = atla.offsetX;
         this.personCustomTable[3 * i + 1] = atla.foreEarAmount;
         this.personCustomTable[3 * i + 2] = atla.shinFootAmount;
         this.vectorEmitFloats[i] = atla.rootTransform.getCopyOf();
         value += atla.rootTransform.getValue();
         float[] bytephaseWarmValues2 = this.vectorEmitFloats[i];

         for (int j = 0; j < bytephaseWarmValues2.length; j += 9) {
            amount = Math.min(amount, bytephaseWarmValues2[j]);
            maxValue4 = Math.max(maxValue4, bytephaseWarmValues2[j]);
            wrapDegreesBound = Math.min(wrapDegreesBound, bytephaseWarmValues2[j + 1]);
            pilotStatusBound = Math.max(pilotStatusBound, bytephaseWarmValues2[j + 1]);
            maxValue3 = Math.min(maxValue3, bytephaseWarmValues2[j + 2]);
            maxValue6 = Math.max(maxValue6, bytephaseWarmValues2[j + 2]);
         }
      }

      this.partIndex = value;
      this.personCustomValue = foreEar == null ? -1 : foreEar.boneIndex;
      if (value == 0) {
         maxValue6 = 0.0F;
         pilotStatusBound = 0.0F;
         maxValue4 = 0.0F;
         maxValue3 = 0.0F;
         wrapDegreesBound = 0.0F;
         amount = 0.0F;
      }

      this.boundsMaxX = amount;
      this.bytephaseWarmAmount = wrapDegreesBound;
      this.boundsMinZ = maxValue3;
      this.scaleAmount = maxValue4;
      this.boundsMaxY = pilotStatusBound;
      this.boundsMaxZ = maxValue6;
   }

   public ForeShinScale getListSize() {
      return new ForeShinScale(this.listSize);
   }

   public float[] getAmounts() {
      return new float[this.listSize * 21];
   }
}
