package dev.bytephase.module.player;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public final class AutoTool extends Module {
    private int originalSlot = -1;
    private int selectedByModule = -1;
    private ClientPlayerEntity capturedPlayer;
    private Registry<Enchantment> cachedRegistry;
    private RegistryEntry<Enchantment> efficiency;

    public AutoTool() {
        super("AutoTool", Category.PLAYER, "Switches to the best tool automatically");
    }

    @Override
    protected void onDisable() {
        restoreSlot();
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.currentScreen != null) {
            restoreSlot();
            return;
        }
        if (capturedPlayer != null && capturedPlayer != mc.player) {
            // Respawns/reconnects create a new local player object. Restore
            // the previous inventory only on the player whose slot we changed.
            restoreSlot();
        }
        if (!mc.options.attackKey.isPressed() || mc.player.isUsingItem()) {
            restoreSlot();
            return;
        }
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) {
            restoreSlot();
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) {
            restoreSlot();
            return;
        }
        int current = mc.player.getInventory().getSelectedSlot();
        if (selectedByModule >= 0 && current != selectedByModule) {
            // The player chose another slot manually; do not later overwrite
            // that choice when mining stops.
            originalSlot = -1;
            selectedByModule = -1;
            capturedPlayer = null;
        }
        ItemStack currentStack = mc.player.getInventory().getStack(current);
        loadEnchantments(mc.world);
        float currentSpeed = miningSpeed(currentStack, state);
        boolean currentSuitable = currentStack.isSuitableFor(state);
        int best = current;
        float bestSpeed = currentSpeed;
        boolean bestSuitable = currentSuitable;
        for (int i = 0; i < 9; i++) {
            ItemStack candidate = mc.player.getInventory().getStack(i);
            float speed = miningSpeed(candidate, state);
            boolean suitable = candidate.isSuitableFor(state);
            if (speed > bestSpeed || speed == bestSpeed && suitable && !bestSuitable) {
                bestSpeed = speed;
                bestSuitable = suitable;
                best = i;
            }
        }
        if (best != current) {
            if (originalSlot < 0) {
                originalSlot = current;
                capturedPlayer = mc.player;
            }
            mc.player.getInventory().setSelectedSlot(best);
            selectedByModule = best;
        } else if (selectedByModule >= 0) {
            selectedByModule = current;
        }
    }

    private void loadEnchantments(net.minecraft.world.World world) {
        Registry<Enchantment> registry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        if (registry != cachedRegistry) {
            cachedRegistry = registry;
            efficiency = registry.getEntry(Enchantments.EFFICIENCY.getValue()).orElse(null);
        }
    }

    /**
     * The tool's base speed for this block, plus the Efficiency bonus vanilla
     * adds (level squared plus one) when the tool is faster than bare hands.
     * Without it a plain netherite pick always beats an Efficiency V diamond one.
     */
    private float miningSpeed(ItemStack stack, BlockState state) {
        float speed = stack.getMiningSpeedMultiplier(state);
        if (speed > 1.0f && efficiency != null) {
            int level = EnchantmentHelper.getLevel(efficiency, stack);
            if (level > 0) {
                speed += level * level + 1;
            }
        }
        return speed;
    }

    private void restoreSlot() {
        if (capturedPlayer != null && originalSlot >= 0 && selectedByModule >= 0
                && capturedPlayer.getInventory().getSelectedSlot() == selectedByModule) {
            capturedPlayer.getInventory().setSelectedSlot(originalSlot);
        }
        originalSlot = -1;
        selectedByModule = -1;
        capturedPlayer = null;
    }

}
