package dev.bytephase.pvp.marlow.crystal;

import dev.bytephase.pvp.marlow.crystal.KeptCrystals;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

public final class PastKeptCrystal {
    private final KeptCrystals keptCrystals;
    private HitResult crosshair;
    private EntityHitResult nextCrystal;
    private BlockHitResult crystalBase;

    public PastKeptCrystal(KeptCrystals keptCrystals) {
        this.keptCrystals = keptCrystals;
    }

    public void clear(Minecraft client) {
        if (nextCrystal != null && client.hitResult == nextCrystal
                || crystalBase != null && client.hitResult == crystalBase) client.hitResult = crosshair;
        crosshair = null;
        nextCrystal = null;
        crystalBase = null;
    }

    private static boolean isCrystal(ItemStack stack) {
        return stack.is(Items.END_CRYSTAL);
    }

    public void attackStarted(Minecraft client) {
        EntityHitResult target;
        HitResult behind = this.behindKeptCrystal(client);
        if (behind instanceof EntityHitResult && (target = (EntityHitResult)behind).getEntity() instanceof EndCrystal) {
            this.crosshair = client.hitResult;
            this.nextCrystal = target;
            client.hitResult = target;
        }
    }

    public void attackEnded(Minecraft client) {
        if (this.nextCrystal != null) {
            client.hitResult = this.crosshair;
        }
        this.crosshair = null;
        this.nextCrystal = null;
    }

    public void useStarted(Minecraft client) {
        BlockHitResult block;
        LocalPlayer player = client.player;
        if (player == null || !PastKeptCrystal.isCrystal(player.getMainHandItem()) && !PastKeptCrystal.isCrystal(player.getOffhandItem())) {
            return;
        }
        HitResult behind = this.behindKeptCrystal(client);
        if (!(behind instanceof BlockHitResult) || (block = (BlockHitResult)behind).getType() != HitResult.Type.BLOCK) {
            return;
        }
        ClientLevel level = client.level;
        BlockPos pos = block.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK) || !level.isEmptyBlock(pos.above())) {
            return;
        }
        this.crosshair = client.hitResult;
        this.crystalBase = block;
    }

    public void handTried(Minecraft client, ItemStack stack) {
        if (this.crystalBase != null) {
            client.hitResult = PastKeptCrystal.isCrystal(stack) ? this.crystalBase : this.crosshair;
        }
    }

    public void useEnded(Minecraft client) {
        if (this.crystalBase != null) {
            client.hitResult = this.crosshair;
        }
        this.crosshair = null;
        this.crystalBase = null;
    }

    private HitResult behindKeptCrystal(Minecraft client) {
        EntityHitResult target;
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null || player.isSpectator()) {
            return null;
        }
        HitResult underCrosshair = client.hitResult;
        if (!(underCrosshair instanceof EntityHitResult) || !this.keptCrystals.isHidden((target = (EntityHitResult)underCrosshair).getEntity())) {
            return null;
        }
        return this.keptCrystals.seePast(() -> PastKeptCrystal.pick(client, player));
    }

    private static HitResult pick(Minecraft client, LocalPlayer player) {
        HitResult hitResult = client.hitResult;
        Entity pickEntity = client.crosshairPickEntity;
        client.gameRenderer.pick(1.0f);
        HitResult behind = client.hitResult;
        client.hitResult = hitResult;
        client.crosshairPickEntity = pickEntity;
        return behind;
    }
}

