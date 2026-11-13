package dev.bytephase.pvp.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Environment(EnvType.CLIENT)
@Mixin(LocalPlayer.class)
public interface AccessorLocalPlayer {
    @Accessor("positionReminder")
    void pvp$setPositionReminder(int var1);

    @Accessor("wasSprinting")
    boolean pvp$wasSprinting();

    @Invoker("sendIsSprintingIfNeeded")
    void pvp$sendSprintState();

    @Accessor("lastSentInput")
    Input pvp$lastSentInput();

    @Accessor("xLast")
    double pvp$lastSentX();

    @Accessor("yLast")
    double pvp$lastSentY();

    @Accessor("zLast")
    double pvp$lastSentZ();
}
