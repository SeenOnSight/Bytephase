package dev.bytephase.pvp.module.impl.combat;

import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.impl.combat.crystal.Crystals;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.impl.combat.melee.SlotReturn;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

@Environment(EnvType.CLIENT)
public final class SwordObsidianModule extends ClientModule {
    private final BooleanSetting switchBack = this.addSetting(
        "switch-back", new BooleanSetting("Switch Back", false));
    private final SlotReturn slots = new SlotReturn(this);

    public SwordObsidianModule() {
        super("sword-obsidian", "Sword Obsidian",
            "Right-click the ground with a sword to select hotbar obsidian and place it.",
            ModuleCategory.COMBAT);
    }

    /** Select before vanilla reads the held stack, so this click places the block. */
    public void beforeUse(Minecraft mc) {
        if (!this.isEnabled() || !MeleeAttacks.canAct(mc) || !mc.isWindowActive()
                || mc.isPaused() || mc.player.isDeadOrDying() || mc.player.isSpectator()
                || !MeleeAttacks.handsFree(mc, mc.player)
                || ServerRotation.pending() || ServerRotation.desynced()
                || !mc.player.getMainHandItem().is(ItemTags.SWORDS)
                || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP) {
            return;
        }
        var cell = hit.getBlockPos().above();
        if (!mc.level.getWorldBorder().isWithinBounds(cell)
                || !mc.level.getBlockState(cell).canBeReplaced()) {
            return;
        }
        int obsidian = Crystals.hotbarSlotOf(mc.player.getInventory(), Items.OBSIDIAN);
        if (obsidian >= 0) this.slots.take(mc.player, obsidian);
    }

    @EventHandler
    public void onTick(TickEvent event) {
        this.slots.tick(Minecraft.getInstance(), this.switchBack.get());
    }

    @Override
    protected void onDisable() {
        this.slots.drop(Minecraft.getInstance(), this.switchBack.get());
    }
}
