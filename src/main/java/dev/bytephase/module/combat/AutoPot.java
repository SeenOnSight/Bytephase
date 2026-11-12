package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.PotionUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * Throws a healing splash potion when health drops below the threshold.
 *
 * <p>The potion is thrown straight down so it lands on the player rather than
 * on the floor ahead. When {@code Rotate} is on the module sends a look packet
 * pointing at the ground before the throw and restores the camera pitch
 * immediately afterwards, so the player's own aim is not permanently moved.</p>
 */
public final class AutoPot extends Module {
    private final NumberSetting health = add(new NumberSetting("Health", 10.0, 1.0, 20.0, 0.5));
    private final NumberSetting delay = add(new NumberSetting("Delay", 10.0, 1.0, 40.0, 1.0));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));

    private int cooldown;

    public AutoPot() {
        super("AutoPot", Category.COMBAT, "Throws a healing splash potion when health is low");
    }

    @Override
    protected void onEnable() {
        cooldown = 0;
    }

    @Override
    protected void onDisable() {
        cooldown = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.interactionManager == null
                || mc.currentScreen != null || mc.player.isDead() || mc.player.isSpectator()) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (mc.player.getHealth() > health.asFloat()) {
            return;
        }
        int slot = PotionUtil.findHealingSlot(mc);
        if (slot < 0) {
            return;
        }
        int previousSlot = mc.player.getInventory().getSelectedSlot();
        if (previousSlot != slot) {
            mc.player.getInventory().setSelectedSlot(slot);
        }
        boolean thrown = PotionUtil.throwDownward(mc, rotate.getValue());
        if (previousSlot != slot && mc.player.getInventory().getSelectedSlot() == slot) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        if (thrown) {
            cooldown = delay.asInt();
        }
    }

}
