package dev.bytephase.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Initializes data controllers without initializing or drawing their old widgets. */
@Mixin(Screen.class)
public interface ScreenDataAccessor {
    @Accessor("client") void bytephase$setClient(MinecraftClient client);
    @Accessor("textRenderer") void bytephase$setTextRenderer(TextRenderer renderer);
}
