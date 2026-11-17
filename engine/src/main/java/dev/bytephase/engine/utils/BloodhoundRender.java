package dev.bytephase.engine.utils;

import dev.bytephase.engine.BytephaseClient;
import dev.bytephase.engine.gui.MatrixPeekUtilsRender;
import dev.bytephase.engine.render.BytephaseRoundedUtils;
import dev.bytephase.engine.render.VectorDotUtils;
import dev.bytephase.engine.render.VertexBuilderUtils;
import dev.bytephase.engine.render.VertexConsumerUtilsVector;
import dev.bytephase.engine.render.VertexTexturePeek;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import dev.bytephase.engine.categories.visuals.ShowHUD;
import dev.bytephase.engine.utils.Site;
import dev.bytephase.engine.utils.Places;

public final class BloodhoundRender {
   private final String[] bloodhoundRenderTexts;
   private final Vec3d[] vecVec3ds;
   private final float[] bloodhoundRenderTable;
   private final int[] table;
   private final Object[] bloodhoundRenderObjs;
   private final String[] cachedClueListTexts;
   private final int[] cachedSiteScores;
   private final String[] cachedAmountTexts;
   private final long[] cachedAmountValues;
   private final float[] columnWidths;
   private final MatrixStack matrixStack = new MatrixStack();
   private final Site[] sites;
   private final String[] cachedPercentTexts;

   public BloodhoundRender() {
      this.bloodhoundRenderTexts = new String[3];
      this.bloodhoundRenderTable = new float[3];
      this.columnWidths = new float[3];
      this.table = new int[3];
      this.sites = new Site[6];
      this.bloodhoundRenderObjs = new Object[6];
      this.cachedSiteScores = new int[6];
      this.cachedAmountValues = new long[6];
      this.cachedPercentTexts = new String[6];
      this.cachedClueListTexts = new String[6];
      this.cachedAmountTexts = new String[6];
      this.vecVec3ds = new Vec3d[6];
   }

   private Vec3d getVec(int index, Site site) {
      Vec3d vec = this.vecVec3ds[index];
      double x = site.getJoinAmount() + 0.5;
      double y = site.getAmount() + 1.6;
      double z = site.getClueJoinAmount() + 0.5;
      if (vec == null || vec.x != x || vec.y != y || vec.z != z) {
         this.vecVec3ds[index] = vec = new Vec3d(x, y, z);
      }

      return vec;
   }

   private static int getClamp(int clampValue, float clampValue2) {
      int clamp = MathHelper.clamp(Math.round(clampValue2), 0, 255);
      return clamp << 24 | clampValue & 16777215;
   }

