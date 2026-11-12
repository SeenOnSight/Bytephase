package dev.bytephase.mixin;

import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantScreen.class)
public interface MerchantScreenAccessor {
    @Accessor("selectedIndex")
    int bytephase$getSelectedIndex();
}
