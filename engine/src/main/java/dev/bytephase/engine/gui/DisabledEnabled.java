package dev.bytephase.engine.gui;

import dev.bytephase.engine.BytephaseClient;
import dev.bytephase.engine.render.BytephaseRoundedUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import dev.bytephase.engine.categories.misc.FakeCoordinates;
import dev.bytephase.engine.categories.misc.NameProtect;
import dev.bytephase.engine.categories.settings.Notifications;
import dev.bytephase.engine.categories.visuals.ShowHUD;
import dev.bytephase.engine.utils.CardStatusUtilsFlag;

public class DisabledEnabled {
   private static final String MODULE_DISABLED = "Module Disabled";
   private static final String DISABLED = " disabled";
   private static final String DISABLED_LABEL = "Disabled";
   private int enabledDisabledValue;
   private static final String ENABLED = "Enabled";
   private static final DisabledEnabled INSTANCE = new DisabledEnabled();
   private final List<CrossMatrix> disabledEnabledItems = new ArrayList<>();
   private static final String MODULE_ENABLED = "Module Enabled";
   private final MatrixStack matrixStack = new MatrixStack();
   private static final String ENABLED_SUFFIX = " enabled";

   public AutoCloseable getDisabledEnabled() {
      this.enabledDisabledValue++;
      return () -> {
         if (this.enabledDisabledValue > 0) {
            this.enabledDisabledValue--;
         }
      };
   }

   public void renderScaledWindow(DrawContext context) {
      this.disabledEnabledItems.removeIf(CrossMatrix::isTimeFlag);
      if (!this.disabledEnabledItems.isEmpty()) {
         if (!Notifications.isInstanceBase()) {
            for (CrossMatrix crossMatrix : this.disabledEnabledItems) {
               crossMatrix.updateFlagTime();
            }
         }

         MinecraftClient instance = MinecraftClient.getInstance();
         double scaleFactor = instance.getWindow().getScaleFactor();
         int calculateScaleFactor = instance.getWindow().calculateScaleFactor(ShowHUD.getMax(instance), instance.forcesUnicodeFont());
         float scaledWindowDivisor = (float)(calculateScaleFactor / scaleFactor);
         boolean scaledWindowFlag = Math.abs(scaledWindowDivisor - 1.0F) > 1.0E-4F;
         int scaledWidth;
         int scaledHeight;
         if (scaledWindowFlag) {
            scaledWidth = Math.round(instance.getWindow().getScaledWidth() / scaledWindowDivisor);
            scaledHeight = Math.round(instance.getWindow().getScaledHeight() / scaledWindowDivisor);
         } else {
            scaledWidth = instance.getWindow().getScaledWidth();
            scaledHeight = instance.getWindow().getScaledHeight();
         }

         MatrixStack matrixStack = BytephaseClient.fontManager.getMatrixStack();
         MatrixStack matrixStack2 = MatrixPeekUtilsRender.getMatrixStack();
         BytephaseClient.fontManager.setMatrixStack(this.matrixStack);
         MatrixPeekUtilsRender.handleMatrixStack(this.matrixStack);
         if (scaledWindowFlag) {
            BytephaseRoundedUtils.scaledWidth = scaledWidth;
            BytephaseRoundedUtils.scaledHeight = scaledHeight;
         }

         try {
            float scaledWindowTotal = scaledHeight - 8 - NativeHud.getAmountTime();

            for (int i = this.disabledEnabledItems.size() - 1; i >= 0; i--) {
               CrossMatrix disabledenableditem = this.disabledEnabledItems.get(i);
               disabledenableditem.renderCrossAmount(context, scaledWidth - 8, scaledWindowTotal - disabledenableditem.getValue());
               scaledWindowTotal -= (disabledenableditem.getValue() + 4) * disabledenableditem.getTimeFlag();
            }
         } finally {
            if (scaledWindowFlag) {
               BytephaseRoundedUtils.scaledWidth = 0.0F;
               BytephaseRoundedUtils.scaledHeight = 0.0F;
            }

            BytephaseClient.fontManager.setMatrixStack(matrixStack);
            MatrixPeekUtilsRender.handleMatrixStack(matrixStack2);
         }
      }
   }

   public static DisabledEnabled getInstance() {
      return INSTANCE;
   }
   public boolean hasPending() {
      this.disabledEnabledItems.removeIf(CrossMatrix::isTimeFlag);
      return !this.disabledEnabledItems.isEmpty();
   }

   public void handleDisabledEnabled(String disabledEnabledText, String substringText, boolean disabledEnabledFlag) {
      if (this.enabledDisabledValue <= 0 && Notifications.isInstanceBase()) {
         String disabledEnabledText3 = disabledEnabledFlag ? " enabled" : " disabled";
         if ((disabledEnabledFlag ? "Module Enabled" : "Module Disabled").equals(disabledEnabledText) && substringText != null && substringText.length() > disabledEnabledText3.length() && substringText.endsWith(disabledEnabledText3)) {
            disabledEnabledText = substringText.substring(0, substringText.length() - disabledEnabledText3.length());
            substringText = disabledEnabledFlag ? "Enabled" : "Disabled";
         }

         disabledEnabledText = NameProtect.getSubstring(disabledEnabledText);
         substringText = NameProtect.getSubstring(substringText);
         disabledEnabledText = FakeCoordinates.formatInstance(disabledEnabledText);
         substringText = FakeCoordinates.formatInstance(substringText);
         disabledEnabledText = CardStatusUtilsFlag.getOrDefault(disabledEnabledText);
         substringText = CardStatusUtilsFlag.getOrDefault(substringText);

         for (CrossMatrix crossMatrix : this.disabledEnabledItems) {
            if (!crossMatrix.isFlag() && crossMatrix.canCrossText(disabledEnabledText, substringText, disabledEnabledFlag)) {
               crossMatrix.updateTimeFlag();
               return;
            }
         }

         long filterCount = this.disabledEnabledItems.stream().filter(crossMatrix2 -> !crossMatrix2.isFlag()).count();
         if (filterCount >= 4L) {
            this.disabledEnabledItems.stream().filter(crossMatrix3 -> !crossMatrix3.isFlag()).findFirst().ifPresent(CrossMatrix::updateFlagTime);
         }

         this.disabledEnabledItems.add(new CrossMatrix(disabledEnabledText, substringText, disabledEnabledFlag));
      }
   }

   public DisabledEnabled() {
      this.enabledDisabledValue = 0;
   }
}
