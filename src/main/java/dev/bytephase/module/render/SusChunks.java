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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Highlights chunks that contain geode amethyst, which Anubis uses as a sign
 * that a base area has been generated and settled.
 *
 * <p>Adapted from Anubis' {@code SusChunkFinderModule}. That module keeps a full
 * packet/light/geode pipeline with several caches; this port keeps the core
 * signal (budding amethyst in a loaded chunk) and a strict per-tick time budget
 * so it cannot eat frames. Chunks are scanned a few at a time and drawn from a
 * cached list.</p>
 */
public final class SusChunks extends Module {
    private static final long SCAN_BUDGET_NANOS = 1_500_000L;
    private static final int RESCAN_TICKS = 400;
    private static final java.util.function.Predicate<BlockState> IS_BUDDING =
            state -> state.isOf(Blocks.BUDDING_AMETHYST);
    private static final int MAX_CACHED_SUSPECTS = 512;
    private static final long CHUNK_TIMEOUT_MS = 120_000L;

    private static final class Suspect {
        final int minY;
        final int maxY;
        final long since;

        Suspect(int minY, int maxY, long since) {
            this.minY = minY;
            this.maxY = maxY;
            this.since = since;
        }
    }

    private final NumberSetting range = add(new NumberSetting("Range", 4.0, 1.0, 8.0, 1.0));
    private final NumberSetting minBudding = add(new NumberSetting("MinBudding", 6.0, 1.0, 64.0, 1.0));
    private final BooleanSetting tracers = add(new BooleanSetting("Tracers", false));
    private final ModeSetting style = add(new ModeSetting("Style",
            EspRenderer.STYLES, "Highlight"));
    private final BooleanSetting throughWalls = add(new BooleanSetting("ThroughWalls", true));
    private final ColorSetting color = add(new ColorSetting("Color", 0xFF6466E9));

    private static final class Scan {
        int nextSection;
        int nextBlock;
        int budding;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
    }

    private final Map<Long, Suspect> suspects = new HashMap<>();
    private final Map<Long, Scan> scans = new HashMap<>();
    private final Deque<Long> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private final List<Box> boxes = new ArrayList<>();
    private final List<Vec3d> centers = new ArrayList<>();

    private ChunkPos scanOrigin;
    private int scanRange = -1;
    private int rescanTicks;
    private long lastRefresh;
    private ClientWorld lastWorld;

    public SusChunks() {
        super("SusChunks", Category.RENDER, "Highlights chunks with geode amethyst");
    }

    @Override
    protected void onEnable() {
        lastWorld = null;
        reset();
        enqueueAroundPlayer();
    }

    @Override
    protected void onDisable() {
        reset();
        lastWorld = null;
    }

    private void reset() {
        suspects.clear();
        scans.clear();
        queue.clear();
        queued.clear();
        boxes.clear();
        centers.clear();
        scanOrigin = null;
        scanRange = -1;
        rescanTicks = 0;
        lastRefresh = 0L;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            reset();
            return;
        }
        if (mc.world != lastWorld) {
            reset();
            lastWorld = mc.world;
        }
        ChunkPos playerChunk = mc.player.getChunkPos();
        int currentRange = range.asInt();
        if (scanOrigin == null) {
            enqueueAroundPlayer();
        } else if (scanOrigin.x != playerChunk.x || scanOrigin.z != playerChunk.z
                || scanRange != currentRange) {
            // Discard partial work from the previous location. Retaining it
            // after a fast move/teleport turns this local scanner into a
            // growing backlog of chunks that are no longer nearby.
            queue.clear();
            queued.clear();
            scans.clear();
            pruneSuspects(playerChunk, currentRange);
            enqueueAroundPlayer();
        } else if (--rescanTicks <= 0) {
            enqueueAroundPlayer();
        }

        // One section per budget slice. Checking the deadline only between
        // whole chunks meant a single 98k-block chunk scan could stall a frame.
        long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;
        while (!queue.isEmpty() && System.nanoTime() < deadline) {
            long key = queue.peek();
            if (!scanSlice(key, deadline)) {
                queue.poll();
                queued.remove(key);
                scans.remove(key);
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastRefresh > CHUNK_TIMEOUT_MS) {
            rebuildRenderCache(now);
        }
    }

    /** @return true while the chunk still has sections left to scan. */
    private boolean scanSlice(long key, long deadline) {
        var mc = BytePhase.mc;
        if (mc.world == null) {
            return false;
        }
        int chunkX = ChunkPos.getPackedX(key);
        int chunkZ = ChunkPos.getPackedZ(key);
        if (!mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
            return false;
        }
        WorldChunk chunk = mc.world.getChunk(chunkX, chunkZ);
        if (chunk instanceof EmptyChunk) {
            return false;
        }

        Scan scan = scans.computeIfAbsent(key, ignored -> new Scan());
        ChunkSection[] sections = chunk.getSectionArray();
        if (scan.budding >= minBudding.asInt()) {
            finishChunk(key, scan);
            return false;
        }
        while (scan.nextSection < sections.length) {
            ChunkSection section = sections[scan.nextSection];
            int sectionIndex = scan.nextSection;
            // hasAny consults the section palette, so sections that contain no
            // budding amethyst at all are skipped without reading 4,096 blocks.
            if (section == null || section.isEmpty() || !section.hasAny(IS_BUDDING)) {
                scan.nextSection++;
                scan.nextBlock = 0;
                continue;
            }
            if (scanSection(chunk, section, sectionIndex, scan, deadline)) {
                scan.nextSection++;
                scan.nextBlock = 0;
            }
            return true;
        }

        finishChunk(key, scan);
        return false;
    }

