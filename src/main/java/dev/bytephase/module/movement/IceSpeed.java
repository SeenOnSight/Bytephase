package dev.bytephase.module.movement;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

/**
 * Replaces the slipperiness of ice-family blocks. Vanilla ice is 0.98, which
 * accelerates slowly but slides a long way; a lower value accelerates and
 * brakes like ground, so lowering it makes ice travel much faster.
 */
public final class IceSpeed extends Module {
    private final NumberSetting friction = add(new NumberSetting("Friction", 0.5, 0.3, 0.98, 0.01));

    public IceSpeed() {
        super("IceSpeed", Category.MOVEMENT, "Makes ice accelerate and move faster");
    }

    public float friction() {
        return friction.asFloat();
    }

    public boolean appliesTo(Block block) {
        return isEnabled() && isIce(block);
    }

    public static boolean isIce(Block block) {
        return block == Blocks.ICE || block == Blocks.PACKED_ICE
                || block == Blocks.BLUE_ICE || block == Blocks.FROSTED_ICE;
    }
}
