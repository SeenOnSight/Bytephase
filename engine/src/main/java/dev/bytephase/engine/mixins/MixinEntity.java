package dev.bytephase.engine.mixins;

import dev.bytephase.engine.render.ClampMatrixUtils;
import dev.bytephase.engine.world.MathClampUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.bytephase.engine.categories.bots.CrystalPvpBot;
import dev.bytephase.engine.categories.bots.SwordPvpBot;
import dev.bytephase.engine.categories.combat.HitBoxes;
import dev.bytephase.engine.categories.combat.SilentAim;
import dev.bytephase.engine.categories.bots.AutoBuilder;
import dev.bytephase.engine.categories.movement.Freecam;
import dev.bytephase.engine.categories.visuals.Freelook;
import dev.bytephase.engine.categories.visuals.NoFlame;
import dev.bytephase.engine.categories.visuals.PlayerESP;
import ai.auth.ModuleRegistry;

@Mixin({Entity.class})
public class MixinEntity {
   @Shadow
   protected static Vec3d movementInputToVelocity(Vec3d vec, float movementInputValue, float serverYaw) {
      throw new AssertionError("shadow");
   }

   @Inject(
      method = {"doesRenderOnFire()Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onDoesRenderOnFire(CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
      NoFlame noFlame = (NoFlame)ModuleRegistry.getModule(NoFlame.class);
      if (noFlame != null && noFlame.isEnabled()) {
         callbackInfoReturnable.setReturnValue(false);
      }
   }

   @Inject(
      method = {"isGlowing()Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsGlowing(CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
      Entity self = (Entity)((net.minecraft.entity.Entity) (Object) (this));
      PlayerESP playerESP = (PlayerESP)ModuleRegistry.getModule(PlayerESP.class);
      if (playerESP != null && playerESP.canGlowOutline(self)) {
         callbackInfoReturnable.setReturnValue(true);
      }
   }

   @Inject(
      method = {"getTeamColorValue()I"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetTeamColorValue(CallbackInfoReturnable<Integer> callbackInfoReturnable) {
      Entity self = (Entity)((net.minecraft.entity.Entity) (Object) (this));
      PlayerESP playerESP = (PlayerESP)ModuleRegistry.getModule(PlayerESP.class);
      if (playerESP != null && playerESP.canGlowOutline(self)) {
         callbackInfoReturnable.setReturnValue(playerESP.getReleaseRight());
      }
   }

   @Inject(
      method = {"isInvisibleTo(Lnet/minecraft/entity/player/PlayerEntity;)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephaseFreecamSeeOwnBody(PlayerEntity player, CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && ((Object) this) == mc.player && player == mc.player) {
         if (Freecam.isBaseInstance() || ClampMatrixUtils.isFrameCamera()) {
            callbackInfoReturnable.setReturnValue(false);
         }
      }
   }

   @Inject(
      method = {"getTargetingMargin()F"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetTargetingMargin(CallbackInfoReturnable<Float> callbackInfoReturnable) {
      HitBoxes hitBoxes = (HitBoxes)ModuleRegistry.getModule(HitBoxes.class);
      if (hitBoxes != null && hitBoxes.isEnabled()) {
         Entity self = (Entity)((net.minecraft.entity.Entity) (Object) (this));
         if (self != MinecraftClient.getInstance().player) {
            if (hitBoxes.isPlayersOnly() ? self instanceof PlayerEntity : self instanceof LivingEntity) {
               callbackInfoReturnable.setReturnValue(hitBoxes.getExpandAmount());
            }
         }
      }
   }

   @Inject(
      method = {"changeLookDirection(DD)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onChangeLookDirection(double changeLookValue, double changeLookValue2, CallbackInfo callbackInfo) {
      if (((Object) this) == MinecraftClient.getInstance().player) {
         if (Freecam.isInstanceFlag()) {
            Freecam.getInstance().handleClampAmount(changeLookValue, changeLookValue2);
            callbackInfo.cancel();
         } else {
            SilentAim silentAim = (SilentAim)ModuleRegistry.getModule(SilentAim.class);
            if (silentAim != null && silentAim.isSilentTurnRunning()) {
               silentAim.applyRotationDelta(changeLookValue, changeLookValue2);
               callbackInfo.cancel();
            } else {
               Freelook freelook = (Freelook)ModuleRegistry.getModule(Freelook.class);
               if (freelook != null && freelook.isEnabled()) {
                  freelook.handleClampAmount(changeLookValue, changeLookValue2);
                  callbackInfo.cancel();
               } else {
                  if (AutoBuilder.isAggressiveBuilderActive()) {
                     AutoBuilder.addMouseDelta(changeLookValue, changeLookValue2);
                     callbackInfo.cancel();
                  }

                  if (!callbackInfo.isCancelled() && CrystalPvpBot.isWaitingPlayer()) {
                     CrystalPvpBot.ignoreValues(changeLookValue, changeLookValue2);
                     callbackInfo.cancel();
                  }

                  if (!callbackInfo.isCancelled() && SwordPvpBot.isSwordBotActive()) {
                     callbackInfo.cancel();
                  }
               }
            }
         }
      }
   }

   @Redirect(
      method = {"updateVelocity(FLnet/minecraft/util/math/Vec3d;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/entity/Entity;movementInputToVelocity(Lnet/minecraft/util/math/Vec3d;FF)Lnet/minecraft/util/math/Vec3d;"
      )
   )
   private Vec3d bytephaseMoveFix(Vec3d vec, float bytephaseMoveValue, float bytephaseMoveValue2) {
      if (MathClampUtils.isClampSettled() && ((Object) this) == MinecraftClient.getInstance().player) {
         float serverYaw = MathClampUtils.getYaw();
         return movementInputToVelocity(vec, bytephaseMoveValue, serverYaw);
      } else {
         return movementInputToVelocity(vec, bytephaseMoveValue, bytephaseMoveValue2);
      }
   }
}
