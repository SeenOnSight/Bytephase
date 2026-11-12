package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.BlockPlacementUtil;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Places a supported obsidian crystal base at the best reachable target position. */
public final class AutoObsidian extends Module {
    private final NumberSetting targetRange = add(new NumberSetting("TargetRange", 6.0, 2.0, 12.0, 0.5));
    private final NumberSetting placeRange = add(new NumberSetting("PlaceRange", 4.5, 2.0, 6.0, 0.1));
    private final NumberSetting delay = add(new NumberSetting("Delay", 1.0, 0.0, 10.0, 1.0));
    private final BooleanSetting swapBack = add(new BooleanSetting("SwapBack", true));

    private int cooldown;

    public AutoObsidian() {
        super("AutoObsidian", Category.COMBAT, "Places the best reachable obsidian base for crystal PvP");
    }

    @Override
    protected void onEnable() {
        cooldown = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || mc.player.isSpectator()) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        int slot = BlockPlacementUtil.findHotbarBlock(mc, Blocks.OBSIDIAN);
        if (slot < 0) return;

        double targetRangeSq = targetRange.getValue() * targetRange.getValue();
        PlayerEntity target = null;
        double nearestDistanceSq = Double.POSITIVE_INFINITY;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) continue;
            double distanceSq = player.squaredDistanceTo(mc.player);
            if (distanceSq <= targetRangeSq && distanceSq < nearestDistanceSq) {
                target = player;
                nearestDistanceSq = distanceSq;
            }
        }
        if (target == null) return;

        BlockPos feet = target.getBlockPos();
        int r = (int) Math.ceil(placeRange.getValue());
        Box candidateArea = new Box(feet.getX() - r, feet.getY() - 1, feet.getZ() - r,
                feet.getX() + r + 1.0, feet.getY() + 3.0, feet.getZ() + r + 1.0);
        var nearbyEntities = mc.world.getOtherEntities(mc.player, candidateArea);
        Vec3d self = mc.player.getEntityPos();
        BlockPos best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        double placeRangeSq = placeRange.getValue() * placeRange.getValue();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 0; dy++) {
                    BlockPos pos = feet.add(dx, dy, dz);
                    if (!mc.world.getBlockState(pos).isReplaceable()
                            || mc.world.getBlockState(pos.down()).getCollisionShape(mc.world, pos.down()).isEmpty()
                            || !mc.world.isAir(pos.up()) || !mc.world.isAir(pos.up(2))) continue;
                    if (mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()) > placeRangeSq) continue;
                    Box placementBox = new Box(pos);
                    // getOtherEntities excludes the querying local player.
                    // Keep the candidate clear of our own collision box too,
                    // otherwise close targets can make the module select a
                    // base inside the player and the server rejects the place.
                    if (mc.player.getBoundingBox().intersects(placementBox)) continue;
                    boolean occupied = false;
                    for (var entity : nearbyEntities) {
                        if (entity.getBoundingBox().intersects(placementBox)) {
                            occupied = true;
                            break;
                        }
                    }
                    if (occupied) continue;
                    Vec3d base = pos.toCenterPos().add(0, 0.5, 0);
                    double score = base.squaredDistanceTo(target.getEntityPos())
                            - base.squaredDistanceTo(self) * 0.2;
                    if (score < bestScore) {
                        best = pos.toImmutable();
                        bestScore = score;
                    }
                }
            }
        }
        if (best != null && BlockPlacementUtil.place(mc, best, slot, swapBack.getValue(), true)) {
            cooldown = delay.asInt();
        }
    }
}
