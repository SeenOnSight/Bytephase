package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.PotionUtil;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;

/** Throws experience bottles at your feet to mend worn armor. */
public final class AutoExp extends Module {
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final NumberSetting delay = add(new NumberSetting("Delay", 1.0, 0.0, 10.0, 1.0));
    private final NumberSetting repairTo = add(new NumberSetting("RepairTo", 100.0, 10.0, 100.0, 5.0));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));

    private int cooldown;
    private Registry<Enchantment> cachedRegistry;
    private RegistryEntry<Enchantment> mending;

    public AutoExp() {
        super("AutoExp", Category.COMBAT, "Throws XP bottles down to mend damaged armor");
    }

    @Override
    protected void onEnable() {
        cooldown = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || mc.player.isDead() || mc.player.isSpectator()) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (!needsRepair()) {
            return;
        }
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.EXPERIENCE_BOTTLE)) {
                slot = i;
                break;
            }
        }
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

    /** True while any worn Mending piece is below the target durability. */
    private boolean needsRepair() {
        var mc = BytePhase.mc;
        Registry<Enchantment> registry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        if (registry != cachedRegistry) {
            cachedRegistry = registry;
            mending = registry.getEntry(Enchantments.MENDING.getValue()).orElse(null);
        }
        double target = repairTo.getValue() / 100.0;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = mc.player.getEquippedStack(slot);
            if (stack.isEmpty() || !stack.isDamageable() || stack.getDamage() <= 0) {
                continue;
            }
            // XP only repairs Mending gear; throwing at anything else wastes bottles.
            if (mending != null && EnchantmentHelper.getLevel(mending, stack) <= 0) {
                continue;
            }
            double remaining = 1.0 - (double) stack.getDamage() / stack.getMaxDamage();
            if (remaining < target) {
                return true;
            }
        }
        return false;
    }
}
