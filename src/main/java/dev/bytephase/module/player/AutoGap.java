package dev.bytephase.module.player;

import dev.bytephase.BytePhase;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.item.Items;

/** Uses AutoEat's input ownership and slot restoration for healing food. */
public final class AutoGap extends AutoEat {
    private final NumberSetting health = add(new NumberSetting("Health", 14, 2, 20, 1));
    private final BooleanSetting enchanted = add(new BooleanSetting("Enchanted", true));
    private final BooleanSetting golden = add(new BooleanSetting("GoldenApple", true));

    public AutoGap() {
        super("AutoGap", "Eats a hotbar golden apple when health is low");
        getSettings().removeIf(setting -> setting.getName().equals("MinHunger")
                || setting.getName().equals("AvoidHarmful") || setting.getName().equals("SaveGoldenApples"));
    }

    public boolean needsHealing() {
        return BytePhase.mc.player != null && BytePhase.mc.player.isAlive()
                && BytePhase.mc.player.getHealth() <= health.getValue() && findFood() >= 0;
    }

    @Override
    protected boolean shouldEat() {
        return needsHealing();
    }

    @Override
    protected int findFood() {
        if (BytePhase.mc.player == null) return -1;
        int normal = -1;
        for (int slot = 0; slot < 9; slot++) {
            var stack = BytePhase.mc.player.getInventory().getStack(slot);
            if (enchanted.getValue() && stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) return slot;
            if (golden.getValue() && normal < 0 && stack.isOf(Items.GOLDEN_APPLE)) normal = slot;
        }
        return normal;
    }
}
