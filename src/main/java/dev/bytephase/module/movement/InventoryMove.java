package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.input.KeyOverride;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

/** Allows physical movement keys in inventory screens, not chat or text fields. */
public final class InventoryMove extends Module {
    private boolean active;

    public InventoryMove() {
        super("InventoryMove", Category.MOVEMENT, "Move while an inventory or container is open");
    }

    public void updateKeys() {
        var mc = BytePhase.mc;
        if (mc == null || mc.options == null) return;
        boolean allowed = mc.player != null && mc.currentScreen instanceof HandledScreen<?> screen
                && !(screen instanceof CreativeInventoryScreen)
                && !(screen.getFocused() instanceof TextFieldWidget)
                && GLFW.glfwGetWindowAttrib(mc.getWindow().getHandle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        if (!allowed) {
            release(mc);
            return;
        }
        active = true;
        for (KeyBinding key : keys(mc)) {
            if (KeyOverride.isPhysicallyPressed(key)) KeyOverride.set(this, key, true);
            else KeyOverride.release(this, key);
        }
    }

    @Override
    protected void onDisable() {
        var mc = BytePhase.mc;
        if (mc != null && mc.options != null) release(mc);
        else KeyOverride.releaseAll(this);
    }

    private void release(net.minecraft.client.MinecraftClient mc) {
        if (!active) return;
        KeyOverride.releaseAll(this, mc.currentScreen == null);
        active = false;
    }

    private static KeyBinding[] keys(net.minecraft.client.MinecraftClient mc) {
        return new KeyBinding[]{mc.options.forwardKey, mc.options.backKey, mc.options.leftKey,
                mc.options.rightKey, mc.options.jumpKey, mc.options.sprintKey};
    }
}
