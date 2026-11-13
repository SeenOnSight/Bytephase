package dev.bytephase.pvp.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.function.Supplier;
import dev.bytephase.pvp.util.pvp.Rotation;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinRotationMultiPlayerGameMode {
    @Shadow
    @Final
    private Minecraft minecraft;

    @WrapMethod(method = "useItem")
    private InteractionResult pvp$useItemRotation(Player player, InteractionHand hand, Operation<InteractionResult> original) {
        return this.pvp$asSent(player, () -> original.call(player, hand));
    }

    @WrapMethod(method = "releaseUsingItem")
    private void pvp$releaseRotation(Player player, Operation<Void> original) {
        this.pvp$asSent(player, () -> original.call(player));
    }

    private <T> T pvp$asSent(Player player, Supplier<T> call) {
        LocalPlayer self = this.minecraft.player;
        if (player == self && !ServerRotation.isSwapped()) {
            Rotation rotation = ServerRotation.commit(self);
            float yaw = self.getYRot();
            float pitch = self.getXRot();
            if (rotation.yaw() == yaw && rotation.pitch() == pitch) {
                return call.get();
            }

            self.setYRot(rotation.yaw());
            self.setXRot(rotation.pitch());

            try {
                return call.get();
            } finally {
                self.setYRot(yaw);
                self.setXRot(pitch);
            }
        } else {
            return call.get();
        }
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true, order = 950)
    private void pvp$attackRotationGate(Player player, Entity target, CallbackInfo ci) {
        LocalPlayer self = this.minecraft.player;
        if (player == self) {
            AABB box = target.getBoundingBox();
            double reach = self.entityInteractionRange();
            AttackRange range = self.getMainHandItem().get(DataComponents.ATTACK_RANGE);
            if (range != null) {
                box = box.inflate(range.hitboxMargin());
                reach = range.effectiveMaxRange(self);
            }

            if (ServerRotation.misses(self, box, reach)) {
                player.resetAttackStrengthTicker();
                ci.cancel();
            }
        }
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true, order = 950)
    private void pvp$interactRotationGate(Player player, Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        LocalPlayer self = this.minecraft.player;
        if (player == self && ServerRotation.misses(self, target.getBoundingBox(), self.entityInteractionRange())) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "interactAt", at = @At("HEAD"), cancellable = true, order = 950)
    private void pvp$interactAtRotationGate(
        Player player, Entity target, EntityHitResult hit, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir
    ) {
        LocalPlayer self = this.minecraft.player;
        if (player == self && ServerRotation.misses(self, target.getBoundingBox(), self.entityInteractionRange())) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, order = 950)
    private void pvp$useItemOnRotationGate(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (player == this.minecraft.player && ServerRotation.missesBlock(player, new AABB(hit.getBlockPos()), player.blockInteractionRange())) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true, order = 950)
    private void pvp$startDestroyRotationGate(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer self = this.minecraft.player;
        if (self != null && ServerRotation.missesBlock(self, new AABB(pos), self.blockInteractionRange())) {
            cir.setReturnValue(false);
        }
    }
}
