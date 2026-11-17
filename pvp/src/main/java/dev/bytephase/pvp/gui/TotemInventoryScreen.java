package dev.bytephase.pvp.gui;

import dev.bytephase.pvp.module.impl.combat.AutoInvTotemModule;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

/** Inventory opened by Auto Inventory Totem; gameplay input cannot edit its slots. */
public final class TotemInventoryScreen extends InventoryScreen {
    private final AutoInvTotemModule owner;

    public TotemInventoryScreen(LocalPlayer player, AutoInvTotemModule owner) {
        super(player);
        this.owner = owner;
    }

    @Override public boolean keyPressed(KeyEvent key) {
        if (key.key() == GLFW.GLFW_KEY_ESCAPE) owner.cancelInventory(this);
        return true;
    }
    @Override public boolean charTyped(CharacterEvent character) { return true; }
    @Override public boolean mouseClicked(MouseButtonEvent click, boolean doubled) { return true; }
    @Override public boolean mouseReleased(MouseButtonEvent click) { return true; }
    @Override public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) { return true; }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return true; }
}
