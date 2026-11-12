package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.EspRenderer;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class HoleEsp extends Module {
    private static final int COLUMNS_PER_TICK = 128;

    private final NumberSetting radius = add(new NumberSetting("Radius", 8, 4, 16, 1));
    private final ModeSetting style = add(new ModeSetting("Style",
            EspRenderer.STYLES, "Filled"));
    private final BooleanSetting throughWalls = add(new BooleanSetting("ThroughWalls", true));
    private final ColorSetting safeColor = add(new ColorSetting("SafeColor", 0xFF6466E9));
    private final ColorSetting unsafeColor = add(new ColorSetting("UnsafeColor", 0xFF6466E9));
    private final List<Box> safeHoles = new ArrayList<>();
    private final List<Box> unsafeHoles = new ArrayList<>();
    private final List<Box> pendingSafeHoles = new ArrayList<>();
    private final List<Box> pendingUnsafeHoles = new ArrayList<>();
    private int scanCooldown;
    private int scanCursor = -1;
    private int scanRadius = -1;
    private BlockPos scanCenter;
    private net.minecraft.client.world.ClientWorld lastWorld;

    public HoleEsp() {
        super("HoleESP", Category.RENDER, "Marks survivable crystal holes");
    }

    @Override
    protected void onEnable() {
        clear();
    }

    @Override
    protected void onDisable() {
        clear();
    }

    private void clear() {
        safeHoles.clear();
        unsafeHoles.clear();
        pendingSafeHoles.clear();
        pendingUnsafeHoles.clear();
        scanCooldown = 0;
        scanCursor = -1;
        scanRadius = -1;
        scanCenter = null;
        lastWorld = null;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            clear();
            return;
        }
        if (mc.world != lastWorld) {
            clear();
            lastWorld = mc.world;
        }
        int r = radius.asInt();
        BlockPos playerPos = mc.player.getBlockPos();
        if (scanCursor >= 0 && (scanRadius != r
                || scanCenter == null
                || scanCenter.getSquaredDistance(playerPos) > (long) r * r)) {
            // A range edit or teleport invalidates partial results. Restart
            // around the new position instead of finishing a stale scan.
            safeHoles.clear();
            unsafeHoles.clear();
            scanCursor = -1;
        }
        if (scanCursor < 0) {
            if (scanCooldown > 0) {
                scanCooldown--;
                return;
            }
            beginScan(playerPos, r);
        }

        int diameter = scanRadius * 2 + 1;
        int totalColumns = diameter * diameter;
        int processed = 0;
        while (scanCursor < totalColumns && processed < COLUMNS_PER_TICK) {
            int dx = scanCursor / diameter - scanRadius;
            int dz = scanCursor % diameter - scanRadius;
            scanCursor++;
            processed++;
            for (int dy = 0; dy >= -1; dy--) {
                BlockPos pos = scanCenter.add(dx, dy, dz);
                if (isHole(pos)) {
                    (isSafe(pos) ? pendingSafeHoles : pendingUnsafeHoles)
                            .add(new Box(pos).expand(0.002));
                }
            }
        }
        if (scanCursor >= totalColumns) {
            safeHoles.clear();
            safeHoles.addAll(pendingSafeHoles);
            unsafeHoles.clear();
            unsafeHoles.addAll(pendingUnsafeHoles);
            pendingSafeHoles.clear();
            pendingUnsafeHoles.clear();
            scanCursor = -1;
            scanCooldown = 10;
        }
    }

    private void beginScan(BlockPos center, int scanRadius) {
        this.scanCenter = center.toImmutable();
        this.scanRadius = scanRadius;
        this.scanCursor = 0;
        pendingSafeHoles.clear();
        pendingUnsafeHoles.clear();
    }

    @Override
    public void onRender3D(MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider consumers, float tickDelta) {
        boolean walls = throughWalls.getValue();
        EspRenderer.drawBoxes(safeHoles, safeColor.getValue(), style.getValue(), walls);
        EspRenderer.drawBoxes(unsafeHoles, unsafeColor.getValue(), style.getValue(), walls);
    }

    private boolean isHole(BlockPos pos) {
        var mc = BytePhase.mc;
        if (!mc.world.getBlockState(pos).isAir() || !mc.world.getBlockState(pos.up()).isAir()) {
            return false;
        }
        if (!isSolid(pos.down())) {
            return false;
        }
        return isSolid(pos.north()) && isSolid(pos.south()) && isSolid(pos.east()) && isSolid(pos.west())
                && isSolid(pos.north().up()) && isSolid(pos.south().up())
                && isSolid(pos.east().up()) && isSolid(pos.west().up());
    }

    private boolean isSolid(BlockPos pos) {
        var mc = BytePhase.mc;
        return !mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty();
    }

    /** Safe means the floor and all four foot-level walls resist crystal blasts. */
    private boolean isSafe(BlockPos pos) {
        return isBlastProof(pos.down()) && isBlastProof(pos.north()) && isBlastProof(pos.south())
                && isBlastProof(pos.east()) && isBlastProof(pos.west());
    }

    private boolean isBlastProof(BlockPos pos) {
        var block = BytePhase.mc.world.getBlockState(pos).getBlock();
        return block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN || block == Blocks.BEDROCK;
    }
}
