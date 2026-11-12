package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.InventoryInfo;
import java.util.function.Consumer;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
    @Inject(method = "appendTooltip", at = @At("TAIL"))
    private void bytephase$inventoryInfo(Item.TooltipContext context, TooltipDisplayComponent display,
                                         PlayerEntity player, TooltipType type, Consumer<Text> lines,
                                         CallbackInfo ci) {
        InventoryInfo info = BytePhase.modules == null ? null : BytePhase.modules.inventoryInfo();
        if (info != null) {
            info.append((ItemStack) (Object) this, lines);
        }
    }
}
