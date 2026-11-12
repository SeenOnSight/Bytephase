package dev.bytephase.module.misc;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

/**
 * Shortens the right-click cooldown while use is held down.
 *
 * <p>Blocks and usable items can be
 * toggled separately so an item such as a potion is not spammed by accident.</p>
 */
public final class FastUse extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", 0.0, 0.0, 3.0, 1.0));
    private final BooleanSetting blocks = add(new BooleanSetting("Blocks", true));
    private final BooleanSetting items = add(new BooleanSetting("Items", true));

    public FastUse() {
        super("FastUse", Category.MISC, "Uses blocks and items faster");
    }

    public int delayTicks() {
        return delay.asInt();
    }

    /** @return the cooldown to force, or -1 when this tick should be untouched. */
    public int cooldownOverride() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.currentScreen != null
                || !mc.options.useKey.isPressed()) {
            return -1;
        }
        return canUseFaster(mc.player.getMainHandStack())
                || canUseFaster(mc.player.getOffHandStack()) ? delay.asInt() : -1;
    }

    private boolean canUseFaster(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof BlockItem ? blocks.getValue() : items.getValue();
    }
}
