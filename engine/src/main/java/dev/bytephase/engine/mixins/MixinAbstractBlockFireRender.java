package dev.bytephase.engine.mixins;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.bytephase.engine.categories.visuals.CustomFlame;

@Mixin({AbstractBlock.class})
public class MixinAbstractBlockFireRender {
   @Inject(
      method = {"getRenderType(Lnet/minecraft/block/BlockState;)Lnet/minecraft/block/BlockRenderType;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bytephase_hideFireForCustomFlame(BlockState blockState, CallbackInfoReturnable<BlockRenderType> callbackInfoReturnable) {
      if ((Object) this instanceof AbstractFireBlock && CustomFlame.isBaseRestyleWorldFire()) {
         callbackInfoReturnable.setReturnValue(BlockRenderType.INVISIBLE);
      }
   }
}
