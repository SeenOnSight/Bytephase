package dev.bytephase.engine.mixins;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({HandledScreen.class})
public interface AccessorHandledScreen {
   @Accessor("x")
   int bytephaseX();

   @Accessor("y")
   int bytephaseY();

   @Accessor("backgroundWidth")
   int bytephaseBackgroundWidth();
}
