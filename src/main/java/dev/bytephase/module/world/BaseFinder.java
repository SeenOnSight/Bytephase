package dev.bytephase.module.world;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.EspRenderer;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.NumberSetting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;

/** Searches loaded chunks for storage clusters and utility blocks using a resumable scan. */
public final class BaseFinder extends Module {
    private static final long BUDGET_NANOS = 800_000L;
    private static final int MAX_CHUNKS = 289;
    private static final int MAX_ALERTS = 128;
    private static final int MAX_ALERT_HISTORY = 1024;
    private static final Predicate<BlockState> INTERESTING =
            state -> storage(state) || utility(state) || state.isOf(Blocks.SPAWNER);

    private final NumberSetting range = add(new NumberSetting("ChunkRange", 4, 1, 8, 1));
    private final NumberSetting storageThreshold = add(new NumberSetting("MinStorage", 6, 2, 64, 1));
    private final NumberSetting utilityThreshold = add(new NumberSetting("MinUtilities", 4, 1, 32, 1));
    private final BooleanSetting spawners = add(new BooleanSetting("FindSpawners", true));
    private final BooleanSetting announce = add(new BooleanSetting("ChatAnnounce", true));
    private final BooleanSetting markers = add(new BooleanSetting("ChunkMarkers", true));
    private final ColorSetting color = add(new ColorSetting("Color", 0xFF6466E9));
    private final ArrayDeque<Long> pending = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private final Set<Long> deferred = new HashSet<>();
    private final Map<Long, Marker> marked = new HashMap<>();
    private final List<Box> renderBoxes = new ArrayList<>();
    private final ArrayDeque<Alert> alerts = new ArrayDeque<>();
    private final Map<String, Boolean> alertHistory = new LinkedHashMap<>();
    private final Object changeLock = new Object();
    private final Map<Long, Long> revisions = new HashMap<>();
    private final Set<Long> changed = new HashSet<>();
    // Only these coordinates and revision notices are shared with packet threads.
    private ChunkPos trackedOrigin;
    private int trackedRange;
    private volatile long nextRevision;
    private long renderRevision;
    private ClientWorld world;
    private ChunkPos origin;
    private int previousRange;
    private int rescanTicks;
    private int announceCooldown;
    private int previousStorage;
    private int previousUtilities;
    private boolean previousSpawners;
    private Scan scan;

    private record Marker(WorldChunk chunk, long revision, Box box) {}
    private record Alert(long key, WorldChunk chunk, long revision, String message, BlockPos pos) {}

    private static final class Scan {
        final long key;
        final WorldChunk chunk;
        final ChunkSection[] sections;
        final long revision;
        int section;
        int block;
        boolean paletteChecked;
        int storage;
        int utilities;
        BlockPos storagePos;
        BlockPos utilityPos;
        final List<BlockPos> spawners = new ArrayList<>();
        int publishedSpawners;
        boolean complete;

        Scan(long key, WorldChunk chunk, long revision) {
            this.key = key;
            this.chunk = chunk;
            this.sections = chunk.getSectionArray().clone();
            this.revision = revision;
        }
    }

    public BaseFinder() {
        super("BaseFinder", Category.EXPLORE, "Finds storage clusters, utility blocks and spawners in loaded chunks");
    }

    private static boolean storage(BlockState state) {
        return state.getBlock() instanceof ShulkerBoxBlock || state.isOf(Blocks.CHEST)
                || state.isOf(Blocks.TRAPPED_CHEST) || state.isOf(Blocks.BARREL)
                || state.isOf(Blocks.HOPPER);
    }

    private static boolean utility(BlockState state) {
        return state.isOf(Blocks.BEACON) || state.isOf(Blocks.ENCHANTING_TABLE)
                || state.isOf(Blocks.ENDER_CHEST) || state.isOf(Blocks.RESPAWN_ANCHOR)
                || state.isOf(Blocks.ANVIL) || state.isOf(Blocks.CHIPPED_ANVIL)
                || state.isOf(Blocks.DAMAGED_ANVIL) || state.isOf(Blocks.REDSTONE_LAMP);
    }

