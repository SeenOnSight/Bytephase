package dev.bytephase.pvp.mixin;

import dev.bytephase.pvp.marlow.MarlowCrystal;
import dev.bytephase.pvp.marlow.crystal.SequencedLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={ClientLevel.class})
public abstract class MarlowClientLevelMixin
implements SequencedLevel {
    @Shadow
    @Final
    private BlockStatePredictionHandler blockStatePredictionHandler;

    @Override
    public int bytephase$marlow$blockSequence() {
        return this.blockStatePredictionHandler.currentSequence();
    }

    @Inject(method={"entitiesForRendering"}, at={@At(value="RETURN")}, cancellable=true)
    private void bytephase$marlow$hideKeptCrystals(CallbackInfoReturnable<Iterable<Entity>> cir) {
        MarlowCrystal mod = MarlowCrystal.get();
        if (!mod.settings().isKeepRender()) {
            cir.setReturnValue(mod.keptCrystals().hide((Iterable)cir.getReturnValue()));
        }
    }

    @Inject(method={"handleBlockChangedAck"}, at={@At(value="HEAD")})
    private void bytephase$marlow$releaseKeptCrystals(int sequence, CallbackInfo ci) {
        MarlowCrystal.get().keptCrystals().acknowledged(sequence);
    }
}

