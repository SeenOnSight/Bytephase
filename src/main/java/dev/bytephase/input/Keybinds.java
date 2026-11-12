package dev.bytephase.input;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Module;
import dev.bytephase.module.player.TradeAssist;
import dev.bytephase.ui.ClickGuiScreen;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class Keybinds {
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("bytephase", "main"));
    private static final Map<Module, KeyBinding> BINDINGS = new LinkedHashMap<>();

    private static KeyBinding clickGuiKey;
    private static boolean suppressNextClickGui;
    private static boolean screenOpen;

    private Keybinds() {
    }

    public static void init() {
        clickGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.bytephase.clickgui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
        for (Module module : BytePhase.modules.all()) {
            KeyBinding binding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.bytephase." + module.getName().toLowerCase(Locale.ROOT),
                    InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
            BINDINGS.put(module, binding);
        }
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof MerchantScreen)) {
                return;
            }
            // Vanilla does not queue ordinary module keybinds inside a handled
            // screen. Consume this bind before it can also move/drop an item.
            boolean[] held = {false};
            ScreenKeyboardEvents.allowKeyPress(screen).register((current, input) -> {
                Module module = merchantModule(input);
                if (module == null || client.currentScreen != current || !client.isWindowFocused()) {
                    return true;
                }
                if (!held[0]) {
                    held[0] = true;
                    module.toggle();
                }
                return false;
            });
            ScreenKeyboardEvents.allowKeyRelease(screen).register((current, input) -> {
                if (merchantModule(input) == null) {
                    return true;
                }
                held[0] = false;
                return false;
            });
        });
    }

    public static void tick(MinecraftClient mc) {
        if (clickGuiKey == null) {
            return;
        }

        if (suppressNextClickGui) {
            while (clickGuiKey.wasPressed()) {
                // The key press that closed the screen must not reopen it.
            }
            suppressNextClickGui = false;
        } else if (mc.currentScreen == null && clickGuiKey.wasPressed()) {
            mc.setScreen(new ClickGuiScreen());
        }

        if (mc.currentScreen != null) {
            while (clickGuiKey.wasPressed()) {
            }
            for (KeyBinding binding : BINDINGS.values()) {
                while (binding.wasPressed()) {
                }
            }
            screenOpen = true;
            return;
        }

        if (screenOpen) {
            // The screen just closed. Drop any presses that occurred while it
            // was open instead of toggling modules on the first game tick.
            screenOpen = false;
            while (clickGuiKey.wasPressed()) {
            }
            for (KeyBinding binding : BINDINGS.values()) {
                while (binding.wasPressed()) {
                }
            }
            return;
        }

        for (Map.Entry<Module, KeyBinding> entry : BINDINGS.entrySet()) {
            while (entry.getValue().wasPressed()) {
                Module module = entry.getKey();
                if (module instanceof dev.bytephase.module.combat.Surround surround
                        && surround.activateFromKey()) continue;
                module.toggle();
            }
        }
    }

    public static void set(Module module, int key) {
        KeyBinding binding = BINDINGS.get(module);
        if (binding == null) {
            return;
        }
        InputUtil.Key newKey = key == GLFW.GLFW_KEY_UNKNOWN
                ? InputUtil.UNKNOWN_KEY
                : InputUtil.Type.KEYSYM.createFromCode(key);
        binding.setBoundKey(newKey);
        KeyBinding.updateKeysByCode();
        KeyBinding.updatePressedStates();
        MinecraftClient mc = BytePhase.mc;
        if (mc != null && mc.options != null) {
            mc.options.write();
        }
    }

    public static String getKeyName(Module module) {
        KeyBinding binding = BINDINGS.get(module);
        return binding == null ? "?" : binding.getBoundKeyLocalizedText().getString();
    }

    /** Whether this key event is the configured ClickGUI open/close key. */
    public static boolean matchesClickGui(KeyInput input) {
        return clickGuiKey != null && clickGuiKey.matchesKey(input);
    }

    public static void suppressNextClickGui() {
        suppressNextClickGui = true;
    }

    private static Module merchantModule(KeyInput input) {
        for (Map.Entry<Module, KeyBinding> entry : BINDINGS.entrySet()) {
            if (entry.getKey() instanceof TradeAssist && entry.getValue().matchesKey(input)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
