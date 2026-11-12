package dev.bytephase.module.misc;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Box;

/**
 * Removes armor stands and chest minecarts that are used to trap players.
 *
 * <p>The entities are only removed
 * client-side, which is what lets a trapped player walk out of the hitbox.</p>
 */
public final class AntiTrap extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", 6.0, 1.0, 16.0, 1.0));
    private final BooleanSetting armorStands = add(new BooleanSetting("ArmorStands", true));
    private final BooleanSetting chestMinecarts = add(new BooleanSetting("ChestMinecarts", true));
    private int cooldown;

    public AntiTrap() {
        super("AntiTrap", Category.MISC, "Removes armor stands and chest minecarts");
    }

    @Override
    protected void onDisable() {
        cooldown = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        cooldown = 5;
        double rangeSq = range.getValue() * range.getValue();
        Box search = mc.player.getBoundingBox().expand(range.getValue());
        for (Entity entity : mc.world.getOtherEntities(mc.player, search)) {
            if (entity == mc.getCameraEntity() || !isTrap(entity)
                    || entity.squaredDistanceTo(mc.player) > rangeSq) {
                continue;
            }
            if (entity.hasPassenger(mc.player)) {
                continue;
            }
            entity.discard();
        }
    }

    private boolean isTrap(Entity entity) {
        if (armorStands.getValue() && entity.getType() == EntityType.ARMOR_STAND) {
            return true;
        }
        return chestMinecarts.getValue() && entity.getType() == EntityType.CHEST_MINECART;
    }
}
