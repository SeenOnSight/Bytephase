package dev.bytephase.pvp.mixin;

import dev.bytephase.pvp.module.ModuleRegistry;
import dev.bytephase.pvp.module.impl.combat.SwordObsidianModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinSwordObsidianMinecraft {
    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void bytephase$swordObsidian(CallbackInfo ci) {
        var module = ModuleRegistry.get(SwordObsidianModule.class);
        if (module != null && module.isEnabled()) module.beforeUse((Minecraft) (Object) this);
    }
}
