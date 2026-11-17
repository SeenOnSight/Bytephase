package dev.bytephase.engine.categories.combat;

import dev.bytephase.engine.config.ConfigSetting;
import dev.bytephase.engine.config.BooleanSetting;
import dev.bytephase.engine.config.DoubleSetting;
import dev.bytephase.engine.config.IntegerSetting;
import dev.bytephase.engine.inventory.SelectedInventoryUtils;
import dev.bytephase.engine.mixins.AccessorKeyBinding;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.item.Items;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import dev.bytephase.engine.utils.WrapDegreesUtils;
import ai.auth.Module;
import dev.bytephase.engine.utils.IconsPngType;

public class PerfectWindCharge extends Module {
   private boolean pitchOverrideActive;
   private float targetPitch;
   private int originalSlot;
   private int windChargeTickCount;
   private boolean jumpKeyForced;
   private final BooleanSetting jumpSetting = new BooleanSetting(
      "Jump",
      "Jump at the instant the charge fires \u2014 the jump's upward velocity stacks with the blast for the maximum-height launch. Off = launch without jumping (e.g. under a low ceiling).",
      true
   );
   private boolean chargeFired;
   private boolean slotPrepared;
   private ClientPlayerEntity owner;
   private int chargeSlot = -1;
   private long aimedAt;
   private long firedAt;
   private final DoubleSetting lookSpeed = new DoubleSetting("Look Down Speed (deg/tick)",
      "How quickly your server-side aim turns downward; your camera stays free", 60, 1, 180, 1);
   private final IntegerSetting aimTicks = new IntegerSetting("Aim Ticks",
      "Minimum ticks before throwing; 1 is the fastest", 2, 1, 20);
   private final IntegerSetting throwDelay = new IntegerSetting("Wind Charge Delay (ms)",
      "Extra wait after the downward aim is ready", 0, 0, 2000, 10);
   private final BooleanSetting switchBack = new BooleanSetting("Switch Back",
      "Return to the item held before the wind charge", true);
   private final IntegerSetting returnDelay = new IntegerSetting("Switch Back Delay (ms)",
      "Wait after throwing before returning to your previous item", 0, 0, 2000, 10);

   private boolean isJumpKeyHeld() {
      if (mc.options != null && mc.getWindow() != null) {
         long handle = mc.getWindow().getHandle();
         Key boundKey = ((AccessorKeyBinding)mc.options.jumpKey).getBoundKey();
         if (boundKey.getCategory() == Type.KEYSYM) {
            return GLFW.glfwGetKey(handle, boundKey.getCode()) == 1;
         } else {
            return boundKey.getCategory() == Type.MOUSE ? GLFW.glfwGetMouseButton(handle, boundKey.getCode()) == 1 : false;
         }
      } else {
         return false;
      }
   }

