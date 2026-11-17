package dev.bytephase.engine.mixins;

import dev.bytephase.engine.render.ClampMatrixUtils;
import dev.bytephase.engine.render.ClampMatrixUtils.Rig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import dev.bytephase.engine.categories.movement.Freecam;
import ai.auth.ModuleRegistry;
import dev.bytephase.engine.utils.DeltaAngle;

@Mixin({Camera.class})
public abstract class MixinCamera {
   @Shadow
   private boolean thirdPerson;
   @Shadow
   private Vec3d pos;
   @Shadow
   private float yaw;
   @Shadow
   private float pitch;
   private DeltaAngle cachedEvent;
   private float capturedTickDelta;
   private boolean dispatchedThisUpdate;

   @Shadow
   protected abstract void setPos(Vec3d vec);

   @Shadow
   protected abstract void setPos(double posValue, double posValue2, double posValue3);

   @Shadow
   protected abstract void setRotation(float rotationValue, float rotationValue2);

   @Shadow
   protected abstract void moveBy(float mixinCameraValue, float mixinCameraValue2, float side);

   @Shadow
   private float clipToSpace(float clipToValue) {
      throw new AssertionError();
   }

   @Inject(
      method = {"update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V"},
      at = {@At("HEAD")}
   )
   private void captureTickDelta(World world, Entity entity, boolean captureTickFlag, boolean captureTickFlag2, float capturedTickDelta, CallbackInfo callbackInfo) {
      this.capturedTickDelta = capturedTickDelta;
      this.dispatchedThisUpdate = false;
   }

   @ModifyArgs(
      method = {"update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"
      )
   )
   private void modifyRotation(Args args) {
      if (!this.dispatchedThisUpdate) {
         float defYaw = (Float)args.get(0);
         float defPitch = (Float)args.get(1);
         this.dispatchCameraEvent(defYaw, defPitch);
      }

      if (this.cachedEvent != null && this.cachedEvent.isFlag()) {
         args.set(0, this.cachedEvent.getAngleDegrees());
         args.set(1, this.cachedEvent.getLerp());
      }
   }

   @ModifyArgs(
      method = {"update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V"
      )
   )
   private void modifyPos(Args args) {
      if (!this.dispatchedThisUpdate) {
         Entity entity = MinecraftClient.getInstance().getCameraEntity();
         float y = entity != null ? entity.getYaw(this.capturedTickDelta) : 0.0F;
         float p = entity != null ? entity.getPitch(this.capturedTickDelta) : 0.0F;
         this.dispatchCameraEvent(y, p);
      }

      if (this.cachedEvent != null && this.cachedEvent.isFlag() && this.cachedEvent.isAngleInitialized()) {
         args.set(0, this.cachedEvent.getAmount());
         args.set(1, this.cachedEvent.getPreviousAngleDegrees());
         args.set(2, this.cachedEvent.getDeltaAngleAmount());
      }
   }

   private void dispatchCameraEvent(float dispatchCameraValue, float defPitch) {
      this.cachedEvent = new DeltaAngle(0.0, 0.0, 0.0, dispatchCameraValue, defPitch, this.capturedTickDelta);
      ModuleRegistry.dispatchCameraAngle(this.cachedEvent);
      this.dispatchedThisUpdate = true;
   }

   @Inject(
      method = {"update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V"},
      at = {@At("TAIL")}
   )
   private void bytephaseFreecamView(World world, Entity entity, boolean bytephaseFreecamFlag, boolean bytephaseFreecamFlag2, float bytephaseFreecamValue, CallbackInfo callbackInfo) {
      if (Freecam.isInstanceFlag()) {
         Freecam freecam = Freecam.getInstance();
         this.setRotation(freecam.getLerpForFloat(bytephaseFreecamValue), freecam.getLerp(bytephaseFreecamValue));
         this.setPos(freecam.getLerpAmount(bytephaseFreecamValue));
      } else if (ClampMatrixUtils.isFrameCamera() && entity != null && this.pos != null) {
         try {
            Rig rig = ClampMatrixUtils.getAmountDegrees(entity, bytephaseFreecamValue, this.pos.x, this.pos.y, this.pos.z, this.yaw, this.pitch);
            if (rig.degreesActive) {
               this.thirdPerson = true;
            }

            this.setRotation(rig.degreesAmount, rig.rigAmount);
            this.setPos(rig.clampAmount, rig.posY, rig.posZ);
            if (rig.rigDistance > 0.001F) {
               float back = ClampMatrixUtils.getClampAmount(this.clipToSpace(rig.rigDistance), rig.rigDistance);
               float side = ClampMatrixUtils.getAmountClamp(rig, back * rig.bytephaseFreecamAmount);
               if (side > 0.001F) {
                  this.moveBy(0.0F, 0.0F, side);
                  back = Math.min(back, this.clipToSpace(back));
               }

               this.moveBy(-back, 0.0F, 0.0F);
            }
         } catch (Throwable e) {
            ClampMatrixUtils.handleFrameClamp("camera", e);
         }
      }
   }
}
