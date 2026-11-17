package dev.bytephase.engine.mixins;

import java.lang.reflect.Method;
import java.util.function.IntToDoubleFunction;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.consume.UseAction;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.categories.visuals.HandCustomer;
import dev.bytephase.engine.categories.visuals.SwordInspect;
import dev.bytephase.engine.utils.AttachmentFramebuffer;
import dev.bytephase.engine.utils.KnifeFinish;
import dev.bytephase.engine.utils.KnifeFinishTextures;
import dev.bytephase.engine.utils.KnifeMesh;
import dev.bytephase.engine.utils.KnifeRig;
import ai.auth.ModuleRegistry;
import dev.bytephase.engine.utils.KnifeHold;

@Mixin({HeldItemRenderer.class})
public abstract class MixinHeldItemRenderer {
   @Shadow
   private ItemStack offHand;
   @Shadow
   @Final
   private EntityRenderManager entityRenderDispatcher;
   @Unique
   private Matrix4f bytephase$knifeCorrection;
   @Unique
   private Entry bytephase$knifeArmEntry;
   @Unique
   private static Method bytephase$applySwingTransformsMethod;
   @Unique
   private static Method bytephase$applyUseTransformsMethod;
   @Unique
   private static boolean bytephase$lancingResolved;

   @Shadow
   public abstract void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext itemDisplayContext, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int mixinHeldValue);

   @Shadow
   protected abstract void swingArm(float swingArmValue, MatrixStack matrices, int i, Arm arm);

   @Shadow
   protected abstract void applyEquipOffset(MatrixStack matrices, Arm arm, float equipOffsetValue);

   @Shadow
   protected abstract void applyEatOrDrinkTransformation(MatrixStack matrices, float eatOrValue, Arm arm, ItemStack stack, PlayerEntity player);

   @Shadow
   protected abstract void applyBrushTransformation(MatrixStack matrices, float brushTransformationValue, Arm arm, PlayerEntity player);

   @Shadow
   protected abstract void renderMapInBothHands(MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int inBothValue, float inBothValue2, float equipOffsetValue, float swingArmValue);

   @Shadow
   protected abstract void renderMapInOneHand(MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int inOneValue, float equipOffsetValue, Arm arm, float swingArmValue, ItemStack stack);

   @Shadow
   protected abstract void renderArmHoldingItem(MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int armHoldingValue, float equipOffsetValue, float swingArmValue, Arm arm);

   @Inject(
      method = {"renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"},
      at = {@At("HEAD")}
   )
   private void bytephase$beforeHands(
      float bytephasebeforeHandsValue, MatrixStack matrixStack, OrderedRenderCommandQueue orderedRenderCommandQueue, ClientPlayerEntity clientPlayerEntity, int bytephasebeforeHandsValue2, CallbackInfo callbackInfo
   ) {
      AttachmentFramebuffer.getInstance().updatePostTextureTrailSamplerActive();
   }

   @Inject(
      method = {"renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"},
      at = {@At("TAIL")}
   )
   private void bytephase$afterHands(
      float bytephaseafterHandsValue, MatrixStack matrixStack, OrderedRenderCommandQueue orderedRenderCommandQueue, ClientPlayerEntity clientPlayerEntity, int bytephaseafterHandsValue2, CallbackInfo callbackInfo
   ) {
      AttachmentFramebuffer renderer = AttachmentFramebuffer.getInstance();
      renderer.updatePostTexture3();
      renderer.isAmountPost();
   }

   @Inject(
      method = {"renderArmHoldingItem(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;IFFLnet/minecraft/util/Arm;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/entity/PlayerEntityRenderer;renderRightArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;Z)V"
      )}
   )
   private void bytephase$knifeRightArm(
      MatrixStack matrixStack, OrderedRenderCommandQueue orderedRenderCommandQueue, int bytephaseknifeRightValue, float bytephaseknifeRightValue2, float bytephaseknifeRightValue3, Arm arm, CallbackInfo callbackInfo
   ) {
      this.bytephase$poseKnifeArm(matrixStack);
   }

   @Inject(
      method = {"renderArmHoldingItem(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;IFFLnet/minecraft/util/Arm;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/entity/PlayerEntityRenderer;renderLeftArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;Z)V"
      )}
   )
   private void bytephase$knifeLeftArm(
      MatrixStack matrixStack, OrderedRenderCommandQueue orderedRenderCommandQueue, int bytephaseknifeLeftValue, float bytephaseknifeLeftValue2, float bytephaseknifeLeftValue3, Arm arm, CallbackInfo callbackInfo
   ) {
      this.bytephase$poseKnifeArm(matrixStack);
   }

   @Unique
   private void bytephase$poseKnifeArm(MatrixStack matrixStack) {
      if (this.bytephase$knifeCorrection != null) {
         matrixStack.multiplyPositionMatrix(this.bytephase$knifeCorrection);
         this.bytephase$knifeArmEntry = matrixStack.peek().copy();
      }
   }

   @Unique
   private boolean bytephase$renderKnifeInHand(
      SwordInspect swordInspect,
      KnifeRig knifeRig,
      AbstractClientPlayerEntity abstractClientPlayerEntity,
      Arm arm,
      ItemStack stack,
      float bytephaserenderKnifeValue,
      float equipOffsetValue,
      MatrixStack matrices,
      OrderedRenderCommandQueue orderedRenderCommandQueue,
      int armHoldingValue
   ) {
      boolean meshes = KnifeMesh.canModel(knifeRig);
      if (!meshes && !swordInspect.canModelBaked(knifeRig)) {
         return false;
      } else {
         int side = arm == Arm.RIGHT ? 1 : -1;
         IntToDoubleFunction ch = KnifeHold.getIntToDoubleFunction(swordInspect.getSwishRound(bytephaserenderKnifeValue), bytephaserenderKnifeValue);
         this.bytephase$knifeCorrection = bytephase$matrix(KnifeHold.getAmounts(side, ch));
         this.bytephase$knifeArmEntry = null;
         matrices.push();

         try {
            this.renderArmHoldingItem(matrices, orderedRenderCommandQueue, armHoldingValue, equipOffsetValue, 0.0F, arm);
         } finally {
            matrices.pop();
            this.bytephase$knifeCorrection = null;
         }

         Entry armEntry = this.bytephase$knifeArmEntry;
         this.bytephase$knifeArmEntry = null;
         if (armEntry == null) {
            return false;
         } else {
            matrices.push();

            try {
               matrices.peek().copy(armEntry);
               this.entityRenderDispatcher.getPlayerRenderer(abstractClientPlayerEntity).getModel().getArm(arm).applyTransform(matrices);
               matrices.multiplyPositionMatrix(bytephase$matrix(KnifeHold.getAmounts(side, 0.48, ch, abstractClientPlayerEntity.getSkin().model() == PlayerSkinType.SLIM)));
               float[] pose = new float[3];
               ItemDisplayContext ctx = arm == Arm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
               KnifeFinishTextures.Skin finish = meshes ? KnifeFinishTextures.getTimeRigs(knifeRig, KnifeFinish.getFinish(swordInspect.formatEnumKnifeFinish())) : null;

               for (int i = 0; i < knifeRig.getParentParts(); i++) {
                  matrices.push();

                  try {
                     knifeRig.handlePivotParent(i, ch, pose);
                     matrices.translate(pose[1], pose[2], 0.0F);
                     matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(pose[0]));
                     KnifeMesh mesh = meshes ? KnifeMesh.getTexturesKnife(knifeRig.getParentParts(i).model()) : null;
                     KnifeFinish.Skin skin = finish != null ? finish.getSkins(i) : null;
                     if (mesh != null && mesh.canAmounts(skin)) {
                        orderedRenderCommandQueue.submitCustom(
                           matrices, finish.renderLayerBytephaserenderKnife, (entry, vertices) -> mesh.handleAmountsLight(entry, vertices, armHoldingValue, OverlayTexture.DEFAULT_UV, skin)
                        );
                        if (finish.canLayerRender(i)) {
                           orderedRenderCommandQueue.getBatchingQueue(1).submitCustom(matrices, finish.renderLayer, (entry, vertices2) -> mesh.handleAmounts(entry, vertices2, skin));
                        }

                        if (stack.hasGlint()) {
                           orderedRenderCommandQueue.submitCustom(
                              matrices, RenderLayers.entityGlint(), (entry, vertices3) -> mesh.handleAmountsLight(entry, vertices3, armHoldingValue, OverlayTexture.DEFAULT_UV)
                           );
                        }
                     } else if (mesh != null) {
                        orderedRenderCommandQueue.submitCustom(
                           matrices, KnifeMesh.getCullCutout(), (entry, vertices4) -> mesh.handleAmountsLight(entry, vertices4, armHoldingValue, OverlayTexture.DEFAULT_UV)
                        );
                        if (stack.hasGlint()) {
                           orderedRenderCommandQueue.submitCustom(
                              matrices, RenderLayers.entityGlint(), (entry, vertices5) -> mesh.handleAmountsLight(entry, vertices5, armHoldingValue, OverlayTexture.DEFAULT_UV)
                           );
                        }
                     } else {
                        matrices.translate(8.0F, 8.0F, 8.0F);
                        matrices.scale(16.0F, 16.0F, 16.0F);
                        this.renderItem(abstractClientPlayerEntity, swordInspect.getModelProbe(stack, knifeRig, i), ctx, matrices, orderedRenderCommandQueue, armHoldingValue);
                     }
                  } finally {
                     matrices.pop();
                  }
               }
            } finally {
               matrices.pop();
            }

            return true;
         }
      }
   }

   @Unique
   private static Matrix4f bytephase$matrix(double[] bytephasematrixValues) {
      return new Matrix4f(
         (float)bytephasematrixValues[0],
         (float)bytephasematrixValues[4],
         (float)bytephasematrixValues[8],
         (float)bytephasematrixValues[12],
         (float)bytephasematrixValues[1],
         (float)bytephasematrixValues[5],
         (float)bytephasematrixValues[9],
         (float)bytephasematrixValues[13],
         (float)bytephasematrixValues[2],
         (float)bytephasematrixValues[6],
         (float)bytephasematrixValues[10],
         (float)bytephasematrixValues[14],
         (float)bytephasematrixValues[3],
         (float)bytephasematrixValues[7],
         (float)bytephasematrixValues[11],
         (float)bytephasematrixValues[15]
      );
   }

   @Unique
   private HandCustomer bytephase$handCustomer() {
      return (HandCustomer)ModuleRegistry.getModule(HandCustomer.class);
   }

   @Inject(
      method = {"renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephase$renderFirstPersonItem(
      AbstractClientPlayerEntity abstractClientPlayerEntity,
      float bytephaserenderFirstValue,
      float bytephaserenderFirstValue2,
      Hand hand,
      float bytephaserenderKnifeValue,
      ItemStack stack,
      float equipOffsetValue,
      MatrixStack matrices,
      OrderedRenderCommandQueue orderedRenderCommandQueue,
      int glintEnumValue,
      CallbackInfo callbackInfo
   ) {
      SwordInspect si = (SwordInspect)ModuleRegistry.getModule(SwordInspect.class);
      if (si != null && si.canSwappedSlot(abstractClientPlayerEntity, hand, stack)) {
         si.handleGlintEnum(abstractClientPlayerEntity, stack, matrices, orderedRenderCommandQueue, glintEnumValue);
         callbackInfo.cancel();
      } else {
         if (si != null && !abstractClientPlayerEntity.isUsingSpyglass() && !abstractClientPlayerEntity.isInvisible()) {
            KnifeRig rig = si.getHand(abstractClientPlayerEntity, hand, stack);
            if (rig != null && (rig != KnifeRig.BUTTERFLY_TP || si.isSwappedButterfly()) && (!abstractClientPlayerEntity.isUsingItem() || abstractClientPlayerEntity.getActiveHand() != hand)) {
               Arm kArm = abstractClientPlayerEntity.getMainArm();
               if (this.bytephase$renderKnifeInHand(si, rig, abstractClientPlayerEntity, kArm, stack, bytephaserenderKnifeValue, equipOffsetValue, matrices, orderedRenderCommandQueue, glintEnumValue)) {
                  callbackInfo.cancel();
                  return;
               }
            }
         }

         if (si != null && si.canEmptyHand(hand, stack) && !abstractClientPlayerEntity.isUsingSpyglass()) {
            callbackInfo.cancel();
            Arm iArm = hand == Hand.MAIN_HAND ? abstractClientPlayerEntity.getMainArm() : abstractClientPlayerEntity.getMainArm().getOpposite();
            int iSide = iArm == Arm.RIGHT ? 1 : -1;
            matrices.push();
            si.handleMultiplyAnim(matrices, iSide, iArm);
            if (!abstractClientPlayerEntity.isInvisible()) {
               matrices.push();
               si.handleMultiplyTranslate(matrices, iSide, iArm);
               this.renderArmHoldingItem(matrices, orderedRenderCommandQueue, glintEnumValue, equipOffsetValue, 0.0F, iArm);
               matrices.pop();
            }

            this.applyEquipOffset(matrices, iArm, equipOffsetValue);
            si.handleAnimTranslate(matrices, iSide, iArm);
            si.handleAnimInspect(matrices, iSide, iArm);
            this.renderItem(
               abstractClientPlayerEntity,
               stack,
               iArm == Arm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
               matrices,
               orderedRenderCommandQueue,
               glintEnumValue
            );
            matrices.pop();
         } else {
            HandCustomer hc = this.bytephase$handCustomer();
            if (hc != null && hc.isBase()) {
               if (!abstractClientPlayerEntity.isUsingSpyglass()) {
                  callbackInfo.cancel();
                  this.bytephase$renderFirstPersonItemInternal(
                     hc, abstractClientPlayerEntity, bytephaserenderFirstValue, bytephaserenderFirstValue2, hand, bytephaserenderKnifeValue, stack, equipOffsetValue, matrices, orderedRenderCommandQueue, glintEnumValue
                  );
               }
            }
         }
      }
   }

   @Unique
   private void bytephase$renderFirstPersonItemInternal(
      HandCustomer handCustomer,
      AbstractClientPlayerEntity abstractClientPlayerEntity,
      float bytephaserenderFirstValue,
      float bytephaserenderFirstValue2,
      Hand hand,
      float swingArmValue,
      ItemStack stack,
      float equipOffsetValue,
      MatrixStack matrices,
      OrderedRenderCommandQueue orderedRenderCommandQueue,
      int armHoldingValue
   ) {
      boolean isMainHand = hand == Hand.MAIN_HAND;
      Arm arm = isMainHand ? abstractClientPlayerEntity.getMainArm() : abstractClientPlayerEntity.getMainArm().getOpposite();
      matrices.push();
      if (stack.isEmpty()) {
         if (handCustomer.isBase()) {
            handCustomer.handleTranslateRight(matrices, arm);
         }

         if (isMainHand && !abstractClientPlayerEntity.isInvisible()) {
            this.renderArmHoldingItem(matrices, orderedRenderCommandQueue, armHoldingValue, equipOffsetValue, swingArmValue, arm);
         }
      } else if (stack.contains(DataComponentTypes.MAP_ID)) {
         if (handCustomer.isBase()) {
            handCustomer.handleTranslateRight(matrices, arm);
         }

         if (isMainHand && this.offHand.isEmpty()) {
            this.renderMapInBothHands(matrices, orderedRenderCommandQueue, armHoldingValue, bytephaserenderFirstValue2, equipOffsetValue, swingArmValue);
         } else {
            this.renderMapInOneHand(matrices, orderedRenderCommandQueue, armHoldingValue, equipOffsetValue, arm, swingArmValue, stack);
         }
      } else if (stack.isOf(Items.CROSSBOW)) {
         if (handCustomer.isBase()) {
            handCustomer.handleTranslateRight(matrices, arm);
         }

         this.applyEquipOffset(matrices, arm, equipOffsetValue);
         boolean isCharged = CrossbowItem.isCharged(stack);
         boolean isRightArm = arm == Arm.RIGHT;
         int i = isRightArm ? 1 : -1;
         if (abstractClientPlayerEntity.isUsingItem() && abstractClientPlayerEntity.getItemUseTimeLeft() > 0 && abstractClientPlayerEntity.getActiveHand() == hand && !isCharged) {
            matrices.translate(i * -0.4785682F, -0.094387F, 0.05731531F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-11.935F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * 65.3F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * -9.785F));
            float f = stack.getMaxUseTime(abstractClientPlayerEntity) - (abstractClientPlayerEntity.getItemUseTimeLeft() - bytephaserenderFirstValue + 1.0F);
            float g = f / CrossbowItem.getPullTime(stack, abstractClientPlayerEntity);
            float clamp = MathHelper.clamp(g, 0.0F, 1.0F);
            if (clamp > 0.1F) {
               float h = MathHelper.sin((f - 0.1F) * 1.3F);
               float k = h * (clamp - 0.1F);
               matrices.translate(k * 0.0F, k * 0.004F, k * 0.0F);
            }

            matrices.translate(clamp * 0.0F, clamp * 0.0F, clamp * 0.04F);
            matrices.scale(1.0F, 1.0F, 1.0F + clamp * 0.2F);
            matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(i * 45.0F));
         } else {
            this.swingArm(swingArmValue, matrices, i, arm);
            if (isCharged && swingArmValue < 0.001F && isMainHand) {
               matrices.translate(i * -0.641864F, 0.0F, 0.0F);
               matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * 10.0F));
            }
         }

         this.renderItem(
            abstractClientPlayerEntity,
            stack,
            isRightArm ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            matrices,
            orderedRenderCommandQueue,
            armHoldingValue
         );
      } else {
         boolean isRightArm = arm == Arm.RIGHT;
         int side = isRightArm ? 1 : -1;
         if (abstractClientPlayerEntity.isUsingItem() && abstractClientPlayerEntity.getItemUseTimeLeft() > 0 && abstractClientPlayerEntity.getActiveHand() == hand) {
            if (handCustomer.isBase()) {
               handCustomer.handleTranslateRight(matrices, arm);
            }

            UseAction useAction = stack.getUseAction();
            if (!useAction.hasNoOffset()) {
               this.applyEquipOffset(matrices, arm, equipOffsetValue);
            }

            switch (useAction) {
               case NONE:
               default:
                  break;
               case EAT:
               case DRINK:
                  this.applyEatOrDrinkTransformation(matrices, bytephaserenderFirstValue, arm, stack, abstractClientPlayerEntity);
                  this.applyEquipOffset(matrices, arm, equipOffsetValue);
                  break;
               case BLOCK:
                  if (!(stack.getItem() instanceof ShieldItem)) {
                     matrices.translate(side * -0.14142136F, 0.08F, 0.14142136F);
                     matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-102.25F));
                     matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 13.365F));
                     matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 78.05F));
                  }
                  break;
               case BOW: {
                  matrices.translate(side * -0.2785682F, 0.18344387F, 0.15731531F);
                  matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-13.935F));
                  matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 35.3F));
                  matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -9.785F));
                  float f = stack.getMaxUseTime(abstractClientPlayerEntity) - (abstractClientPlayerEntity.getItemUseTimeLeft() - bytephaserenderFirstValue + 1.0F);
                  float gx = f / 20.0F;
                  float bytephaserenderFirstLimit = (gx * gx + gx * 2.0F) / 3.0F;
                  if (bytephaserenderFirstLimit > 1.0F) {
                     bytephaserenderFirstLimit = 1.0F;
                  }

                  if (bytephaserenderFirstLimit > 0.1F) {
                     float h = MathHelper.sin((f - 0.1F) * 1.3F);
                     float k = h * (bytephaserenderFirstLimit - 0.1F);
                     matrices.translate(k * 0.0F, k * 0.004F, k * 0.0F);
                  }

                  matrices.translate(bytephaserenderFirstLimit * 0.0F, bytephaserenderFirstLimit * 0.0F, bytephaserenderFirstLimit * 0.04F);
                  matrices.scale(1.0F, 1.0F, 1.0F + bytephaserenderFirstLimit * 0.2F);
                  matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(side * 45.0F));
                  break;
               }
               case TRIDENT: {
                  matrices.translate(side * -0.5F, 0.7F, 0.1F);
                  matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-55.0F));
                  matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 35.3F));
                  matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -9.785F));
                  float f = stack.getMaxUseTime(abstractClientPlayerEntity) - (abstractClientPlayerEntity.getItemUseTimeLeft() - bytephaserenderFirstValue + 1.0F);
                  float g = f / 10.0F;
                  if (g > 1.0F) {
                     g = 1.0F;
                  }

                  if (g > 0.1F) {
                     float h = MathHelper.sin((f - 0.1F) * 1.3F);
                     float k = h * (g - 0.1F);
                     matrices.translate(k * 0.0F, k * 0.004F, k * 0.0F);
                  }

                  matrices.translate(0.0F, 0.0F, g * 0.2F);
                  matrices.scale(1.0F, 1.0F, 1.0F + g * 0.2F);
                  matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(side * 45.0F));
                  break;
               }
               case BRUSH:
                  this.applyBrushTransformation(matrices, bytephaserenderFirstValue, arm, abstractClientPlayerEntity);
                  break;
               case BUNDLE:
                  this.swingArm(swingArmValue, matrices, side, arm);
                  break;
               case SPEAR: {
                  matrices.translate(side * 0.56F, -0.52F, -0.72F);
                  float f = stack.getMaxUseTime(abstractClientPlayerEntity) - (abstractClientPlayerEntity.getItemUseTimeLeft() - bytephaserenderFirstValue + 1.0F);
                  bytephase$invokeLancingUse(abstractClientPlayerEntity.getTimeSinceLastKineticAttack(bytephaserenderFirstValue), matrices, f, arm, stack);
               }
            }
         } else if (abstractClientPlayerEntity.isUsingRiptide()) {
            if (handCustomer.isBase()) {
               handCustomer.handleTranslateRight(matrices, arm);
            }

            this.applyEquipOffset(matrices, arm, equipOffsetValue);
            matrices.translate(side * -0.4F, 0.8F, 0.3F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 65.0F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -85.0F));
         } else {
            boolean handled = false;
            if (handCustomer.isBase()) {
               handled = handCustomer.canMultiplyDegrees(swingArmValue, equipOffsetValue, matrices, side, arm);
            }

            if (!handled) {
               if (handCustomer.isBase()) {
                  handCustomer.handleTranslateRight(matrices, arm);
               }

               this.applyEquipOffset(matrices, arm, equipOffsetValue);
               switch (stack.getSwingAnimation().type()) {
                  case NONE:
                  default:
                     break;
                  case WHACK:
                     this.swingArm(swingArmValue, matrices, side, arm);
                     break;
                  case STAB:
                     bytephase$invokeLancingSwing(swingArmValue, matrices, side, arm);
               }
            }
         }

         this.renderItem(
            abstractClientPlayerEntity,
            stack,
            isRightArm ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            matrices,
            orderedRenderCommandQueue,
            armHoldingValue
         );
      }

      matrices.pop();
   }

   @Unique
   private static void bytephase$resolveLancing() {
      if (!bytephase$lancingResolved) {
         bytephase$lancingResolved = true;

         try {
            Class<?> lancingClass = Class.forName("net.minecraft.client.render.entity.state.Lancing");

            for (Method m : lancingClass.getDeclaredMethods()) {
               if ((m.getName().equals("method_75391") || m.getName().equals("applySwingTransforms") || m.getName().equals("applySwingOffset"))
                  && m.getParameterCount() == 4) {
                  m.setAccessible(true);
                  bytephase$applySwingTransformsMethod = m;
               }

               if ((m.getName().equals("method_75396") || m.getName().equals("applyUseTransforms") || m.getName().equals("applyUseOffset"))
                  && m.getParameterCount() == 5) {
                  m.setAccessible(true);
                  bytephase$applyUseTransformsMethod = m;
               }
            }
         } catch (Exception e) {
         }
      }
   }

   @Unique
   private static void bytephase$invokeLancingSwing(float bytephaseinvokeLancingValue, MatrixStack matrixStack, int side, Arm arm) {
      bytephase$resolveLancing();
      if (bytephase$applySwingTransformsMethod != null) {
         try {
            bytephase$applySwingTransformsMethod.invoke(null, bytephaseinvokeLancingValue, matrixStack, side, arm);
         } catch (Exception e) {
         }
      }
   }

   @Unique
   private static void bytephase$invokeLancingUse(float bytephaseinvokeLancingValue, MatrixStack matrixStack, float f, Arm arm, ItemStack stack) {
      bytephase$resolveLancing();
      if (bytephase$applyUseTransformsMethod != null) {
         try {
            bytephase$applyUseTransformsMethod.invoke(null, bytephaseinvokeLancingValue, matrixStack, f, arm, stack);
         } catch (Exception e) {
         }
      }
   }
}
