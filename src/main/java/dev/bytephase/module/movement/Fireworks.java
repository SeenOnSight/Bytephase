package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/** Fires a rocket from your inventory to keep an elytra glide going. */
public final class Fireworks extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", 2.5, 0.5, 10.0, 0.5));
    private final NumberSetting minSpeed = add(new NumberSetting("MinSpeed", 0.8, 0.1, 3.0, 0.1));

    private int cooldown;

    public Fireworks() {
        super("Fireworks", Category.MOVEMENT, "Uses a firework rocket when gliding slows down");
    }

    @Override
    protected void onEnable() {
        cooldown = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.interactionManager == null || mc.currentScreen != null
                || !mc.player.isGliding()) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        var velocity = mc.player.getVelocity();
        if (Math.hypot(velocity.x, velocity.z) >= minSpeed.getValue()) {
            return;
        }
        // Prefer the offhand so the selected slot never changes.
        if (mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            mc.player.swingHand(Hand.OFF_HAND);
            cooldown = (int) Math.round(delay.getValue() * 20.0);
            return;
        }
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            return;
        }
        int previousSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.getInventory().setSelectedSlot(previousSlot);
        cooldown = (int) Math.round(delay.getValue() * 20.0);
    }
}
