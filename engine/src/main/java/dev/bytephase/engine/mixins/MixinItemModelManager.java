package dev.bytephase.engine.mixins;

import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import dev.bytephase.engine.categories.visuals.SwordInspect;
import ai.auth.ModuleRegistry;

@Mixin({ItemModelManager.class})
public abstract class MixinItemModelManager {
   @ModifyVariable(
      method = {"updateForLivingEntity(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/entity/LivingEntity;)V"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private ItemStack bytephase$thirdPersonKnife(ItemStack modelThird, ItemRenderState itemRenderState, ItemStack itemStack, ItemDisplayContext itemDisplayContext, LivingEntity livingEntity) {
      SwordInspect si = (SwordInspect)ModuleRegistry.getModule(SwordInspect.class);
      return si == null ? modelThird : si.getModelThird(livingEntity, modelThird, itemDisplayContext);
   }
}