   private void renderTextsAmounts(DrawContext context, VertexTexturePeek vertexTexturePeek, Site site, int index, double bloodhoundRenderValue, float bloodhoundRenderValue2, float bloodhoundRenderValue3, float scaledWindowValue, float bloodhoundRenderValue5) {
      float clamp = MathHelper.clamp(1.1F - (float)bloodhoundRenderValue / 900.0F, 0.78F, 1.0F);
      float bloodhoundRenderValue6 = 18.0F * clamp;
      float bloodhoundRenderValue7 = 7.0F * clamp;
      float bytephaseValue = 11.0F * clamp;
      float bytephaseLicenseLimit = 5.0F * clamp;
      if (this.sites[index] != site || this.cachedSiteScores[index] != site.getConfidenceScore() || this.cachedPercentTexts[index] == null) {
         this.sites[index] = site;
         this.cachedSiteScores[index] = site.getConfidenceScore();
         this.cachedPercentTexts[index] = site.getConfidenceScore() + "%";
      }

      List copyOf = site.getCopyOfClueJoin();
      if (this.bloodhoundRenderObjs[index] != copyOf || this.cachedClueListTexts[index] == null) {
         this.bloodhoundRenderObjs[index] = copyOf;
         this.cachedClueListTexts[index] = copyOf.isEmpty() ? "" : "  " + String.join(", ", copyOf);
      }

      long bloodhoundRenderValue10 = bloodhoundRenderValue < 1000.0 ? Math.round(bloodhoundRenderValue) : 1000000L + Math.round(bloodhoundRenderValue / 100.0);
      if (this.cachedAmountValues[index] != bloodhoundRenderValue10 || this.cachedAmountTexts[index] == null) {
         this.cachedAmountValues[index] = bloodhoundRenderValue10;
         this.cachedAmountTexts[index] = "  " + Places.getFormat(bloodhoundRenderValue);
      }

      String bloodhoundRenderText = this.cachedPercentTexts[index];
      String bytephaseText = this.cachedClueListTexts[index];
      String bloodhoundRenderText3 = this.cachedAmountTexts[index];
      float bytephaseAmounts = vertexTexturePeek.getBytephaseclientAmounts(bloodhoundRenderText, bytephaseValue);
      float bytephaseAmounts2 = vertexTexturePeek.getBytephaseclientAmounts(bytephaseText, bytephaseValue);
      float bytephaseAmounts3 = vertexTexturePeek.getBytephaseclientAmounts(bloodhoundRenderText3, bytephaseValue);
      float materialFactor = bloodhoundRenderValue7 + bytephaseAmounts + bytephaseAmounts2 + bytephaseAmounts3 + bloodhoundRenderValue7;
      float matrixPeekValue = bloodhoundRenderValue2 - materialFactor / 2.0F;
      float matrixStackValue = bloodhoundRenderValue3 - bloodhoundRenderValue6;
      if (!(matrixPeekValue + materialFactor + 16.0F < 0.0F) && !(matrixPeekValue - 16.0F > scaledWindowValue) && !(matrixStackValue + bloodhoundRenderValue6 + 16.0F < 0.0F) && !(matrixStackValue - 16.0F > bloodhoundRenderValue5)) {
         MatrixPeekUtilsRender.renderForDrawContextFloat(context, matrixPeekValue, matrixStackValue, materialFactor, bloodhoundRenderValue6, bytephaseLicenseLimit, -233499369, 620756991);
         float bloodhoundRenderValue14 = matrixPeekValue + bloodhoundRenderValue7;
         float bloodhoundRenderValue15 = matrixStackValue + (bloodhoundRenderValue6 - vertexTexturePeek.getAmountForFloat(bytephaseValue)) / 2.0F;
         int peekEndLimit = 0;
         this.bloodhoundRenderTexts[peekEndLimit] = bloodhoundRenderText;
         this.bloodhoundRenderTable[peekEndLimit] = bloodhoundRenderValue14;
         this.table[peekEndLimit++] = getValue(site.getConfidenceScore());
         float bloodhoundRenderTotal = bloodhoundRenderValue14 + bytephaseAmounts;
         if (!bytephaseText.isEmpty()) {
            this.bloodhoundRenderTexts[peekEndLimit] = bytephaseText;
            this.bloodhoundRenderTable[peekEndLimit] = bloodhoundRenderTotal;
            this.table[peekEndLimit++] = -1;
            bloodhoundRenderTotal += bytephaseAmounts2;
         }

         this.bloodhoundRenderTexts[peekEndLimit] = bloodhoundRenderText3;
         this.bloodhoundRenderTable[peekEndLimit] = bloodhoundRenderTotal;
         this.table[peekEndLimit++] = -6645087;

         for (int i = 0; i < peekEndLimit; i++) {
            this.columnWidths[i] = bloodhoundRenderValue15;
         }

         vertexTexturePeek.handlePeekEnd(this.bloodhoundRenderTexts, this.bloodhoundRenderTable, this.columnWidths, this.table, peekEndLimit, bytephaseValue);
      }
   }

