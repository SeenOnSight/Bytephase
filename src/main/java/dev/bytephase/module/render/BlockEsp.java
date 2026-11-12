package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.EspRenderer;
import dev.bytephase.render.RenderBox;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Highlights nearby blocks of a chosen kind.
 *
 * <p>Adapted from Anubis' {@code BlockEspModule}. Anubis scans through a
 * dedicated chunk scanner with a colour list; BytePhase uses preset groups and
 * a bounded scan. The scan is sliced across ticks and skips unloaded chunk
 * columns, because the previous version did ~120k block lookups in a single
 * tick once per second, which showed up as a regular stutter.</p>
 */
public final class BlockEsp extends Module {
    private static final Set<Block> ORES = Set.of(
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.NETHER_QUARTZ_ORE, Blocks.ANCIENT_DEBRIS);
    private static final Set<Block> STORAGE = Set.of(
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST,
            Blocks.HOPPER, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
            Blocks.SHULKER_BOX, Blocks.WHITE_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX,
            Blocks.MAGENTA_SHULKER_BOX, Blocks.LIGHT_BLUE_SHULKER_BOX,
            Blocks.YELLOW_SHULKER_BOX, Blocks.LIME_SHULKER_BOX, Blocks.PINK_SHULKER_BOX,
            Blocks.GRAY_SHULKER_BOX, Blocks.LIGHT_GRAY_SHULKER_BOX, Blocks.CYAN_SHULKER_BOX,
            Blocks.PURPLE_SHULKER_BOX, Blocks.BLUE_SHULKER_BOX, Blocks.BROWN_SHULKER_BOX,
            Blocks.GREEN_SHULKER_BOX, Blocks.RED_SHULKER_BOX, Blocks.BLACK_SHULKER_BOX);
    private static final Set<Block> PORTAL = Set.of(
            Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.END_PORTAL_FRAME, Blocks.END_GATEWAY);
    private static final Set<Block> SPAWNER = Set.of(Blocks.SPAWNER);

    private static final int COLUMNS_PER_TICK = 6;
    private static final int MAX_RESULTS = 1024;

    private final ModeSetting mode = add(new ModeSetting("Mode",
            new String[]{"Spawner", "Portal", "Ore", "Storage"}, "Spawner"));
    private final NumberSetting range = add(new NumberSetting("Range", 16.0, 8.0, 64.0, 1.0));
    private final NumberSetting verticalRange = add(new NumberSetting("YRange", 16.0, 8.0, 48.0, 1.0));
    private final BooleanSetting tracers = add(new BooleanSetting("Tracers", false));
    private final ModeSetting style = add(new ModeSetting("Style",
            EspRenderer.STYLES, "Highlight"));
    private final BooleanSetting throughWalls = add(new BooleanSetting("ThroughWalls", true));
    private final ColorSetting spawnerColor = add(new ColorSetting("SpawnerColor", 0xFF6466E9));
    private final ColorSetting portalColor = add(new ColorSetting("PortalColor", 0xFF6466E9));
    private final ColorSetting oreColor = add(new ColorSetting("OreColor", 0xFF6466E9));
    private final ColorSetting storageColor = add(new ColorSetting("StorageColor", 0xFF6466E9));

    private final Set<BlockPos> found = new HashSet<>();
    private final List<Box> boxes = new ArrayList<>();
    private final List<Vec3d> centers = new ArrayList<>();

    private int scanX;
    private int scanZ;
    private int scanRemaining;
    private int spiralLength;
    private int spiralSteps;
    private int spiralLegs;
    private int spiralDirection;
    private int publishCooldown;
    private BlockPos scanCenter;
    private boolean scanning;
    private int restartCooldown;
    private ClientWorld lastWorld;
    private String lastMode;
    private int lastRange;
    private int lastVertical;
    private int publishedResultCount = -1;
    private boolean preservePublishedDuringScan;

    public BlockEsp() {
        super("BlockESP", Category.RENDER, "Highlights spawners, portals, ores and storage");
    }

    @Override
    protected void onEnable() {
        lastWorld = null;
        lastMode = mode.getValue();
        lastRange = range.asInt();
        lastVertical = verticalRange.asInt();
        resetScan();
    }

    @Override
    protected void onDisable() {
        found.clear();
        boxes.clear();
        centers.clear();
        publishedResultCount = -1;
        scanning = false;
        scanCenter = null;
        lastWorld = null;
    }

