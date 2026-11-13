package dev.bytephase.pvp.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.bytephase.pvp.module.ModuleRegistry;
import dev.bytephase.pvp.module.impl.combat.CrystalMacroModule;
import dev.bytephase.pvp.module.impl.combat.crystal.Crystals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinCrystalMacroMultiPlayerGameMode {
    @WrapMethod(method = "useItemOn")
    private InteractionResult bytephase$trackCrystalPlacement(
            LocalPlayer player, InteractionHand hand, BlockHitResult hit,
            Operation<InteractionResult> original) {
        Minecraft mc = Minecraft.getInstance();
        CrystalMacroModule macro = ModuleRegistry.get(CrystalMacroModule.class);
        BlockPos base = hit.getBlockPos();
        // Capture the held item before vanilla consumes the final crystal.
        boolean track = macro != null && macro.isEnabled() && player == mc.player
            && mc.level != null && player.getItemInHand(hand).is(Items.END_CRYSTAL)
            && Crystals.isBase(mc.level.getBlockState(base));
        if (track && !macro.canPlaceCrystal(player, base)) {
            return InteractionResult.FAIL;
        }
        InteractionResult result = original.call(player, hand, hit);
        if (track && result.consumesAction()) {
            macro.placedCrystal(player, base);
        }
        return result;
    }
}
