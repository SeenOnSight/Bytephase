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
    private static final Map<String, KeyBinding> IMPORTED_BINDINGS = new LinkedHashMap<>();

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
        // Fabric rejects registration after GameOptions has been initialized.
        // Register names now and attach the actual feature instances after GLFW.
        for (String name : dev.bytephase.module.engine.BytephaseIntegration.plannedNames()) {
            String id = name.toLowerCase(Locale.ROOT);
            IMPORTED_BINDINGS.put(id, KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.bytephase." + id, InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY)));
        }
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>)) {
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
            net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).register((current, click) -> {
                Module module = containerMouseModule(click.button());
                if (module == null || client.currentScreen != current || !client.isWindowFocused()) return true;
                if (!held[0]) { held[0] = true; module.toggle(); }
                return false;
            });
            net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseRelease(screen).register((current, click) -> {
                if (containerMouseModule(click.button()) == null) return true;
                held[0] = false;
                return false;
            });
        });
    }

    public static void tick(MinecraftClient mc) {
        if (dev.bytephase.engine.utils.DirExtractionUtils.isFlag()) return;
        dev.bytephase.render.capture.StreamProof.emergencyKey(mc);
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
            for (var entry : BINDINGS.entrySet()) {
                if (entry.getKey() instanceof dev.bytephase.module.engine.BytephaseModule module && module.holdBind()) module.setEnabled(false);
                while (entry.getValue().wasPressed()) {
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
            if (entry.getKey() instanceof dev.bytephase.module.engine.BytephaseModule module && module.holdBind()) {
                module.setEnabled(entry.getValue().isPressed());
                while (entry.getValue().wasPressed()) { }
                continue;
            }
            while (entry.getValue().wasPressed()) {
                Module module = entry.getKey();
                module.toggle();
            }
        }
    }

    public static void attachImportedFeatures() {
        for (Module module : BytePhase.modules.all()) {
            if (!(module instanceof dev.bytephase.module.engine.BytephaseModule)) continue;
            KeyBinding binding = IMPORTED_BINDINGS.get(module.getName().toLowerCase(Locale.ROOT));
            if (binding == null) throw new IllegalStateException("Missing early keybinding for " + module.getName());
            BINDINGS.put(module, binding);
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
        if (module instanceof dev.bytephase.module.engine.BytephaseModule imported) imported.bindKey(key);
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

    public static void restoreLegacyBindings() {
        java.nio.file.Path options = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("options.txt");
        if (!java.nio.file.Files.isRegularFile(options)) return;
        try {
            for (String line : java.nio.file.Files.readAllLines(options)) {
                int split = line.indexOf(':');
                if (split < 0 || !dev.bytephase.config.EngineDataMigration.legacyRadioKey(line.substring(0, split))) continue;
                Module module = BytePhase.modules.getByName("BytephaseRadio");
                KeyBinding binding = BINDINGS.get(module);
                if (binding == null || keyCode(module) >= 0) continue;
                InputUtil.Key key = InputUtil.fromTranslationKey(line.substring(split + 1));
                binding.setBoundKey(key);
                if (module instanceof dev.bytephase.module.engine.BytephaseModule engine)
                    engine.bindKey(key.getCategory() == InputUtil.Type.MOUSE ? 1000 + key.getCode() : key.getCode());
                KeyBinding.updateKeysByCode();
            }
        } catch (java.io.IOException | IllegalArgumentException error) {
            BytePhase.LOGGER.warn("Could not restore a previous module shortcut", error);
        }
    }

    public static int keyCode(Module module) {
        KeyBinding binding = BINDINGS.get(module);
        if (binding == null) return -1;
        InputUtil.Key key = ((dev.bytephase.mixin.KeyBindingAccessor) binding).bytephase$getBoundKey();
        return key.getCategory() == InputUtil.Type.MOUSE ? 1000 + key.getCode() : key.getCode();
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
            if ((entry.getKey() instanceof TradeAssist && BytePhase.mc.currentScreen instanceof MerchantScreen
                || entry.getKey() instanceof dev.bytephase.module.engine.BytephaseModule module && module.containerBind())
                && entry.getValue().matchesKey(input)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static Module containerMouseModule(int button) {
        for (Module module : BINDINGS.keySet()) {
            if (module instanceof dev.bytephase.module.engine.BytephaseModule imported
                && imported.containerBind() && keyCode(module) == 1000 + button) return module;
        }
        return null;
    }
}
