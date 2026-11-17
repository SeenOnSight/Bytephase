package dev.bytephase.ui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Version and source-license information for the combined client. */
public final class ClientInfoScreen extends Screen {
    private final Screen parent;
    public ClientInfoScreen(Screen parent) { super(Text.literal("Bytephase — client information")); this.parent = parent; }
    @Override protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close())
            .dimensions(width / 2 - 70, height / 2 + 75, 140, 22).build());
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        String version = FabricLoader.getInstance().getModContainer("bytephase").orElseThrow().getMetadata().getVersion().getFriendlyString();
        String[] lines = {"Bytephase " + version, "Minecraft 1.21.11 / Fabric", "",
            "Bytephase utility source: MIT", "Embedded PvP source: GPL-3.0-only",
            "Third-party source and assets: original rights retained", "See the bundled license notices."};
        int y = height / 2 - 65;
        for (String line : lines) {
            context.drawCenteredTextWithShadow(textRenderer, line, width / 2, y, 0xFFE0E0E0);
            y += 15;
        }
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
