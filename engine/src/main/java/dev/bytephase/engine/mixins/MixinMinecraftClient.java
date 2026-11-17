package dev.bytephase.engine.mixins;

import dev.bytephase.engine.entity.ClientThreadUtils;
import dev.bytephase.engine.render.FramebufferSimpleUtils;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.bytephase.engine.categories.bots.AnchorHunter;
import dev.bytephase.engine.categories.bots.AutoAreaMiner;
import dev.bytephase.engine.categories.bots.AutoBuilder;
import dev.bytephase.engine.categories.bots.AutoElytraFinder;
import dev.bytephase.engine.utils.LightPacketCache;
import dev.bytephase.engine.categories.bots.SpawnerProtect;
import dev.bytephase.engine.utils.Cooldown;
import ai.auth.ModuleRegistry;
import dev.bytephase.engine.utils.ScaffoldTrade;

@Mixin({MinecraftClient.class})
public class MixinMinecraftClient {
   @Shadow
   @Nullable
   public ClientWorld world;
   @Shadow
   private int itemUseCooldown;
   @Shadow
   protected int attackCooldown;

   @Inject(
      method = {"doItemUse()V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onItemUseHead(CallbackInfo callbackInfo) {
      if (ClientThreadUtils.isDequeArray()) {
         ClientThreadUtils.update();
         callbackInfo.cancel();
      } else {
         ScaffoldTrade event = new ScaffoldTrade();
         ModuleRegistry.dispatchPreItemUse(event);
         if (event.isFlag()) {
            ClientThreadUtils.update();
            callbackInfo.cancel();
         }
      }
   }

   @Inject(
      method = {"doItemUse()V"},
      at = {@At("RETURN")}
   )
   private void onItemUseReturn(CallbackInfo callbackInfo) {
      Cooldown event = new Cooldown(this.itemUseCooldown);
      ModuleRegistry.dispatchPostItemUse(event);
      this.itemUseCooldown = event.useCooldown;
      ClientThreadUtils.update();
   }

   @Inject(
      method = {"handleInputEvents()V"},
      at = {@At("RETURN")}
   )
   private void bytephaseAfterInputEvents(CallbackInfo callbackInfo) {
      ClientThreadUtils.updateOptionsInstance();
   }

   @Inject(
      method = {"tick()V"},
      at = {@At("HEAD")}
   )
   private void onTick(CallbackInfo callbackInfo) {
      LightPacketCache.INSTANCE.processPendingLight();
      if (this.world != null) {
         ModuleRegistry.onGameTick();
      }
   }

   @Inject(
      method = {"tick()V"},
      at = {@At("RETURN")}
   )
   private void bytephaseAfterTick(CallbackInfo callbackInfo) {
      ClientThreadUtils.updateFlag();
   }

   @Inject(
      method = {"handleBlockBreaking(Z)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephaseBotOwnsBreak(boolean bytephaseBotFlag, CallbackInfo callbackInfo) {
      AutoBuilder builder = (AutoBuilder)ModuleRegistry.getModule(AutoBuilder.class);
      if (builder != null && builder.didBlockUpdateThisTick()) {
         callbackInfo.cancel();
      } else {
         SpawnerProtect spawnerProtect = (SpawnerProtect)ModuleRegistry.getModule(SpawnerProtect.class);
         if (spawnerProtect != null && spawnerProtect.isBreakingSpawner()) {
            callbackInfo.cancel();
         } else {
            AutoElytraFinder elytra = (AutoElytraFinder)ModuleRegistry.getModule(AutoElytraFinder.class);
            if (elytra != null && elytra.isBreakingBlock()) {
               callbackInfo.cancel();
            } else {
               if (ClientThreadUtils.isFlag2()) {
                  callbackInfo.cancel();
               }
            }
         }
      }
   }

   @ModifyVariable(
      method = {"handleBlockBreaking(Z)V"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private boolean bytephaseMinerV2HoldsAttack(boolean bytephaseMinerFlag) {
      if (bytephaseMinerFlag) {
         return true;
      } else {
         AutoAreaMiner miner = (AutoAreaMiner)ModuleRegistry.getModule(AutoAreaMiner.class);
         if (miner == null || !miner.isEngineActive()) {
            AnchorHunter hunter = (AnchorHunter)ModuleRegistry.getModule(AnchorHunter.class);
            if (hunter == null || !hunter.isMiningAnchor()) {
               return false;
            }
         }

         this.attackCooldown = 0;
         return true;
      }
   }

   @Inject(
      method = {"doAttack()Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephaseSilentAimAttack(CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
      Boolean result = ClientThreadUtils.getMaxDeque();
      if (result != null) {
         callbackInfoReturnable.setReturnValue(result);
      }
   }

   @Inject(
      method = {"doAttack()Z"},
      at = {@At("RETURN")}
   )
   private void bytephaseAfterAttack(CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
      ClientThreadUtils.updateMax();
   }

   @ModifyReturnValue(
      method = {"getFramebuffer()Lnet/minecraft/client/gl/Framebuffer;"},
      at = {@At("RETURN")}
   )
   private Framebuffer bytephase$gui3dFramebuffer(Framebuffer framebuffer) {
      Framebuffer override = FramebufferSimpleUtils.getFramebuffer();
      return override != null ? override : framebuffer;
   }

   @WrapOperation(
      method = {"render(Z)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/GameRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;Z)V"
      )}
   )
   private void bytephase$gui3dGuard(GameRenderer gameRenderer, RenderTickCounter renderTickCounter, boolean bytephasegui3dGuardFlag, Operation<Void> operation) {
      try {
         operation.call(new Object[]{gameRenderer, renderTickCounter, bytephasegui3dGuardFlag});
      } finally {
         FramebufferSimpleUtils.updateCompositeBegin();
      }
   }

   @Inject(
      method = {"onResolutionChanged()V"},
      at = {@At("HEAD")}
   )
   private void bytephase$gui3dResize(CallbackInfo callbackInfo) {
      FramebufferSimpleUtils.updateCompositeBegin();
   }
}
