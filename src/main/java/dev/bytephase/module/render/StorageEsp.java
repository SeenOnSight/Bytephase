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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;

public final class StorageEsp extends Module {
    private static final int MAX_STORAGE_MARKERS = 4096;

    private enum StorageType {
        CHEST,
        SHULKER,
        BARREL,
        HOPPER,
        FURNACE,
        ENDER_CHEST
    }

    private final NumberSetting range = add(new NumberSetting("Range", 48.0, 8.0, 128.0, 1.0));
    private final ModeSetting style = add(new ModeSetting("Style",
            EspRenderer.STYLES, "Highlight"));
    private final BooleanSetting throughWalls = add(new BooleanSetting("ThroughWalls", true));
    private final BooleanSetting tracers = add(new BooleanSetting("Tracers", false));
    private final ColorSetting chestColor = add(new ColorSetting("ChestColor", 0xFF6466E9));
    private final ColorSetting shulkerColor = add(new ColorSetting("ShulkerColor", 0xFF6466E9));
    private final ColorSetting barrelColor = add(new ColorSetting("BarrelColor", 0xFF6466E9));
    private final ColorSetting hopperColor = add(new ColorSetting("HopperColor", 0xFF6466E9));
    private final ColorSetting furnaceColor = add(new ColorSetting("FurnaceColor", 0xFF6466E9));
    private final ColorSetting enderChestColor = add(new ColorSetting("EnderChestColor", 0xFF6466E9));

    private Map<StorageType, List<Box>> boxesByType = new EnumMap<>(StorageType.class);
    private Map<StorageType, List<Vec3d>> centersByType = new EnumMap<>(StorageType.class);
    private Map<StorageType, List<Box>> pendingBoxesByType;
    private Map<StorageType, List<Vec3d>> pendingCentersByType;
    private List<ChunkPos> scanChunks = List.of();
    private int scanCursor;
    private int scanCooldown;
    private int pendingCount;
    private int scanChunkX;
    private int scanChunkZ;
    private int scanRange = -1;
    private ClientWorld lastWorld;