    private boolean scanSection(WorldChunk chunk, ChunkSection section, int sectionIndex,
                                Scan scan, long deadline) {
        int needed = minBudding.asInt();
        int sectionY = chunk.sectionIndexToCoord(sectionIndex) * 16;
        while (scan.nextBlock < 4096 && scan.budding < needed) {
            int index = scan.nextBlock++;
            int y = index / 256;
            int x = index / 16 % 16;
            int z = index % 16;
            BlockState state = section.getBlockState(x, y, z);
            if (state.isOf(Blocks.BUDDING_AMETHYST)) {
                scan.budding++;
                int worldY = sectionY + y;
                scan.minY = Math.min(scan.minY, worldY);
                scan.maxY = Math.max(scan.maxY, worldY);
            }
            // Resume partway through a dense section on the next tick rather
            // than letting one 4,096-block scan exceed the frame-time budget.
            if ((scan.nextBlock & 63) == 0 && System.nanoTime() >= deadline) {
                return false;
            }
        }
        return scan.nextBlock >= 4096 || scan.budding >= needed;
    }

    private void finishChunk(long key, Scan scan) {
        if (scan.budding >= minBudding.asInt()) {
            Suspect existing = suspects.get(key);
            boolean changed = existing == null
                    || existing.minY != scan.minY
                    || existing.maxY != scan.maxY;
            if (existing == null && suspects.size() >= MAX_CACHED_SUSPECTS) {
                evictOldestSuspect();
            }
            suspects.put(key, new Suspect(scan.minY, scan.maxY,
                    // Expiry and eviction should follow the most recent
                    // successful verification, not the first time this
                    // chunk was found. Otherwise a stable in-range chunk
                    // disappears after two minutes even though it is
                    // continuously re-scanned.
                    System.currentTimeMillis()));
            if (changed) {
                lastRefresh = 0L;
            }
        } else if (suspects.remove(key) != null) {
            // Drop the cached box immediately; otherwise a chunk that no
            // longer qualifies would stay on screen until the next timeout.
            lastRefresh = 0L;
        }
    }

    private void evictOldestSuspect() {
        long oldestKey = 0L;
        long oldestSince = Long.MAX_VALUE;
        for (Map.Entry<Long, Suspect> entry : suspects.entrySet()) {
            if (entry.getValue().since < oldestSince) {
                oldestSince = entry.getValue().since;
                oldestKey = entry.getKey();
            }
        }
        if (oldestSince != Long.MAX_VALUE) {
            suspects.remove(oldestKey);
            lastRefresh = 0L;
        }
    }

    private void enqueueAroundPlayer() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) {
            return;
        }
        rescanTicks = RESCAN_TICKS;
        scanOrigin = mc.player.getChunkPos();
        int radius = range.asInt();
        scanRange = radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                long key = ChunkPos.toLong(scanOrigin.x + dx, scanOrigin.z + dz);
                if (queued.add(key)) {
                    queue.add(key);
                }
            }
        }
    }

    private void pruneSuspects(ChunkPos center, int radius) {
        suspects.keySet().removeIf(key ->
                Math.abs(ChunkPos.getPackedX(key) - center.x) > radius
                        || Math.abs(ChunkPos.getPackedZ(key) - center.z) > radius);
        lastRefresh = 0L;
        rebuildRenderCache(System.currentTimeMillis());
    }

    private void rebuildRenderCache(long now) {
        boxes.clear();
        centers.clear();
        suspects.entrySet().removeIf(entry -> now - entry.getValue().since > CHUNK_TIMEOUT_MS);
        for (Map.Entry<Long, Suspect> entry : suspects.entrySet()) {
            long key = entry.getKey();
            int chunkX = ChunkPos.getPackedX(key);
            int chunkZ = ChunkPos.getPackedZ(key);
            Suspect suspect = entry.getValue();
            double minY = suspect.minY - 1.0;
            double maxY = suspect.maxY + 2.0;
            Box box = new Box(chunkX * 16.0, minY, chunkZ * 16.0,
                    chunkX * 16.0 + 16.0, maxY, chunkZ * 16.0 + 16.0);
            boxes.add(box);
            centers.add(box.getCenter());
        }
        lastRefresh = now;
    }

    @Override
    public void onRender3D(MatrixStack matrices,
                           net.minecraft.client.render.VertexConsumerProvider consumers,
                           float tickDelta) {
        if (boxes.isEmpty()) {
            return;
        }
        EspRenderer.drawBoxes(boxes, color.getValue(), style.getValue(), throughWalls.getValue());
        if (tracers.getValue()) {
            RenderBox.drawLinesFrom(RenderBox.tracerOrigin(), centers,
                    EspRenderer.withAlpha(color.rgb(), 0x80), throughWalls.getValue());
        }
    }
}
