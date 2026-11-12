package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public final class AutoArmor extends Module {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    public final NumberSetting delay = add(new NumberSetting("Delay", 2.0, 0.0, 20.0, 1.0));
    public final BooleanSetting keepElytra = add(new BooleanSetting("KeepElytra", false));

    private int cooldown;
    // The attribute callback is synchronous on the client thread; reuse this
    // accumulator (armor, toughness, knockback resistance) instead of
    // allocating per item.
    private final double[] statScratch = new double[3];
    /** Pieces with this little durability left are treated as worthless. */
    private static final int NEARLY_BROKEN = 10;
    /** A candidate must beat the worn piece by this much, so equal pieces never ping-pong. */
    private static final double UPGRADE_MARGIN = 0.5;
    private Registry<Enchantment> cachedRegistry;
    private RegistryEntry<Enchantment> protection;
    private RegistryEntry<Enchantment> blastProtection;
    private RegistryEntry<Enchantment> bindingCurse;
    private static final Map<EquipmentSlot, Integer> SLOT_IDS = new EnumMap<>(EquipmentSlot.class);

    static {
        SLOT_IDS.put(EquipmentSlot.HEAD, PlayerScreenHandler.EQUIPMENT_START);
        SLOT_IDS.put(EquipmentSlot.CHEST, PlayerScreenHandler.EQUIPMENT_START + 1);
        SLOT_IDS.put(EquipmentSlot.LEGS, PlayerScreenHandler.EQUIPMENT_START + 2);
        SLOT_IDS.put(EquipmentSlot.FEET, PlayerScreenHandler.EQUIPMENT_START + 3);
    }

    public AutoArmor() {
        super("AutoArmor", Category.COMBAT, "Equips the best armor from your inventory");
    }

    @Override
    protected void onDisable() {
        cooldown = 0;
    }

    @Override
    public void onTick() {
        if (ElytraMace.isBusy()) {
            return;
        }
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (!(handler instanceof PlayerScreenHandler)) {
            return;
        }
        if (!handler.getCursorStack().isEmpty()) {
            return;
        }
        loadEnchantments();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack worn = mc.player.getEquippedStack(slot);
            boolean wornElytra = slot == EquipmentSlot.CHEST && worn.isOf(Items.ELYTRA);
            if (keepElytra.getValue() && wornElytra) {
                continue;
            }
            // Curse of Binding stops a survival player taking the piece off;
            // the swap would fail and be retried every few ticks forever.
            if (!worn.isEmpty() && !mc.player.isCreative() && bindingCurse != null
                    && EnchantmentHelper.getLevel(bindingCurse, worn) > 0) {
                continue;
            }
            double wornValue = armorValue(worn, slot);
            int bestIndex = -1;
            double bestValue = wornValue + UPGRADE_MARGIN;
            for (int i = PlayerScreenHandler.INVENTORY_START; i < PlayerScreenHandler.HOTBAR_END; i++) {
                ItemStack candidate = handler.getSlot(i).getStack();
                double value = armorValue(candidate, slot);
                if (value > bestValue) {
                    bestValue = value;
                    bestIndex = i;
                }
            }
            if (bestIndex >= 0) {
                mc.interactionManager.clickSlot(handler.syncId, bestIndex, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(handler.syncId, SLOT_IDS.get(slot), 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(handler.syncId, bestIndex, 0, SlotActionType.PICKUP, mc.player);
                cooldown = delay.asInt();
                return;
            }
        }
    }

    private void loadEnchantments() {
        var world = BytePhase.mc.world;
        if (world == null) {
            return;
        }
        Registry<Enchantment> registry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        if (registry != cachedRegistry) {
            cachedRegistry = registry;
            protection = registry.getEntry(Enchantments.PROTECTION.getValue()).orElse(null);
            blastProtection = registry.getEntry(Enchantments.BLAST_PROTECTION.getValue()).orElse(null);
            bindingCurse = registry.getEntry(Enchantments.BINDING_CURSE.getValue()).orElse(null);
        }
    }

    /**
     * Scores a piece for {@code slot}. Armor points dominate, toughness and
     * (netherite) knockback resistance break ties between materials, and
     * Protection/Blast Protection levels separate enchanted from plain gear.
     * Anything that does not fit the slot, or is about to break, scores 0.
     */
    private double armorValue(ItemStack stack, EquipmentSlot slot) {
        if (stack == null || stack.isEmpty()) {
            return 0.0;
        }
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable == null || equippable.slot() != slot) {
            return 0.0;
        }
        if (stack.isDamageable() && stack.getMaxDamage() - stack.getDamage() <= NEARLY_BROKEN) {
            return 0.0;
        }
        statScratch[0] = 0.0;
        statScratch[1] = 0.0;
        statScratch[2] = 0.0;
        stack.applyAttributeModifiers(slot, (RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute, EntityAttributeModifier modifier) -> {
            if (attribute.equals(EntityAttributes.ARMOR)) {
                statScratch[0] += modifier.value();
            } else if (attribute.equals(EntityAttributes.ARMOR_TOUGHNESS)) {
                statScratch[1] += modifier.value();
            } else if (attribute.equals(EntityAttributes.KNOCKBACK_RESISTANCE)) {
                statScratch[2] += modifier.value();
            }
        });
        double score = statScratch[0] * 4.0 + statScratch[1] * 2.0 + statScratch[2] * 10.0;
        if (protection != null) {
            score += EnchantmentHelper.getLevel(protection, stack) * 1.5;
        }
        if (blastProtection != null) {
            score += EnchantmentHelper.getLevel(blastProtection, stack) * 2.0;
        }
        return score;
    }
}
