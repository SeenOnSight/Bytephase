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
import dev.bytephase.util.DiscoverySignals;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.BitSet;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.text.Text;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Finds amethyst growth candidates in loaded chunks. Growth and geode presence
 * are heuristics, never proof of a base or a currently online player.
 * Scanning is resumable and uses Bytephase's existing chunk-marker renderer.
 */
public final class SusChunks extends Module {
    private static final long SCAN_BUDGET_NANOS = 1_500_000L;
    private static final int RESCAN_TICKS = 400;
    private static final int MAX_CHUNKS = 289;
    private static final int MAX_ALERTS = 128;
    private static final int MAX_ALERT_HISTORY = 512;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };
    private static final long CHUNK_TIMEOUT_NANOS = 120_000_000_000L;

    private static final class Suspect {
        final WorldChunk chunk;
        final long revision;
        final Box box;
        final Vec3d center;
        final long since;

        Suspect(WorldChunk chunk, long revision, int minY, int maxY, long since) {
            this.chunk = chunk;
            this.revision = revision;
            this.since = since;
            double x = chunk.getPos().x * 16.0;
            double z = chunk.getPos().z * 16.0;
            this.box = new Box(x, minY - 1.0, z, x + 16.0, maxY + 2.0, z + 16.0);
            this.center = box.getCenter();
        }
    }

    private final NumberSetting range = add(new NumberSetting("Range", 4.0, 1.0, 8.0, 1.0));
    private final ModeSetting signal = add(new ModeSetting("Signal",
            new String[]{"Growth", "Geode", "WeightedGrowth", "Activity"}, "Growth"));
    private final NumberSetting minBudding = add(new NumberSetting("MinBudding", 6.0, 1.0, 64.0, 1.0));
    private final NumberSetting minGrowth = add(new NumberSetting("MinGrowth", 6.0, 1.0, 64.0, 1.0));
    private final NumberSetting minActivity = add(new NumberSetting("MinActivity", 4, 1, 128, 1));
    private final BooleanSetting kelp = add(new BooleanSetting("Kelp", false));
    private final BooleanSetting caveVines = add(new BooleanSetting("CaveVines", false));
    private final BooleanSetting vines = add(new BooleanSetting("Vines", false));
    private final BooleanSetting bamboo = add(new BooleanSetting("Bamboo", false));
    private final BooleanSetting beeNests = add(new BooleanSetting("BeeNests", false));
    private final BooleanSetting cocoa = add(new BooleanSetting("Cocoa", false));
    private final BooleanSetting rotatedDeepslate = add(new BooleanSetting("RotatedDeepslate", false));
    private final BooleanSetting verticalTunnels = add(new BooleanSetting("VerticalTunnels", false));
    private final NumberSetting minTunnelLength = add(new NumberSetting("MinTunnelLength", 7, 3, 40, 1));
    private final BooleanSetting logFinds = add(new BooleanSetting("LogFinds", true));
    private final BooleanSetting announce = add(new BooleanSetting("ChatAnnounce", true));
    private final BooleanSetting tracers = add(new BooleanSetting("Tracers", false));
    private final ModeSetting style = add(new ModeSetting("Style",
            EspRenderer.STYLES, "Highlight"));
    private final BooleanSetting throughWalls = add(new BooleanSetting("ThroughWalls", true));
    private final ColorSetting color = add(new ColorSetting("Color", 0xFF6466E9));

    private static final class Scan {
        final WorldChunk chunk;
        final ChunkSection[] sections;
        final long revision;
        final boolean geode;
        final boolean activityMode;
        final boolean weighted;
        final DiscoverySignals.Options options;
        final boolean tunnels;
        final int tunnelLength;
        final Map<Long, WorldChunk> neighbors = new HashMap<>();
        final int[] shaftRuns = new int[256];
        final BitSet shaftCounted = new BitSet(256);
        int nextSection;
        int nextBlock;
        boolean paletteChecked;
        int budding;
        int growth;
        int activity;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        BlockPos evidence;

        Scan(WorldChunk chunk, long revision, String mode, DiscoverySignals.Options options,
             boolean tunnels, int tunnelLength) {
            this.chunk = chunk;
            this.sections = chunk.getSectionArray().clone();
            this.revision = revision;
            this.geode = mode.equals("Geode");
            this.activityMode = mode.equals("Activity");
            this.weighted = mode.equals("WeightedGrowth");
            this.options = options;
            this.tunnels = tunnels && activityMode;
            this.tunnelLength = tunnelLength;
        }
    }

    private record Alert(long key, WorldChunk chunk, long revision, String label, BlockPos pos) {}

    private final Map<Long, Suspect> suspects = new HashMap<>();
    private final Map<Long, Scan> scans = new HashMap<>();
    private final Deque<Long> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private final Set<Long> deferred = new HashSet<>();
    private final List<Box> boxes = new ArrayList<>();
    private final List<Vec3d> centers = new ArrayList<>();
    private final Deque<Alert> alerts = new ArrayDeque<>();
    private final Map<Long, Integer> alertHistory = new LinkedHashMap<>();
    private final Object changeLock = new Object();
    private final Map<Long, Long> revisions = new HashMap<>();
    private final Set<Long> changed = new HashSet<>();
    private ChunkPos trackedOrigin;
    private int trackedRange;
    private volatile long nextRevision;
    private long renderRevision;

    private ChunkPos scanOrigin;
    private int scanRange = -1;
    private int rescanTicks;
    private ClientWorld lastWorld;
    private String lastSettings;
    private int announceCooldown;

    public SusChunks() {
        super("SusChunks", Category.EXPLORE, "Finds amethyst, plant-growth and construction signals in loaded chunks");
    }

    @Override
    protected void onEnable() {
        lastWorld = null;
        reset();
    }

    @Override
    protected void onDisable() {
        reset();
        lastWorld = null;
    }

    private void reset() {
        resetScan();
        alerts.clear();
        alertHistory.clear();
        synchronized (changeLock) {
            trackedOrigin = null;
            revisions.clear();
            changed.clear();
        }
        lastSettings = null;
        announceCooldown = 0;
    }

    private void resetScan() {
        suspects.clear();
        scans.clear();
        queue.clear();
        queued.clear();
        deferred.clear();
        boxes.clear();
        centers.clear();
        scanOrigin = null;
        scanRange = -1;
        rescanTicks = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            reset();
            lastWorld = null;
            return;
        }
        if (mc.world != lastWorld) {
            reset();
            lastWorld = mc.world;
        }
        String currentSettings = signal.getValue() + ":" + minBudding.asInt() + ":" + minGrowth.asInt()
                + ":" + logFinds.getValue() + ":" + minActivity.asInt() + ":" + options()
                + ":" + verticalTunnels.getValue() + ":" + minTunnelLength.asInt();
        if (!currentSettings.equals(lastSettings)) {
            resetScan();
            alerts.clear();
            lastSettings = currentSettings;
        }
        if (announceCooldown > 0) announceCooldown--;
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
            deferred.clear();
            pruneSuspects(playerChunk, currentRange);
            enqueueAroundPlayer();
        } else if (--rescanTicks <= 0 && queue.isEmpty() && scans.isEmpty()) {
            enqueueAroundPlayer();
        }
        for (long key : deferred) enqueue(key);
        deferred.clear();
        synchronized (changeLock) {
            deferred.addAll(changed);
            changed.clear();
        }
        for (long key : deferred) {
            scans.remove(key);
            suspects.remove(key);
        }
        long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;
        while (!queue.isEmpty() && System.nanoTime() < deadline) {
            long key = queue.peek();
            if (deferred.contains(key) || !scanSlice(key, deadline)) {
                queue.poll();
                queued.remove(key);
                scans.remove(key);
            }
        }

        rebuildRenderCache(System.nanoTime());
        drainAlert();
    }

    /** @return true while the chunk still has sections left to scan. */
    private boolean scanSlice(long key, long deadline) {
        WorldChunk chunk = loadedChunk(key);
        if (chunk == null) {
            suspects.remove(key);
            return false;
        }

        Scan scan = scans.computeIfAbsent(key,
                ignored -> new Scan(chunk, revision(key), signal.getValue(), options(),
                        verticalTunnels.getValue(), minTunnelLength.asInt()));
        if (!valid(key, scan)) {
            suspects.remove(key);
            deferred.add(key);
            return false;
        }
        ChunkSection[] sections = scan.sections;
        while (scan.nextSection < sections.length) {
            if (System.nanoTime() >= deadline) return true;
            ChunkSection section = sections[scan.nextSection];
            int sectionIndex = scan.nextSection;
            if (!scan.paletteChecked) {
                if (section == null || section.isEmpty() || !section.hasAny(
                        state -> System.nanoTime() >= deadline || evidence(state, scan))) {
                    Arrays.fill(scan.shaftRuns, 0);
                    nextSection(scan);
                    continue;
                }
                scan.paletteChecked = true;
            }
            if (scanSection(chunk, section, sectionIndex, scan, deadline)) {
                nextSection(scan);
            }
            return true;
        }

        if (System.nanoTime() >= deadline) return true;
        if (!valid(key, scan)) {
            suspects.remove(key);
            deferred.add(key);
            return false;
        }
        finishChunk(key, scan);
        return false;
    }

    private static void nextSection(Scan scan) {
        scan.nextSection++;
        scan.nextBlock = 0;
        scan.paletteChecked = false;
    }

    private boolean scanSection(WorldChunk chunk, ChunkSection section, int sectionIndex,
                                Scan scan, long deadline) {
        int sectionY = chunk.sectionIndexToCoord(sectionIndex) * 16;
        while (scan.nextBlock < 4096) {
            if ((scan.nextBlock & 31) == 0 && System.nanoTime() >= deadline) return false;
            int index = scan.nextBlock++;
            int y = index / 256;
            int x = index / 16 % 16;
            int z = index % 16;
            BlockState state = section.getBlockState(x, y, z);
            boolean budding = state.isOf(Blocks.BUDDING_AMETHYST);
            BlockPos pos = null;
            if (budding || isGrowth(state) || scan.activityMode && (DiscoverySignals.enabled(state, scan.options) || scan.tunnels)) {
                pos = new BlockPos(chunk.getPos().x * 16 + x, sectionY + y, chunk.getPos().z * 16 + z);
            }
            boolean growth = !scan.geode && !scan.activityMode && isGrowth(state) && attachedToBudding(state, pos, scan);
            if (budding) scan.budding++;
            if (growth) scan.growth += scan.weighted ? DiscoverySignals.growthWeight(state) : 1;
            int activity = 0;
            if (scan.activityMode && DiscoverySignals.enabled(state, scan.options)) {
                boolean needsNeighbors = state.isOf(Blocks.KELP) || state.isOf(Blocks.CAVE_VINES)
                        || state.isOf(Blocks.VINE) || state.isOf(Blocks.BAMBOO);
                activity = DiscoverySignals.activity(state, needsNeighbors ? neighborState(pos.up(), scan) : null,
                        needsNeighbors ? neighborState(pos.down(), scan) : null, scan.options);
            }
            if (scan.tunnels) {
                int column = x * 16 + z;
                if (state.isAir() && enclosed(pos, scan)) {
                    if (++scan.shaftRuns[column] >= scan.tunnelLength && !scan.shaftCounted.get(column)) {
                        scan.shaftCounted.set(column);
                        activity++;
                    }
                } else {
                    scan.shaftRuns[column] = 0;
                }
            }
            scan.activity += activity;
            if (scan.activityMode ? activity > 0 : scan.geode ? budding : growth) {
                int worldY = sectionY + y;
                scan.minY = Math.min(scan.minY, worldY);
                scan.maxY = Math.max(scan.maxY, worldY);
                if (scan.evidence == null) scan.evidence = pos;
            }
        }
        return true;
    }

    private static boolean isGrowth(BlockState state) {
        return state.isOf(Blocks.SMALL_AMETHYST_BUD) || state.isOf(Blocks.MEDIUM_AMETHYST_BUD)
                || state.isOf(Blocks.LARGE_AMETHYST_BUD) || state.isOf(Blocks.AMETHYST_CLUSTER);
    }

    private boolean attachedToBudding(BlockState state, BlockPos pos, Scan scan) {
        BlockPos neighbor = pos.offset(state.get(AmethystClusterBlock.FACING).getOpposite());
        BlockState support = neighborState(neighbor, scan);
        return support != null && support.isOf(Blocks.BUDDING_AMETHYST);
    }

    private BlockState neighborState(BlockPos neighbor, Scan scan) {
        if (lastWorld.isOutOfHeightLimit(neighbor)) return null;
        long key = ChunkPos.toLong(neighbor.getX() >> 4, neighbor.getZ() >> 4);
        WorldChunk chunk;
        if (key == scan.chunk.getPos().toLong()) {
            chunk = scan.chunk;
        } else {
            if (!scan.neighbors.containsKey(key)) scan.neighbors.put(key, loadedChunk(key));
            chunk = scan.neighbors.get(key);
        }
        return chunk == null ? null : chunk.getBlockState(neighbor);
    }

    private boolean enclosed(BlockPos pos, Scan scan) {
        for (Direction direction : HORIZONTAL) {
            BlockPos neighbor = pos.offset(direction);
            BlockState state = neighborState(neighbor, scan);
            if (state == null || !state.getFluidState().isEmpty() || !state.isFullCube(lastWorld, neighbor)) return false;
        }
        return true;
    }

    private DiscoverySignals.Options options() {
        return new DiscoverySignals.Options(kelp.getValue(), caveVines.getValue(), vines.getValue(),
                bamboo.getValue(), beeNests.getValue(), cocoa.getValue(), rotatedDeepslate.getValue());
    }

    private static boolean evidence(BlockState state, Scan scan) {
        return scan.activityMode ? DiscoverySignals.enabled(state, scan.options) || scan.tunnels && state.isAir()
                : state.isOf(Blocks.BUDDING_AMETHYST) || isGrowth(state);
    }

    private boolean qualifies(Scan scan) {
        return scan.activityMode ? scan.activity >= minActivity.asInt() : scan.geode ? scan.budding >= minBudding.asInt()
                : scan.growth >= minGrowth.asInt();
    }

    private void finishChunk(long key, Scan scan) {
        if (qualifies(scan) && scan.evidence != null) {
            suspects.put(key, new Suspect(scan.chunk, scan.revision, scan.minY, scan.maxY, System.nanoTime()));
            BlockPos pos = scan.evidence;
            String label = scan.activityMode ? "Activity (" + scan.activity + " signals)"
                    : scan.geode ? "Geode (" + scan.budding + " budding)"
                    : scan.weighted ? "Amethyst growth (" + scan.growth + " weighted)"
                    : "Amethyst growth (" + scan.growth + ")";
            boolean recorded = false;
            if (logFinds.getValue() && BytePhase.locations != null) {
                recorded = BytePhase.locations.remember(scan.activityMode ? "activity" : "geode", label, pos);
            }
            int mode = scan.activityMode ? 8 : scan.weighted ? 4 : scan.geode ? 2 : 1;
            int previous = alertHistory.getOrDefault(key, 0);
            if (announce.getValue() && (previous & mode) == 0 && (!logFinds.getValue() || recorded)) {
                if (alerts.size() >= MAX_ALERTS) alerts.poll();
                alerts.add(new Alert(key, scan.chunk, scan.revision, label, pos));
            }
            if (!alertHistory.containsKey(key) && alertHistory.size() >= MAX_ALERT_HISTORY) {
                alertHistory.remove(alertHistory.keySet().iterator().next());
            }
            alertHistory.put(key, previous | mode);
        } else {
            suspects.remove(key);
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
        synchronized (changeLock) {
            trackedOrigin = scanOrigin;
            trackedRange = radius;
            revisions.keySet().removeIf(key -> !inTrackedRange(key));
            changed.removeIf(key -> !inTrackedRange(key));
        }
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                        enqueue(ChunkPos.toLong(scanOrigin.x + dx, scanOrigin.z + dz));
                    }
                }
            }
        }
    }

    private void pruneSuspects(ChunkPos center, int radius) {
        suspects.keySet().removeIf(key ->
                Math.abs((long) ChunkPos.getPackedX(key) - center.x) > radius
                        || Math.abs((long) ChunkPos.getPackedZ(key) - center.z) > radius);
        rebuildRenderCache(System.nanoTime());
    }

    private void rebuildRenderCache(long now) {
        synchronized (changeLock) {
            boxes.clear();
            centers.clear();
            suspects.entrySet().removeIf(entry -> now - entry.getValue().since > CHUNK_TIMEOUT_NANOS
                    || loadedChunk(entry.getKey()) != entry.getValue().chunk
                    || !unchanged(entry.getKey(), entry.getValue().revision)
                    || scanOrigin == null
                    || Math.abs((long) ChunkPos.getPackedX(entry.getKey()) - scanOrigin.x) > scanRange
                    || Math.abs((long) ChunkPos.getPackedZ(entry.getKey()) - scanOrigin.z) > scanRange);
            for (Map.Entry<Long, Suspect> entry : suspects.entrySet()) {
                Suspect suspect = entry.getValue();
                boxes.add(suspect.box);
                centers.add(suspect.center);
            }
            renderRevision = nextRevision;
        }
    }

    private void enqueue(long key) {
        if (queue.size() < MAX_CHUNKS && queued.add(key)) queue.add(key);
    }

    private boolean inTrackedRange(long key) {
        return trackedOrigin != null
                && Math.abs((long) ChunkPos.getPackedX(key) - trackedOrigin.x) <= trackedRange
                && Math.abs((long) ChunkPos.getPackedZ(key) - trackedOrigin.z) <= trackedRange;
    }

    private WorldChunk loadedChunk(long key) {
        if (lastWorld == null || lastWorld != BytePhase.mc.world) return null;
        int x = ChunkPos.getPackedX(key);
        int z = ChunkPos.getPackedZ(key);
        if (!lastWorld.getChunkManager().isChunkLoaded(x, z)) return null;
        WorldChunk chunk = lastWorld.getChunk(x, z);
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

    private boolean valid(long key, Scan scan) {
        if (loadedChunk(key) != scan.chunk || !unchanged(key, scan.revision)) return false;
        ChunkSection[] sections = scan.chunk.getSectionArray();
        if (sections.length != scan.sections.length) return false;
        for (int i = 0; i < sections.length; i++) {
            if (sections[i] != scan.sections[i]) return false;
        }
        for (Map.Entry<Long, WorldChunk> neighbor : scan.neighbors.entrySet()) {
            if (loadedChunk(neighbor.getKey()) != neighbor.getValue()) return false;
        }
        return true;
    }

    private void changedChunk(int x, int z) {
        synchronized (changeLock) {
            // Growth attachment can depend on the four adjacent chunks.
            changeNotice(x, z);
            changeNotice(x - 1, z);
            changeNotice(x + 1, z);
            changeNotice(x, z - 1);
            changeNotice(x, z + 1);
        }
    }

    private void changeNotice(int x, int z) {
        long key = ChunkPos.toLong(x, z);
        if (!inTrackedRange(key) || (!revisions.containsKey(key) && revisions.size() >= MAX_CHUNKS)) return;
        revisions.put(key, ++nextRevision);
        changed.add(key);
    }

    private void changedBlock(BlockPos pos) {
        int x = pos.getX() >> 4;
        int z = pos.getZ() >> 4;
        synchronized (changeLock) {
            changeNotice(x, z);
            if ((pos.getX() & 15) == 0) changeNotice(x - 1, z);
            if ((pos.getX() & 15) == 15) changeNotice(x + 1, z);
            if ((pos.getZ() & 15) == 0) changeNotice(x, z - 1);
            if ((pos.getZ() & 15) == 15) changeNotice(x, z + 1);
        }
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
            BlockPos[] first = new BlockPos[1];
            delta.visitUpdates((pos, state) -> {
                if (first[0] == null) first[0] = pos.toImmutable();
            });
            if (first[0] != null) changedChunk(first[0].getX() >> 4, first[0].getZ() >> 4);
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

    private void drainAlert() {
        alerts.removeIf(alert -> loadedChunk(alert.key) != alert.chunk || deferred.contains(alert.key)
                || !unchanged(alert.key, alert.revision)
                || Math.abs((long) ChunkPos.getPackedX(alert.key) - scanOrigin.x) > scanRange
                || Math.abs((long) ChunkPos.getPackedZ(alert.key) - scanOrigin.z) > scanRange);
        if (!announce.getValue()) {
            alerts.clear();
            return;
        }
        if (announceCooldown > 0 || alerts.isEmpty()) return;
        Alert alert = alerts.poll();
        announceCooldown = 20;
        BlockPos pos = alert.pos;
        BytePhase.mc.inGameHud.getChatHud().addMessage(Text.literal("[Bytephase] " + alert.label
                + " candidate at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
    }

    @Override
    public String getHudText() {
        return getName() + " [" + suspects.size() + "]";
    }

    @Override
    public void onRender3D(MatrixStack matrices,
                           net.minecraft.client.render.VertexConsumerProvider consumers,
                           float tickDelta) {
        if (lastWorld == null || BytePhase.mc.world != lastWorld) return;
        if (renderRevision != nextRevision) rebuildRenderCache(System.nanoTime());
        if (boxes.isEmpty()) return;
        EspRenderer.drawBoxes(boxes, color.getValue(), style.getValue(), throughWalls.getValue());
        if (tracers.getValue()) {
            RenderBox.drawLinesFrom(RenderBox.tracerOrigin(), centers,
                    EspRenderer.withAlpha(color.rgb(), 0x80), throughWalls.getValue());
        }
    }
}
