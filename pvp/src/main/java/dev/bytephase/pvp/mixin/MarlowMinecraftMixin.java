package dev.bytephase.pvp.mixin;

import dev.bytephase.pvp.marlow.MarlowCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={Minecraft.class})
public class MarlowMinecraftMixin {
    @Inject(method={"tick"}, at={@At(value="HEAD")})
    private void bytephase$marlow$countTick(CallbackInfo ci) {
        Minecraft client = (Minecraft)(Object)this;
        if (client.level != null && !client.isPaused()) {
            MarlowCrystal.get().keptCrystals().ticked();
        }
    }

    @Inject(method={"startAttack"}, at={@At(value="HEAD")})
    private void bytephase$marlow$aimPastKeptCrystal(CallbackInfoReturnable<Boolean> cir) {
        MarlowCrystal mod = MarlowCrystal.get();
        if (!mod.serverSession().isOptedOut()) {
            mod.pastKeptCrystal().attackStarted((Minecraft)(Object)this);
        }
    }

    @Inject(method={"startAttack"}, at={@At(value="RETURN")})
    private void bytephase$marlow$restoreAttackCrosshair(CallbackInfoReturnable<Boolean> cir) {
        MarlowCrystal.get().pastKeptCrystal().attackEnded((Minecraft)(Object)this);
    }

    @Inject(method={"startUseItem"}, at={@At(value="HEAD")})
    private void bytephase$marlow$placePastKeptCrystal(CallbackInfo ci) {
        MarlowCrystal mod = MarlowCrystal.get();
        if (!mod.serverSession().isOptedOut()) {
            mod.pastKeptCrystal().useStarted((Minecraft)(Object)this);
        }
    }

    @ModifyVariable(method={"startUseItem"}, at=@At(value="STORE"), ordinal=0)
    private ItemStack bytephase$marlow$aimForHand(ItemStack stack) {
        MarlowCrystal.get().pastKeptCrystal().handTried((Minecraft)(Object)this, stack);
        return stack;
    }

    @Inject(method={"startUseItem"}, at={@At(value="RETURN")})
    private void bytephase$marlow$restoreUseCrosshair(CallbackInfo ci) {
        MarlowCrystal.get().pastKeptCrystal().useEnded((Minecraft)(Object)this);
    }
}

