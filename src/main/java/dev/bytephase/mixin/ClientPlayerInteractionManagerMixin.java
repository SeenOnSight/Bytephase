package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.combat.Criticals;
import dev.bytephase.util.AttackLimiter;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void bytephase$packetMineStart(BlockPos pos, Direction direction,
                                           CallbackInfoReturnable<Boolean> cir) {
        var packetMine = BytePhase.modules == null ? null : BytePhase.modules.get(
                dev.bytephase.module.world.PacketMine.class);
        if (packetMine != null && packetMine.begin(pos, direction)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void bytephase$packetMineProgress(BlockPos pos, Direction direction,
                                              CallbackInfoReturnable<Boolean> cir) {
        var packetMine = BytePhase.modules == null ? null : BytePhase.modules.get(
                dev.bytephase.module.world.PacketMine.class);
        if (packetMine != null && packetMine.ownsBreaking(pos)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void bytephase$beforeAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        AttackLimiter.markAttack();
        if (BytePhase.modules != null) {
            BytePhase.modules.beforeAttack(player, target);
        }
        Criticals criticals = BytePhase.modules == null ? null : BytePhase.modules.get(Criticals.class);
        if (criticals != null && criticals.isEnabled() && criticals.shouldCrit(player)) {
            criticals.sendCritPackets(player);
        }
    }
}