    @Override
    protected void onDisable() {
        pending.clear();
        queued.clear();
        deferred.clear();
        marked.clear();
        renderBoxes.clear();
        alerts.clear();
        alertHistory.clear();
        synchronized (changeLock) {
            trackedOrigin = null;
            changed.clear();
            revisions.clear();
        }
        scan = null;
        world = null;
        origin = null;
        rescanTicks = 0;
        announceCooldown = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            onDisable();
            return;
        }
        ChunkPos current = mc.player.getChunkPos();
        int radius = range.asInt();
        boolean newWorld = world != mc.world;
        boolean newSettings = previousStorage != storageThreshold.asInt()
                || previousUtilities != utilityThreshold.asInt()
                || previousSpawners != spawners.getValue();
        if (newWorld || newSettings || origin == null || current.x != origin.x || current.z != origin.z
                || previousRange != radius) {
            if (newWorld) onDisable();
            if (newSettings) {
                marked.clear();
                alerts.clear();
            }
            world = mc.world;
            origin = current;
            previousRange = radius;
            previousStorage = storageThreshold.asInt();
            previousUtilities = utilityThreshold.asInt();
            previousSpawners = spawners.getValue();
            pending.clear();
            queued.clear();
            deferred.clear();
            scan = null;
            synchronized (changeLock) {
                trackedOrigin = current;
                trackedRange = radius;
                revisions.keySet().removeIf(key -> !inRange(key));
                changed.removeIf(key -> !inRange(key));
            }
            enqueue(radius);
            refreshMarkers();
        } else if (--rescanTicks <= 0 && pending.isEmpty() && scan == null) {
            enqueue(radius);
        }
        for (long key : deferred) enqueue(key);
        deferred.clear();
        synchronized (changeLock) {
            deferred.addAll(changed);
            changed.clear();
        }
        for (long key : deferred) {
            marked.remove(key);
            if (scan != null && scan.key == key) scan = null;
        }
        if (announceCooldown > 0) announceCooldown--;
        long deadline = System.nanoTime() + BUDGET_NANOS;
        while (System.nanoTime() < deadline) {
            if (scan == null) {
                Long key = pending.poll();
                if (key == null) break;
                queued.remove(key);
                if (deferred.contains(key)) continue;
                WorldChunk chunk = loadedChunk(key);
                if (chunk == null) continue;
                scan = new Scan(key, chunk, revision(key));
            }
            if (!valid(scan)) {
                marked.remove(scan.key);
                deferred.add(scan.key);
                scan = null;
                continue;
            }
            if (!scan.complete) scan.complete = slice(deadline);
            if (scan.complete && System.nanoTime() < deadline) {
                if (publish(scan, deadline)) scan = null;
            }
        }
        refreshMarkers();
        drainAlert();
    }

    private void enqueue(int radius) {
        // Finish nearby chunks first, including while travelling between chunks.
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                        enqueue(ChunkPos.toLong(origin.x + dx, origin.z + dz));
                    }
                }
            }
        }
        rescanTicks = 200;
    }

    private void enqueue(long key) {
        if (pending.size() < MAX_CHUNKS && queued.add(key)) pending.add(key);
    }

    private boolean inRange(long key) {
        return trackedOrigin != null
                && Math.abs((long) ChunkPos.getPackedX(key) - trackedOrigin.x) <= trackedRange
                && Math.abs((long) ChunkPos.getPackedZ(key) - trackedOrigin.z) <= trackedRange;
    }

    private WorldChunk loadedChunk(long key) {
        if (world == null || world != BytePhase.mc.world) return null;
        int x = ChunkPos.getPackedX(key);
        int z = ChunkPos.getPackedZ(key);
        if (!world.getChunkManager().isChunkLoaded(x, z)) return null;
        WorldChunk chunk = world.getChunk(x, z);
        return chunk instanceof EmptyChunk ? null : chunk;
    }

    private long revision(long key) {
        synchronized (changeLock) {
            return revisions.getOrDefault(key, 0L);
        }
    }

    private boolean unchanged(long key, long expected) {
        synchronized (changeLock) {
            return !changed.contains(key) && revisions.getOrDefault(key, 0L) == expected;
        }
    }

    private boolean valid(Scan result) {
        if (loadedChunk(result.key) != result.chunk || !unchanged(result.key, result.revision)) return false;
        ChunkSection[] sections = result.chunk.getSectionArray();
        if (sections.length != result.sections.length) return false;
        for (int i = 0; i < sections.length; i++) {
            if (sections[i] != result.sections[i]) return false;
        }
        return true;
    }

    private void changedChunk(int x, int z) {
        synchronized (changeLock) {
            long key = ChunkPos.toLong(x, z);
            if (!inRange(key) || (!revisions.containsKey(key) && revisions.size() >= MAX_CHUNKS)) return;
            revisions.put(key, ++nextRevision);
            changed.add(key);
        }
    }

    private void changedBlock(BlockPos pos) {
        changedChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        if (packet instanceof BlockUpdateS2CPacket update) {
            changedBlock(update.getPos());
        } else if (packet instanceof ChunkDataS2CPacket data) {
            changedChunk(data.getChunkX(), data.getChunkZ());
        } else if (packet instanceof UnloadChunkS2CPacket unload) {
            changedChunk(unload.pos().x, unload.pos().z);
        } else if (packet instanceof ChunkDeltaUpdateS2CPacket delta) {
            // Every position in a delta belongs to the same section.
            BlockPos[] first = new BlockPos[1];
            delta.visitUpdates((pos, state) -> {
                if (first[0] == null) first[0] = pos.toImmutable();
            });
            if (first[0] != null) changedBlock(first[0]);
        }
        return false;
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (packet instanceof PlayerActionC2SPacket action) {
            switch (action.getAction()) {
                case START_DESTROY_BLOCK, ABORT_DESTROY_BLOCK, STOP_DESTROY_BLOCK -> changedBlock(action.getPos());
                default -> {}
            }
        } else if (packet instanceof PlayerInteractBlockC2SPacket interaction) {
            var hit = interaction.getBlockHitResult();
            changedBlock(hit.getBlockPos());
            changedBlock(hit.getBlockPos().offset(hit.getSide()));
        }
        return false;
    }

    private boolean slice(long deadline) {
        ChunkSection[] sections = scan.sections;
        while (scan.section < sections.length) {
            if (System.nanoTime() >= deadline) return false;
            ChunkSection section = sections[scan.section];
            if (!scan.paletteChecked) {
                if (section == null || section.isEmpty() || !section.hasAny(
                        state -> System.nanoTime() >= deadline || INTERESTING.test(state))) {
                    nextSection();
                    continue;
                }
                // An expired palette check conservatively falls back to block scanning.
                scan.paletteChecked = true;
            }
            while (scan.block < 4096) {
                if ((scan.block & 31) == 0 && System.nanoTime() >= deadline) return false;
                int index = scan.block++;
                int x = index & 15;
                int z = index >> 4 & 15;
                int y = index >> 8;
                BlockState state = section.getBlockState(x, y, z);
                if (INTERESTING.test(state)) {
                    BlockPos pos = new BlockPos(ChunkPos.getPackedX(scan.key) * 16 + x,
                            scan.chunk.sectionIndexToCoord(scan.section) * 16 + y,
                            ChunkPos.getPackedZ(scan.key) * 16 + z);
                    if (storage(state)) {
                        scan.storage++;
                        if (scan.storagePos == null) scan.storagePos = pos;
                    }
                    if (utility(state)) {
                        scan.utilities++;
                        if (scan.utilityPos == null) scan.utilityPos = pos;
                    }
                    if (spawners.getValue() && state.isOf(Blocks.SPAWNER) && scan.spawners.size() < 32) {
                        scan.spawners.add(pos);
                    }
                }
            }
            nextSection();
        }
        return true;
    }

    private void nextSection() {
        scan.section++;
        scan.block = 0;
        scan.paletteChecked = false;
    }

    private boolean publish(Scan result, long deadline) {
        if (!valid(result)) {
            marked.remove(result.key);
            deferred.add(result.key);
            return true;
        }
        while (result.publishedSpawners < result.spawners.size()) {
            if (System.nanoTime() >= deadline) return false;
            if (!unchanged(result.key, result.revision)) {
                marked.remove(result.key);
                deferred.add(result.key);
                return true;
            }
            BlockPos pos = result.spawners.get(result.publishedSpawners++);
            if (BytePhase.locations != null && world.getBlockState(pos).isOf(Blocks.SPAWNER)
                    && BytePhase.locations.remember("spawner", "Spawner", pos)) {
                notify("spawner:" + pos.asLong(), "Spawner", pos, result);
            }
        }
        if (System.nanoTime() >= deadline) return false;
        if (!valid(result)) {
            marked.remove(result.key);
            deferred.add(result.key);
            return true;
        }
        boolean stash = result.storage >= storageThreshold.asInt();
        boolean activity = result.utilities >= utilityThreshold.asInt();
        BlockPos evidence = stash ? result.storagePos : result.utilityPos;
        if ((stash || activity) && evidence != null) {
            String label = result.storage + " storage, " + result.utilities + " utility blocks";
            if (BytePhase.locations != null
                    && BytePhase.locations.remember(stash ? "stash" : "activity", label, evidence)) {
                notify("candidate:" + result.key, "Candidate: " + label, evidence, result);
            }
            double x = ChunkPos.getPackedX(result.key) * 16.0;
            double z = ChunkPos.getPackedZ(result.key) * 16.0;
            marked.put(result.key, new Marker(result.chunk, result.revision,
                    new Box(x, evidence.getY() - 1, z, x + 16, evidence.getY() + 2, z + 16)));
        } else {
            marked.remove(result.key);
        }
        return true;
    }

    private void notify(String id, String message, BlockPos pos, Scan result) {
        if (!announce.getValue() || alertHistory.containsKey(id)) return;
        if (alertHistory.size() >= MAX_ALERT_HISTORY) {
            alertHistory.remove(alertHistory.keySet().iterator().next());
        }
        alertHistory.put(id, true);
        if (alerts.size() >= MAX_ALERTS) alerts.poll();
        alerts.add(new Alert(result.key, result.chunk, result.revision, message, pos));
    }

    private void drainAlert() {
        alerts.removeIf(alert -> loadedChunk(alert.key) != alert.chunk || deferred.contains(alert.key)
                || !unchanged(alert.key, alert.revision)
                || Math.abs((long) ChunkPos.getPackedX(alert.key) - origin.x) > previousRange
                || Math.abs((long) ChunkPos.getPackedZ(alert.key) - origin.z) > previousRange);
        if (!announce.getValue()) {
            alerts.clear();
            return;
        }
        if (announceCooldown > 0 || alerts.isEmpty()) return;
        Alert alert = alerts.poll();
        announceCooldown = 20;
        BlockPos pos = alert.pos;
        BytePhase.mc.inGameHud.getChatHud().addMessage(Text.literal("[Bytephase] " + alert.message
                + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
    }

    private void refreshMarkers() {
        synchronized (changeLock) {
            renderBoxes.clear();
            marked.entrySet().removeIf(entry -> loadedChunk(entry.getKey()) != entry.getValue().chunk
                    || !unchanged(entry.getKey(), entry.getValue().revision)
                    || Math.abs((long) ChunkPos.getPackedX(entry.getKey()) - origin.x) > previousRange
                    || Math.abs((long) ChunkPos.getPackedZ(entry.getKey()) - origin.z) > previousRange);
            marked.forEach((key, marker) -> renderBoxes.add(marker.box));
            renderRevision = nextRevision;
        }
    }

    @Override
    public String getHudText() {
        return getName() + " [" + marked.size() + "]";
    }

    @Override
    public void onRender3D(MatrixStack matrices, VertexConsumerProvider consumers, float tickDelta) {
        if (world == null || BytePhase.mc.world != world || !markers.getValue()) return;
        // Ticks maintain the cache. Between ticks only a packet revision can
        // invalidate it, so unchanged frames reuse the same marker lists.
        if (renderRevision != nextRevision) refreshMarkers();
        if (!renderBoxes.isEmpty()) {
            EspRenderer.drawBoxes(renderBoxes, color.getValue(), "Highlight", true);
        }
    }
}