    private void resetScan() {
        found.clear();
        boxes.clear();
        centers.clear();
        scanX = 0;
        scanZ = 0;
        int diameter = range.asInt() * 2 + 1;
        scanRemaining = diameter * diameter;
        resetSpiral();
        scanCenter = null;
        scanning = true;
        preservePublishedDuringScan = false;
        restartCooldown = 0;
        publishCooldown = 0;
        publishedResultCount = -1;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            resetScan();
            return;
        }
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            resetScan();
            return;
        }
        int horizontal = range.asInt();
        int vertical = verticalRange.asInt();
        String currentMode = mode.getValue();
        if (!currentMode.equals(lastMode) || horizontal != lastRange || vertical != lastVertical) {
            lastMode = currentMode;
            lastRange = horizontal;
            lastVertical = vertical;
            resetScan();
        }
        if (!scanning) {
            if (--restartCooldown > 0) {
                return;
            }
            scanX = 0;
            scanZ = 0;
            int diameter = horizontal * 2 + 1;
            scanRemaining = diameter * diameter;
            resetSpiral();
            scanCenter = null;
            found.clear();
            // Keep the last complete result visible while the next scan runs.
            // Publishing partial replacements makes distant markers flicker.
            preservePublishedDuringScan = true;
            publishedResultCount = -1;
            scanning = true;
        }

        BlockPos center = mc.player.getBlockPos();
        if (scanCenter == null) {
            scanCenter = center;
        } else {
            long recenterDistance = Math.max(16, horizontal - 8);
            if (scanCenter.getSquaredDistance(center) > recenterDistance * recenterDistance) {
                resetScan();
                scanCenter = center;
            }
        }
        Set<Block> wanted = wanted();
        int columns = 0;
        while (scanning && columns < COLUMNS_PER_TICK && scanRemaining > 0) {
            if (Math.abs(scanX) <= horizontal && Math.abs(scanZ) <= horizontal) {
                int worldX = scanCenter.getX() + scanX;
                int worldZ = scanCenter.getZ() + scanZ;
                // Do not ask ClientWorld for a chunk that is not loaded. The
                // scanner only reports blocks the client already knows about,
                // and probing unloaded columns can create empty chunk
                // placeholders or waste work at the edge of view distance.
                if (mc.world.getChunkManager().isChunkLoaded(worldX >> 4, worldZ >> 4)) {
                    scanColumn(mc.world, worldX, worldZ, scanCenter.getY(), wanted, vertical);
                }
            }
            columns++;
            scanRemaining--;
            advanceSpiral();
            if (!scanning) {
                return;
            }
        }
        if (scanRemaining <= 0) {
            finishScan();
            return;
        }
        if (!preservePublishedDuringScan && --publishCooldown <= 0) {
            publishCooldown = 10;
            publishResults();
        }
    }

    private void resetSpiral() {
        spiralLength = 1;
        spiralSteps = 0;
        spiralLegs = 0;
        spiralDirection = 0;
    }

    /** Advances a square spiral so the closest columns are scanned first. */
    private void advanceSpiral() {
        switch (spiralDirection) {
            case 0 -> scanX++;
            case 1 -> scanZ++;
            case 2 -> scanX--;
            default -> scanZ--;
        }
        if (++spiralSteps >= spiralLength) {
            spiralSteps = 0;
            spiralDirection = (spiralDirection + 1) & 3;
            if (++spiralLegs == 2) {
                spiralLegs = 0;
                spiralLength++;
            }
        }
    }

    private void scanColumn(net.minecraft.client.world.ClientWorld world, int worldX, int worldZ,
                            int centerY, Set<Block> wanted, int vertical) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = centerY - vertical; y <= centerY + vertical; y++) {
            pos.set(worldX, y, worldZ);
            BlockState state = world.getBlockState(pos);
            if (state.isAir() || !wanted.contains(state.getBlock())) {
                continue;
            }
            found.add(pos.toImmutable());
            if (found.size() >= MAX_RESULTS) {
                finishScan();
                return;
            }
        }
    }

    private void finishScan() {
        scanning = false;
        restartCooldown = 20;
        // A finished scan always republishes: the set can change while its
        // size stays the same (one block broken, another placed).
        publishedResultCount = -1;
        publishResults();
    }

    private void publishResults() {
        if (publishedResultCount == found.size()) {
            return;
        }
        boxes.clear();
        centers.clear();
        for (BlockPos pos : found) {
            boxes.add(new Box(pos).expand(0.002));
            centers.add(pos.toCenterPos());
        }
        publishedResultCount = found.size();
    }

    @Override
    public void onRender3D(MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider consumers,
                           float tickDelta) {
        if (boxes.isEmpty()) {
            return;
        }
        int color = switch (mode.getValue()) {
            case "Portal" -> portalColor.getValue();
            case "Ore" -> oreColor.getValue();
            case "Storage" -> storageColor.getValue();
            default -> spawnerColor.getValue();
        };
        EspRenderer.drawBoxes(boxes, color, style.getValue(), throughWalls.getValue());
        if (tracers.getValue()) {
            RenderBox.drawLinesFrom(RenderBox.tracerOrigin(), centers,
                    EspRenderer.withAlpha(color & 0xFFFFFF, 0x80), throughWalls.getValue());
        }
    }

    private Set<Block> wanted() {
        return switch (mode.getValue()) {
            case "Portal" -> PORTAL;
            case "Ore" -> ORES;
            case "Storage" -> STORAGE;
            default -> SPAWNER;
        };
    }
}
