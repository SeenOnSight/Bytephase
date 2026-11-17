package dev.bytephase.engine.utils;

import dev.bytephase.engine.gui.MatrixMatricesScreen;
import dev.bytephase.engine.gui.MatrixPeekUtilsRender;
import dev.bytephase.engine.mixins.AccessorInventoryScreen;
import dev.bytephase.engine.render.BytephaseRoundedUtils;
import java.awt.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import dev.bytephase.engine.categories.misc.SkinProtect;
import dev.bytephase.engine.categories.misc.CustomSkin;

public final class QuaternionfRotateBytephaseclient {
   private double lastAngleDegrees;
   private boolean quaternionfRotateActive;
   private float bytephaseScaleAmount;
   private double quaternionfRotateAmount;
   private float bodyYawOffsetDegrees = 0.0F;

   public boolean canAmountFlag(double amount, double amount2, int quaternionfRotateValue, int quaternionfRotateValue2, int quaternionfRotateValue3, int quaternionfRotateValue4, int quaternionfRotateValue5) {
      if (quaternionfRotateValue5 == 0 && amount >= quaternionfRotateValue && amount <= quaternionfRotateValue + quaternionfRotateValue3 && amount2 >= quaternionfRotateValue2 && amount2 <= quaternionfRotateValue2 + quaternionfRotateValue4) {
         this.quaternionfRotateActive = true;
         this.lastAngleDegrees = amount;
         this.quaternionfRotateAmount = amount2;
         return true;
      } else {
         return false;
      }
   }

   public void renderBytephaseclientScale(DrawContext context, int bytephaseScaleValue, int editValue, int releaseRightLimit, int n4, int bytephaseValue) {
      MatrixPeekUtilsRender.renderMatrixStack(context, bytephaseScaleValue, editValue, releaseRightLimit, n4, 6, BytephaseRoundedUtils.getPanelOverlay(new Color(255, 255, 255, 18).getRGB()));
      MatrixPeekUtilsRender.renderMatrixStack(context, bytephaseScaleValue + 1, editValue + 1, releaseRightLimit - 2, n4 - 2, 5, BytephaseRoundedUtils.getPanelOverlay(new Color(12, 12, 16, 236).getRGB()));
      MinecraftClient instance = MinecraftClient.getInstance();
      if (instance.player != null) {
         if (!MatrixMatricesScreen.isMatrixMatricesActive()) {
            int max5 = Math.max(20, (int)(n4 * 0.46F));
            int bytephaseValue2 = SkinProtect.bytephaseValue;
            SkinProtect.bytephaseValue = bytephaseValue;
            int value = CustomSkin.scaleValue;
            CustomSkin.scaleValue = -2;

            try {
               EntityRenderState bytephaseBuildEntityState = AccessorInventoryScreen.bytephaseBuildEntityState(instance.player);
               Quaternionf rotateZ = new Quaternionf().rotateZ((float) Math.PI);
               Quaternionf rotateX = new Quaternionf().rotateX(this.bytephaseScaleAmount * (float) (Math.PI / 180.0));
               rotateZ.mul(rotateX);
               if (bytephaseBuildEntityState instanceof LivingEntityRenderState livingEntityRenderState) {
                  livingEntityRenderState.bodyYaw = 180.0F + this.bodyYawOffsetDegrees;
                  livingEntityRenderState.relativeHeadYaw = 0.0F;
                  livingEntityRenderState.pitch = 0.0F;
                  if (livingEntityRenderState.baseScale != 0.0F) {
                     livingEntityRenderState.width = livingEntityRenderState.width / livingEntityRenderState.baseScale;
                     livingEntityRenderState.height = livingEntityRenderState.height / livingEntityRenderState.baseScale;
                     livingEntityRenderState.baseScale = 1.0F;
                  }
               }

               Vector3f vector3f = new Vector3f(0.0F, bytephaseBuildEntityState.height / 2.0F, 0.0F);
               context.addEntity(bytephaseBuildEntityState, max5, vector3f, rotateZ, rotateX, bytephaseScaleValue + 2, editValue + 2, bytephaseScaleValue + releaseRightLimit - 2, editValue + n4 - 2);
            } catch (Exception e) {
            } finally {
               SkinProtect.bytephaseValue = bytephaseValue2;
               CustomSkin.scaleValue = value;
            }
         }
      }
   }

   public void handleAmountFlag(double amount, double amount2) {
      if (this.quaternionfRotateActive) {
         this.bodyYawOffsetDegrees = RotateQuaternionfUtils.getAmount(this.bodyYawOffsetDegrees + RotateQuaternionfUtils.getAmount(amount - this.lastAngleDegrees));
         this.bytephaseScaleAmount = RotateQuaternionfUtils.getAmountForFloat(this.bytephaseScaleAmount + RotateQuaternionfUtils.getAmountForDouble(amount2 - this.quaternionfRotateAmount));
         this.lastAngleDegrees = amount;
         this.quaternionfRotateAmount = amount2;
      }
   }

   public QuaternionfRotateBytephaseclient() {
      this.bytephaseScaleAmount = 0.0F;
      this.quaternionfRotateActive = false;
   }

   public void updateFlag() {
      this.quaternionfRotateActive = false;
   }
}
