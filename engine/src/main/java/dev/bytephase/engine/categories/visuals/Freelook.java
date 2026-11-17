package dev.bytephase.engine.categories.visuals;

import net.minecraft.util.math.MathHelper;
import ai.auth.Module;
import ai.auth.ModuleRegistry;
import dev.bytephase.engine.utils.DeltaAngle;
import dev.bytephase.engine.utils.IconsPngType;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.network.ClientPlayerEntity;
import java.lang.ref.WeakReference;

public class Freelook extends Module {
   private float lastPitch;
   private float startClamp;
   private float clamp;
   private float endClamp;
   private Perspective previousPerspective;
   private WeakReference<ClientPlayerEntity> angleOwner = new WeakReference<>(null);

   @Override
   protected void onCameraAngle(DeltaAngle deltaAngle) {
      this.capturePlayer();
      float tickDelta = deltaAngle.getTickDelta();
      deltaAngle.setAngleDegrees(MathHelper.lerp(tickDelta, this.startClamp, this.endClamp));
      deltaAngle.setLerp(MathHelper.lerp(tickDelta, this.lastPitch, this.clamp));
      deltaAngle.updateFlag();
   }

   public static boolean isFlag() {
      Freelook freelook = (Freelook)ModuleRegistry.getModule(Freelook.class);
      return freelook != null && freelook.isEnabled();
   }

   @Override
   public void onEnable() {
      if (mc.options != null) {
         this.previousPerspective = mc.options.getPerspective();
         mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
      }
      this.angleOwner.clear();
      this.capturePlayer();
   }

   private void capturePlayer() {
      if (mc.player != null && mc.player != this.angleOwner.get()) {
         this.angleOwner = new WeakReference<>(mc.player);
         this.endClamp = this.startClamp = mc.player.getYaw();
         this.clamp = this.lastPitch = mc.player.getPitch();
      }
   }

   @Override
   protected void onDisable() {
      if (this.previousPerspective != null && mc.options != null) {
         mc.options.setPerspective(this.previousPerspective);
      }
      this.previousPerspective = null;
      this.angleOwner.clear();
   }

   public void handleClampAmount(double clampValue, double clampValue2) {
      this.startClamp = this.endClamp;
      this.lastPitch = this.clamp;
      this.endClamp += (float)(clampValue * 0.15);
      this.clamp = MathHelper.clamp(this.clamp + (float)(clampValue2 * 0.15), -90.0F, 90.0F);
   }

   public Freelook() {
      super("Freelook", IconsPngType.VISUAL);
   }
}
