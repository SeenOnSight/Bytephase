package dev.bytephase.pvp.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(MouseHandler.class)
public interface AccessorMouseHandler {
    @Accessor("accumulatedDX")
    void easyplace$setAccumulatedDX(double var1);

    @Accessor("accumulatedDY")
    void easyplace$setAccumulatedDY(double var1);
}
