package dev.bytephase.engine.utils;

import dev.bytephase.engine.entity.ClientThreadUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import dev.bytephase.engine.categories.movement.Freecam;
import dev.bytephase.engine.categories.visuals.Freelook;
import dev.bytephase.engine.utils.RotationInterpolator;

public final class FrameRotationApplier {
   private final RotationInterpolator interpolator;

   public void applyInterpolatedRotation(DeltaAngle deltaAngle, boolean pitchYawFlag) {
      if (deltaAngle != null && pitchYawFlag && this.interpolator != null && !deltaAngle.isFlag()) {
         if (!Freecam.isInstanceFlag() && !Freelook.isFlag()) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
               if (this.interpolator.isApplicableAt(ClientThreadUtils.getTime(), player.getYaw(), player.getPitch())) {
                  float tickDelta = deltaAngle.getTickDelta();
                  deltaAngle.setAngleDegrees(this.interpolator.getInterpolatedYaw(tickDelta));
                  deltaAngle.setLerp(this.interpolator.getInterpolatedPitch(tickDelta));
                  deltaAngle.updateFlag();
               }
            }
         }
      }
   }

   public FrameRotationApplier(RotationInterpolator pitchYaw) {
      this.interpolator = pitchYaw;
   }
}
