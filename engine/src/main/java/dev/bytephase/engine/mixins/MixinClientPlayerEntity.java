package dev.bytephase.engine.mixins;

import dev.bytephase.engine.world.MathClampUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.text.Text;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.categories.bots.AutoBuilder;
import dev.bytephase.engine.categories.combat.PerfectWindCharge;
import dev.bytephase.engine.categories.misc.AutoPromo;
import dev.bytephase.engine.categories.movement.Flight;
import dev.bytephase.engine.categories.movement.SpeedHack;
import ai.auth.ModuleRegistry;
import dev.bytephase.engine.utils.ElytraAutoUtils;
import dev.bytephase.engine.utils.ExtUtils;

@Mixin({ClientPlayerEntity.class})
public class MixinClientPlayerEntity {
   @Shadow
   protected int ticksLeftToDoubleTapSprint;
   @Unique
   private float bytephaseRealYaw;
   @Unique
   private float bytephaseRealPitch;
   @Unique
   private boolean bytephaseRotationSwapped;
   @Unique
   private final float bytephasePrevSentYaw = Float.NaN;

   @Inject(
      method = {"tickMovement()V"},
      at = {@At("HEAD")}
   )
   private void bytephaseGapJumpIsAWalkingHop(CallbackInfo callbackInfo) {
      if (AutoBuilder.bytephaseGapActive) {
         AutoBuilder builder = (AutoBuilder)ModuleRegistry.getModule(AutoBuilder.class);
         if (builder != null && builder.isEnabled()) {
            ClientPlayerEntity self = (ClientPlayerEntity)((net.minecraft.client.network.ClientPlayerEntity) (Object) (this));
            this.ticksLeftToDoubleTapSprint = 0;
            GameOptions options = MinecraftClient.getInstance().options;
            if (options != null) {
               ElytraAutoUtils.handleRawPressed(options.sprintKey, false);
            }

            if (self.isSprinting()) {
               self.setSprinting(false);
            }
         } else {
            AutoBuilder.bytephaseGapActive = false;
         }
      }
   }

   @Inject(
      method = {"tickMovement()V"},
      at = {@At("HEAD")}
   )
   private void bytephaseWalkerNeedsGravity(CallbackInfo callbackInfo) {
      ClientPlayerEntity self = (ClientPlayerEntity)((net.minecraft.client.network.ClientPlayerEntity) (Object) (this));
      PlayerAbilities ab = self.getAbilities();
      AutoBuilder builder = (AutoBuilder)ModuleRegistry.getModule(AutoBuilder.class);
      if (builder != null && builder.isEnabled()) {
         if (ab.creativeMode || ab.allowFlying || ab.flying) {
            if (ab.allowFlying) {
               AutoBuilder.switchedDisableFlag = true;
            }

            ab.allowFlying = false;
            if (ab.flying) {
               ab.flying = false;
               self.sendAbilitiesUpdate();
            }
         }
      }
   }

   @Inject(
      method = {"tickMovement()V"},
      at = {@At("TAIL")}
   )
   private void bytephaseMovementHacks(CallbackInfo callbackInfo) {
      ClientPlayerEntity self = (ClientPlayerEntity)((net.minecraft.client.network.ClientPlayerEntity) (Object) (this));
      Flight flight = (Flight)ModuleRegistry.getModule(Flight.class);
      if (flight == null || !flight.isEnabled() || !flight.canPressedInput(self)) {
         SpeedHack speed = (SpeedHack)ModuleRegistry.getModule(SpeedHack.class);
         if (speed != null && speed.isEnabled()) {
            speed.handleVelocityInput(self);
         }
      }
   }

   @Inject(
      method = {"sendMessage(Lnet/minecraft/text/Text;Z)V"},
      at = {@At("HEAD")}
   )
   private void onMessage(Text text, boolean messageFlag, CallbackInfo callbackInfo) {
      if (text != null && !messageFlag) {
         ClientPlayerEntity self = (ClientPlayerEntity)((net.minecraft.client.network.ClientPlayerEntity) (Object) (this));
         AutoPromo autoPromo = (AutoPromo)ModuleRegistry.getModule(AutoPromo.class);
         if (autoPromo != null && autoPromo.isEnabled()) {
            String raw = text.getString();
            String myName = self.getName().getString();
            String lower = raw.toLowerCase();
            String lowerMe = myName.toLowerCase();
            String victim = null;
            if (lower.contains(" by " + lowerMe)) {
               int wasIdx = lower.indexOf(" was ");
               if (wasIdx > 0) {
                  victim = raw.substring(0, wasIdx).trim();
               }
            } else if (lower.startsWith(lowerMe + " killed ")) {
               victim = raw.substring((myName + " killed ").length()).trim();
            }

            if (victim != null && !victim.equalsIgnoreCase(myName)) {
               autoPromo.handleBase(victim);
            }
         }
      }
   }

   @Redirect(
      method = {"hasCollidedSoftly(Lnet/minecraft/util/math/Vec3d;)Z"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"
      )
   )
   private float bytephaseCollidedSoftlyYaw(ClientPlayerEntity player) {
      return MathClampUtils.isClampSettled() ? MathClampUtils.getYaw() : player.getYaw();
   }

   @Inject(
      method = {"sendMovementPackets()V"},
      at = {@At("HEAD")}
   )
   private void bytephaseSilentAimHead(CallbackInfo callbackInfo) {
      this.bytephaseRotationSwapped = false;
      ClientPlayerEntity self = (ClientPlayerEntity)((net.minecraft.client.network.ClientPlayerEntity) (Object) (this));
      boolean silent = MathClampUtils.isClampActive();
      PerfectWindCharge charge = (PerfectWindCharge)ModuleRegistry.getModule(PerfectWindCharge.class);
      boolean windCharge = charge != null && charge.hasSilentRotation();
      if (silent || windCharge) {
         this.bytephaseRealYaw = self.getYaw();
         this.bytephaseRealPitch = self.getPitch();
         if (!windCharge) self.setYaw(MathClampUtils.getYaw());
         self.setPitch(windCharge ? charge.silentPitch() : MathClampUtils.getClamp());
         this.bytephaseRotationSwapped = true;
      }
   }

   @Inject(
      method = {"sendMovementPackets()V"},
      at = {@At("RETURN")}
   )
   private void bytephaseSilentAimReturn(CallbackInfo callbackInfo) {
      if (this.bytephaseRotationSwapped) {
         ClientPlayerEntity self = (ClientPlayerEntity)((net.minecraft.client.network.ClientPlayerEntity) (Object) (this));
         self.setYaw(this.bytephaseRealYaw);
         self.setPitch(this.bytephaseRealPitch);
         this.bytephaseRotationSwapped = false;
      }
   }

   @Inject(
      method = {"sendMovementPackets()V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;sendSprintingPacket()V",
         shift = Shift.AFTER
      )}
   )
   private void bytephaseFireSilentInventory(CallbackInfo callbackInfo) {
      ClientPlayerEntity self = (ClientPlayerEntity)((net.minecraft.client.network.ClientPlayerEntity) (Object) (this));
      PlayerInput p = self.input.playerInput;
      boolean quiet = !p.forward() && !p.backward() && !p.left() && !p.right() && !p.jump() && !p.sneak() && !self.isSprinting();
      double speed = Math.hypot(self.getVelocity().x, self.getVelocity().z);
      ExtUtils.handleActionFlag(quiet, speed, self.isOnGround());
   }
}
