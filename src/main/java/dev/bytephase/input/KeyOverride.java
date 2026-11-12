package dev.bytephase.input;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import dev.bytephase.mixin.KeyBindingAccessor;
import org.lwjgl.glfw.GLFW;

/** Coordinates temporary key states shared by modules. */
public final class KeyOverride {
    private static final Map<KeyBinding, State> STATES = new IdentityHashMap<>();

    private static final class State {
        final Map<Object, Boolean> owners = new IdentityHashMap<>();
    }

    private KeyOverride() {
    }

    public static void set(Object owner, KeyBinding key, boolean pressed) {
        State state = STATES.computeIfAbsent(key, ignored -> new State());
        state.owners.put(owner, pressed);
        apply(key, state);
    }

    public static void release(Object owner, KeyBinding key) {
        State state = STATES.get(key);
        if (state == null || state.owners.remove(owner) == null) {
            return;
        }
        if (state.owners.isEmpty()) {
            key.setPressed(isPhysicallyPressed(key));
            STATES.remove(key);
        } else {
            apply(key, state);
        }
    }

    public static void releaseAll(Object owner) {
        releaseAll(owner, true);
    }

    /** When entering a text screen, do not reassert physical movement keys. */
    public static void releaseAll(Object owner, boolean restorePhysical) {
        Iterator<Map.Entry<KeyBinding, State>> iterator = STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<KeyBinding, State> entry = iterator.next();
            State state = entry.getValue();
            if (state.owners.remove(owner) == null) {
                continue;
            }
            if (state.owners.isEmpty()) {
                entry.getKey().setPressed(restorePhysical && isPhysicallyPressed(entry.getKey()));
                iterator.remove();
            } else {
                apply(entry.getKey(), state);
            }
        }
    }

    private static void apply(KeyBinding key, State state) {
        // A false claim (for example, while a screen is open) must suppress
        // other modules' true claims until that owner releases the key.
        boolean pressed = true;
        for (boolean ownerPressed : state.owners.values()) {
            if (!ownerPressed) {
                pressed = false;
                break;
            }
        }
        key.setPressed(pressed);
    }

    /** Restore the live hardware state, since it may have changed under an override. */
    public static boolean isPhysicallyPressed(KeyBinding binding) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return false;
        }
        InputUtil.Key key = ((KeyBindingAccessor) binding).bytephase$getBoundKey();
        int code = key.getCode();
        if (code < 0) {
            return false;
        }
        long window = client.getWindow().getHandle();
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, code) == GLFW.GLFW_PRESS;
        }
        return InputUtil.isKeyPressed(client.getWindow(), code);
    }
}
