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
 * One-shot version of {@link AutoPot}: switching the module on throws one or
 * two healing potions depending on how low health is, then turns itself off.
 *
 * <p>Two potions are used below {@code DoubleBelow}; the second throw is spaced
 * by a short cooldown so both use packets are accepted. If no potion is
 * available the module disables itself without throwing anything.</p>
 */
public final class ThrowPot extends Module {
    private final NumberSetting doubleBelow = add(new NumberSetting("DoubleBelow", 8.0, 1.0, 19.0, 0.5));
    private final NumberSetting spacing = add(new NumberSetting("Spacing", 8.0, 2.0, 20.0, 1.0));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));

    private int remaining;
    private int waitTicks;

    public ThrowPot() {
        super("ThrowPot", Category.COMBAT, "Throws one or two healing potions when toggled");
    }

    @Override
    protected void onEnable() {
        var mc = BytePhase.mc;
        remaining = 0;
        waitTicks = 0;
        if (mc == null || mc.player == null || mc.player.isDead() || mc.player.isSpectator()) {
            setEnabled(false);
            return;
        }
        if (PotionUtil.findHealingSlot(mc) < 0) {
            setEnabled(false);
            return;
        }
        remaining = mc.player.getHealth() <= doubleBelow.asFloat() ? 2 : 1;
    }

    @Override
    protected void onDisable() {
        remaining = 0;
        waitTicks = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.interactionManager == null
                || mc.currentScreen != null || mc.player.isDead() || mc.player.isSpectator()) {
            return;
        }
        if (remaining <= 0) {
            setEnabled(false);
            return;
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        int slot = PotionUtil.findHealingSlot(mc);
        if (slot < 0) {
            setEnabled(false);
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
        if (!thrown) {
            setEnabled(false);
            return;
        }
        remaining--;
        waitTicks = remaining > 0 ? spacing.asInt() : 0;
    }

}