    public StorageEsp() {
        super("StorageESP", Category.RENDER, "Outlined storage blocks");
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
        boxesByType.clear();
        centersByType.clear();
        pendingBoxesByType = null;
        pendingCentersByType = null;
        scanChunks = List.of();
        scanCursor = 0;
        pendingCount = 0;
        scanCooldown = 0;
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
        ChunkPos center = mc.player.getChunkPos();
        int currentRange = range.asInt();
        // Only a teleport (or a range change) invalidates a scan in progress.
        // Restarting at every chunk border meant a long-range scan could never
        // finish while flying, and clearing the markers each time made the
        // ESP vanish. Ordinary movement is picked up by the next periodic scan.
        boolean teleported = Math.abs(center.x - scanChunkX) > 6 || Math.abs(center.z - scanChunkZ) > 6;
        boolean rangeChanged = currentRange != scanRange;
        if (teleported || rangeChanged
                || pendingBoxesByType == null && scanCooldown <= 0) {
            beginScan(center, currentRange);
        }
        if (pendingBoxesByType == null) {
            scanCooldown--;
            return;
        }

        // Process only a few columns each tick. A large render distance may
        // contain hundreds of loaded chunks, and walking all their block
        // entity maps at once caused periodic client-thread FPS drops.
        ClientWorld world = mc.world;
        BlockPos playerPos = mc.player.getBlockPos();
        double rangeSq = range.getValue() * range.getValue();
        int processed = 0;
        while (scanCursor < scanChunks.size() && processed < 2) {
            ChunkPos pos = scanChunks.get(scanCursor++);
            processed++;
            int cx = pos.x;
            int cz = pos.z;
            if (!world.getChunkManager().isChunkLoaded(cx, cz)) continue;
            WorldChunk chunk = world.getChunk(cx, cz);
            if (chunk == null || chunk instanceof EmptyChunk) continue;
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                BlockPos blockPos = blockEntity.getPos();
                if (blockPos.getSquaredDistance(playerPos) > rangeSq) continue;
                StorageType type = typeFor(blockEntity);
                if (type == null) continue;
                Box box = outlineBox(blockEntity);
                pendingBoxesByType.computeIfAbsent(type, key -> new ArrayList<>()).add(box);
                pendingCentersByType.computeIfAbsent(type, key -> new ArrayList<>()).add(box.getCenter());
                if (++pendingCount >= MAX_STORAGE_MARKERS) {
                    // Dense storage hubs can contain thousands of block
                    // entities. Bound both the render batch and the queued
                    // mask size so shader ESP cannot allocate an unbounded
                    // per-frame vertex buffer.
                    scanCursor = scanChunks.size();
                    break;
                }
            }
        }
        if (scanCursor >= scanChunks.size()) {
            boxesByType = pendingBoxesByType;
            centersByType = pendingCentersByType;
            pendingBoxesByType = null;
            pendingCentersByType = null;
            scanCooldown = 40;
        }
    }

    private void beginScan(ChunkPos center, int currentRange) {
        boolean movedOrResized = Math.abs(center.x - scanChunkX) > 6 || Math.abs(center.z - scanChunkZ) > 6
                || currentRange != scanRange;
        scanChunkX = center.x;
        scanChunkZ = center.z;
        scanRange = currentRange;
        if (movedOrResized) {
            // After a teleport or range change the published boxes belong to a
            // different area, so drop them rather than show stale markers.
            boxesByType.clear();
            centersByType.clear();
        }
        int chunkRadius = MathHelper.ceil(range.getValue() / 16.0);
        List<ChunkPos> chunks = new ArrayList<>((chunkRadius * 2 + 1) * (chunkRadius * 2 + 1));
        for (int cx = center.x - chunkRadius; cx <= center.x + chunkRadius; cx++) {
            for (int cz = center.z - chunkRadius; cz <= center.z + chunkRadius; cz++) {
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        scanChunks = chunks;
        scanCursor = 0;
        pendingCount = 0;
        pendingBoxesByType = new EnumMap<>(StorageType.class);
        pendingCentersByType = new EnumMap<>(StorageType.class);
        scanCooldown = 0;
    }

    /**
     * Uses the block's selection outline instead of a full 1x1x1 cube. Chests,
     * for example, are 14/16 of a block, and double-chest halves extend to the
     * seam. Their outline is therefore a much tighter fit than {@code new Box(pos)}
     * while still being the same geometry vanilla highlights when targeted.
     */
    private Box outlineBox(BlockEntity blockEntity) {
        BlockPos pos = blockEntity.getPos();
        VoxelShape shape = blockEntity.getCachedState()
                .getOutlineShape(BytePhase.mc.world, pos, ShapeContext.absent());
        if (shape.isEmpty()) {
            return new Box(pos).expand(0.002);
        }
        return shape.getBoundingBox()
                .offset(pos.getX(), pos.getY(), pos.getZ())
                .expand(0.002);
    }

    private StorageType typeFor(BlockEntity blockEntity) {
        if (blockEntity instanceof EnderChestBlockEntity) {
            return StorageType.ENDER_CHEST;
        }
        if (blockEntity instanceof ChestBlockEntity) {
            return StorageType.CHEST;
        }
        if (blockEntity instanceof ShulkerBoxBlockEntity) {
            return StorageType.SHULKER;
        }
        if (blockEntity instanceof BarrelBlockEntity) {
            return StorageType.BARREL;
        }
        if (blockEntity instanceof HopperBlockEntity) {
            return StorageType.HOPPER;
        }
        if (blockEntity instanceof AbstractFurnaceBlockEntity) {
            return StorageType.FURNACE;
        }
        return null;
    }

    private int colorFor(StorageType type) {
        return switch (type) {
            case CHEST -> chestColor.getValue();
            case SHULKER -> shulkerColor.getValue();
            case BARREL -> barrelColor.getValue();
            case HOPPER -> hopperColor.getValue();
            case FURNACE -> furnaceColor.getValue();
            case ENDER_CHEST -> enderChestColor.getValue();
        };
    }

    @Override
    public void onRender3D(MatrixStack matrices,
                           net.minecraft.client.render.VertexConsumerProvider consumers,
                           float tickDelta) {
        boolean walls = throughWalls.getValue();
        for (Map.Entry<StorageType, List<Box>> entry : boxesByType.entrySet()) {
            int color = colorFor(entry.getKey());
            EspRenderer.drawBoxes(entry.getValue(), color, style.getValue(), walls);
            if (tracers.getValue()) {
                List<Vec3d> centers = centersByType.get(entry.getKey());
                if (centers != null) {
                    RenderBox.drawLinesFrom(RenderBox.tracerOrigin(), centers,
                            EspRenderer.withAlpha(color & 0xFFFFFF, 0x80), walls);
                }
            }
        }
    }
}