   public void handleCameraClamp(List<Site> list, boolean cameraClampFlag, boolean cameraClampFlag2) {
      if (!list.isEmpty()) {
         MinecraftClient instance = MinecraftClient.getInstance();
         if (instance.player != null) {
            Vec3d cameraPos = instance.gameRenderer.getCamera().getCameraPos();
            Matrix4f matrix = VertexBuilderUtils.getMatrix();
            VertexConsumer vertexConsumer = cameraClampFlag ? VertexBuilderUtils.getBufferIMMEDIATE() : null;
            float cameraClampFactor = 0.85F + 0.15F * (float)Math.sin(System.currentTimeMillis() % 10000L / 1000.0 * 2.4);

            for (int i = 0; i < list.size(); i++) {
               Site tracer = (Site)list.get(i);
               int cameraClampValue = getValue(tracer.getConfidenceScore()) & 16777215;
               double cameraClampValue2 = tracer.getJoinAmount() + 0.5;
               double cameraClampValue3 = tracer.getAmount();
               double vertexBuilderValue = tracer.getClueJoinAmount() + 0.5;
               double cameraClampFactor2 = cameraClampValue2 - cameraPos.x;
               double cameraClampFactor3 = vertexBuilderValue - cameraPos.z;
               double distance = Math.sqrt(cameraClampFactor2 * cameraClampFactor2 + cameraClampFactor3 * cameraClampFactor3);
               float clamp = (float)MathHelper.clamp(1.0 - (distance - 900.0) / 1400.0, 0.35, 1.0);
               if (vertexConsumer != null) {
                  float cameraClampFactor4 = distance > 0.001 ? (float)(cameraClampFactor3 / distance) : 1.0F;
                  float cameraClampFactor5 = distance > 0.001 ? (float)(-cameraClampFactor2 / distance) : 0.0F;
                  float cameraClampFactor6 = 0.16F * (float)MathHelper.clamp(distance / 48.0, 1.0, 6.0);
                  float amountX = VertexBuilderUtils.getAmountX(cameraClampValue2);
                  float amountForDouble = VertexBuilderUtils.getAmountForDouble(vertexBuilderValue);
                  float cameraClampValue7 = VertexBuilderUtils.getAmount(cameraClampValue3);
                  float deltaY = VertexBuilderUtils.getAmount(cameraClampValue3 + 72.0);
                  int valueInt = getClamp(cameraClampValue, 150.0F * clamp * cameraClampFactor);
                  int clamp3 = getClamp(cameraClampValue, 0.0F);
                  VertexBuilderUtils.handleVertexColor(
                     vertexConsumer,
                     matrix,
                     amountX + cameraClampFactor4 * cameraClampFactor6,
                     cameraClampValue7,
                     amountForDouble + cameraClampFactor5 * cameraClampFactor6,
                     valueInt,
                     amountX - cameraClampFactor4 * cameraClampFactor6,
                     cameraClampValue7,
                     amountForDouble - cameraClampFactor5 * cameraClampFactor6,
                     valueInt,
                     amountX - cameraClampFactor4 * cameraClampFactor6,
                     deltaY,
                     amountForDouble - cameraClampFactor5 * cameraClampFactor6,
                     clamp3,
                     amountX + cameraClampFactor4 * cameraClampFactor6,
                     deltaY,
                     amountForDouble + cameraClampFactor5 * cameraClampFactor6,
                     clamp3
                  );
                  VertexBuilderUtils.handleVertexColor(
                     vertexConsumer,
                     matrix,
                     amountX - cameraClampFactor5 * cameraClampFactor6,
                     cameraClampValue7,
                     amountForDouble + cameraClampFactor4 * cameraClampFactor6,
                     valueInt,
                     amountX + cameraClampFactor5 * cameraClampFactor6,
                     cameraClampValue7,
                     amountForDouble - cameraClampFactor4 * cameraClampFactor6,
                     valueInt,
                     amountX + cameraClampFactor5 * cameraClampFactor6,
                     deltaY,
                     amountForDouble - cameraClampFactor4 * cameraClampFactor6,
                     clamp3,
                     amountX - cameraClampFactor5 * cameraClampFactor6,
                     deltaY,
                     amountForDouble + cameraClampFactor4 * cameraClampFactor6,
                     clamp3
                  );
               }

               if (cameraClampFlag) {
                  float cameraClampValue9 = 0.5F;
                  VertexBuilderUtils.handle(
                     matrix,
                     VertexBuilderUtils.getAmountX(cameraClampValue2 - cameraClampValue9),
                     VertexBuilderUtils.getAmount(cameraClampValue3 - cameraClampValue9),
                     VertexBuilderUtils.getAmountForDouble(vertexBuilderValue - cameraClampValue9),
                     VertexBuilderUtils.getAmountX(cameraClampValue2 + cameraClampValue9),
                     VertexBuilderUtils.getAmount(cameraClampValue3 + cameraClampValue9),
                     VertexBuilderUtils.getAmountForDouble(vertexBuilderValue + cameraClampValue9),
                     getClamp(cameraClampValue, 230.0F * clamp),
                     1.6F
                  );
               }

               if (cameraClampFlag2 && i < 3) {
                  VectorDotUtils.handle(cameraClampValue2, cameraClampValue3, vertexBuilderValue, cameraClampValue | 0xFF000000, i == 0);
               }
            }
         }
      }
   }

