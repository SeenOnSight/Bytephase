package dev.bytephase.mixin;

import dev.bytephase.BytePhase;
import dev.bytephase.module.render.ExtraTab;
import net.minecraft.client.gui.hud.PlayerListHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @ModifyConstant(method = "collectPlayerEntries", constant = @Constant(longValue = 80L))
    private long bytephase$extraTabLimit(long original) {
        ExtraTab extraTab = BytePhase.modules == null ? null : BytePhase.modules.extraTab();
        return extraTab == null || !extraTab.isEnabled() ? original : extraTab.limit();
    }
}
