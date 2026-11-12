package dev.bytephase.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.bytephase.BytePhase;
import dev.bytephase.module.combat.NoHitDelay;
import dev.bytephase.module.misc.FastPlace;
import dev.bytephase.module.misc.FastUse;
import dev.bytephase.module.player.MultiTask;
import dev.bytephase.util.AttackLimiter;
import net.minecraft.item.BlockItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow
    private int itemUseCooldown;

    @Shadow
    private int attackCooldown;

    @Shadow
    @Final
    public GameOptions options;

    @Shadow
    private boolean doAttack() {
        throw new AssertionError();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void bytephase$fastPlace(CallbackInfo ci) {
        AttackLimiter.beginTick();
        FastPlace fastPlace = BytePhase.modules == null ? null : BytePhase.modules.fastPlace();
        ClientPlayerEntity localPlayer = BytePhase.mc == null ? null : BytePhase.mc.player;
        boolean placingBlock = localPlayer != null && this.options.useKey.isPressed()
                && (localPlayer.getMainHandStack().getItem() instanceof BlockItem
                || localPlayer.getOffHandStack().getItem() instanceof BlockItem);
        if (fastPlace != null && placingBlock) {
            this.itemUseCooldown = 0;
        }
        FastUse fastUse = BytePhase.modules == null ? null : BytePhase.modules.fastUse();
        int override = fastUse == null ? -1 : fastUse.cooldownOverride();
        if (override >= 0) {
            this.itemUseCooldown = Math.min(this.itemUseCooldown, override);
        }
        // Vanilla charges ten ticks after a missed attack. Clearing it at the
        // head of the tick is enough because the counter is only decremented
        // and read later in this same tick.
        NoHitDelay noHitDelay = BytePhase.modules == null ? null : BytePhase.modules.noHitDelay();
        if (noHitDelay != null) {
            this.attackCooldown = 0;
        }
    }

    /**
     * Vanilla deliberately routes attack input through a different branch while
     * an item is in use, so the attack key is consumed without ever calling
     * {@code doAttack()}.  Only change that first branch test: keep the rest of
     * the using-item branch intact (notably the stopUsingItem call when the use
     * key is released), and consume/attack the queued attack press here instead.
     */
    @WrapOperation(
            method = "handleInputEvents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z",
                    ordinal = 0
            )
    )
    private boolean bytephase$multiTask(ClientPlayerEntity player, Operation<Boolean> original) {
        boolean using = original.call(player);
        if (!using) {
            return false;
        }
        MultiTask multiTask = BytePhase.modules == null ? null : BytePhase.modules.multiTask();
        if (multiTask != null
                && this.options.attackKey.wasPressed()) {
            this.doAttack();
        }
        return true;
    }
}