   public void renderScaledWindow(DrawContext context, List<Site> list) {
      if (!list.isEmpty()) {
         MinecraftClient instance = MinecraftClient.getInstance();
         if (instance.player != null && instance.currentScreen == null) {
            BytephaseClient.fontManager.setMatrixStack(this.matrixStack);
            MatrixPeekUtilsRender.handleMatrixStack(this.matrixStack);
            double scaleFactor = instance.getWindow().getScaleFactor();
            int calculateScaleFactor = instance.getWindow().calculateScaleFactor(ShowHUD.getMax(instance), instance.forcesUnicodeFont());
            float scaledWindowDivisor = (float)(calculateScaleFactor / scaleFactor);
            boolean scaledWindowFlag = Math.abs(scaledWindowDivisor - 1.0F) > 1.0E-4F;
            if (scaledWindowFlag) {
               BytephaseRoundedUtils.scaledWidth = Math.round(instance.getWindow().getScaledWidth() / scaledWindowDivisor);
               BytephaseRoundedUtils.scaledHeight = Math.round(instance.getWindow().getScaledHeight() / scaledWindowDivisor);
            }

            VertexTexturePeek regular = VertexTexturePeek.getRegular();
            VertexTexturePeek vertexTexturePeek = regular.isBytephaseclientFlag() ? regular : VertexTexturePeek.getSuisse();

            try {
               if (!vertexTexturePeek.isBytephaseclientFlag()) {
                  return;
               }

               Vec3d cameraPos = instance.gameRenderer.getCamera().getCameraPos();
               float scaledWindowValue = BytephaseRoundedUtils.scaledWidth > 0.0F
                  ? BytephaseRoundedUtils.scaledWidth
                  : (float)instance.getWindow().getFramebufferWidth() / instance.getWindow().getScaleFactor();
               float scaledWindowValue2 = BytephaseRoundedUtils.scaledHeight > 0.0F
                  ? BytephaseRoundedUtils.scaledHeight
                  : (float)instance.getWindow().getFramebufferHeight() / instance.getWindow().getScaleFactor();
               int min3 = Math.min(6, list.size());

               for (int i = min3 - 1; i >= 0; i--) {
                  Site site = (Site)list.get(i);
                  double scaledWindowFactor = site.getJoinAmount() + 0.5 - cameraPos.x;
                  double scaledWindowFactor2 = site.getAmount() - cameraPos.y;
                  double scaledWindowFactor3 = site.getClueJoinAmount() + 0.5 - cameraPos.z;
                  double distance = Math.sqrt(scaledWindowFactor * scaledWindowFactor + scaledWindowFactor2 * scaledWindowFactor2 + scaledWindowFactor3 * scaledWindowFactor3);
                  if (i <= 0 || !(distance > 640.0)) {
                     Vec3d windowFramebuffer = VertexConsumerUtilsVector.getWindowFramebuffer(this.getVec(i, site));
                     if (windowFramebuffer != null && !(windowFramebuffer.z <= 0.0) && !(windowFramebuffer.z >= 1.0)) {
                        this.renderTextsAmounts(
                           context, vertexTexturePeek, site, i, Math.sqrt(scaledWindowFactor * scaledWindowFactor + scaledWindowFactor3 * scaledWindowFactor3), (float)(windowFramebuffer.x / scaledWindowDivisor), (float)(windowFramebuffer.y / scaledWindowDivisor), scaledWindowValue, scaledWindowValue2
                        );
                     }
                  }
               }
            } finally {
               if (scaledWindowFlag) {
                  BytephaseRoundedUtils.scaledWidth = 0.0F;
                  BytephaseRoundedUtils.scaledHeight = 0.0F;
               }
            }
         }
      }
   }

   public static int getValue(int bloodhoundRenderLimit) {
      if (bloodhoundRenderLimit >= 75) {
         return -8393375;
      } else {
         return bloodhoundRenderLimit >= 55 ? -1535700 : -8408321;
      }
   }

   static String getFormat(double bloodhoundRenderValue) {
      return String.format(Locale.ROOT, "%.0f", bloodhoundRenderValue);
   }
}