   @Override
   public void onReset() {
      if (mc.player != null && mc.interactionManager != null && mc.player.networkHandler != null) {
         if (this.owner == null) this.owner = mc.player;
         if (this.owner != mc.player || mc.currentScreen != null) {
            this.pitchOverrideActive = false;
            this.setEnabled(false);
         } else if (this.chargeFired) {
            this.restoreJumpKey();
            this.pitchOverrideActive = false;
            if (!this.switchBack.isEnabled() || System.nanoTime() - this.firedAt
                  >= this.returnDelay.getValue() * 1_000_000L) this.setEnabled(false);
         } else {
            int selectedSlot = SelectedInventoryUtils.getValue(Items.WIND_CHARGE);
            if (selectedSlot == -1) {
               this.pitchOverrideActive = false;
               this.setEnabled(false);
            } else {
               if (!this.slotPrepared) {
                  int selectedSlot2 = mc.player.getInventory().getSelectedSlot();
                  this.originalSlot = selectedSlot2 != selectedSlot ? selectedSlot2 : -1;
                  if (selectedSlot2 != selectedSlot) {
                     mc.player.getInventory().setSelectedSlot(selectedSlot);
                  }

                  this.slotPrepared = true;
                  this.chargeSlot = selectedSlot;
                  this.targetPitch = mc.player.getPitch();
               }
               if (mc.player.getInventory().getSelectedSlot() != this.chargeSlot
                     || !mc.player.getMainHandStack().isOf(Items.WIND_CHARGE)) {
                  this.setEnabled(false);
                  return;
               }
               this.pitchOverrideActive = true;
               this.targetPitch = MathHelper.clamp(WrapDegreesUtils.stepAngleClamped(
                  this.targetPitch, 90.0F, (float)this.lookSpeed.getValue()), -90.0F, 90.0F);
               this.windChargeTickCount++;
               long now = System.nanoTime();
               if (90.0F - this.targetPitch <= 1.0F) {
                  if (this.aimedAt == 0) this.aimedAt = now;
               } else this.aimedAt = 0;
               if (this.windChargeTickCount >= this.aimTicks.getValue() && this.aimedAt != 0
                     && now - this.aimedAt >= this.throwDelay.getValue() * 1_000_000L
                     && mc.player.isOnGround() && !mc.player.isUsingItem()
                     && !mc.player.getItemCooldownManager().isCoolingDown(mc.player.getMainHandStack())) {
                  if (this.jumpSetting.isEnabled() && mc.options != null) {
                     mc.options.jumpKey.setPressed(true);
                     this.jumpKeyForced = true;
                  }

                  float cameraPitch = mc.player.getPitch();
                  try {
                     mc.player.setPitch(this.targetPitch);
                     mc.player.networkHandler.sendPacket(new LookAndOnGround(mc.player.getYaw(),
                        this.targetPitch, mc.player.isOnGround(), mc.player.horizontalCollision));
                     if (mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND).isAccepted()) {
                        mc.player.swingHand(Hand.MAIN_HAND);
                        this.chargeFired = true;
                        this.firedAt = now;
                     } else this.restoreJumpKey();
                  } finally {
                     mc.player.setPitch(cameraPitch);
                  }
               }
            }
         }
      }
   }

   public boolean hasSilentRotation() {
      return this.isEnabled() && this.pitchOverrideActive && this.owner == mc.player && mc.currentScreen == null;
   }

   public float silentPitch() { return this.targetPitch; }

   @Override
   protected void onDisable() {
      this.restoreJumpKey();
      this.pitchOverrideActive = false;
      if (this.switchBack.isEnabled() && this.originalSlot != -1 && mc.player != null
            && mc.player == this.owner && mc.player.getInventory().getSelectedSlot() == this.chargeSlot) {
         mc.player.getInventory().setSelectedSlot(this.originalSlot);
      }

      this.originalSlot = -1;
      this.slotPrepared = false;
      this.chargeFired = false;
      this.windChargeTickCount = 0;
      this.owner = null;
      this.chargeSlot = -1;
   }

   public PerfectWindCharge() {
      super("Perfect Wind Charge", IconsPngType.COMBAT);
      this.originalSlot = -1;
      this.addSettings(new ConfigSetting[]{this.jumpSetting, this.lookSpeed, this.aimTicks,
         this.throwDelay, this.switchBack, this.returnDelay});
   }

   @Override
   protected void onEnable() {
      this.originalSlot = -1;
      this.slotPrepared = false;
      this.chargeFired = false;
      this.windChargeTickCount = 0;
      this.pitchOverrideActive = false;
      this.owner = mc.player;
      this.chargeSlot = -1;
      this.aimedAt = 0;
      this.firedAt = 0;
      this.restoreJumpKey();
   }

   private void restoreJumpKey() {
      if (this.jumpKeyForced && mc.options != null) {
         mc.options.jumpKey.setPressed(this.isJumpKeyHeld());
      }

      this.jumpKeyForced = false;
   }
}
