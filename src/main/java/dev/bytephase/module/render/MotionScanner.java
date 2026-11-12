package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.setting.NumberSetting;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.LightData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/** Classifies explicit zero-valued received skylight sections, then groups nearby candidates. */
public final class MotionScanner extends DiscoveryOverlay {
    private final NumberSetting sensitivity = add(new NumberSetting("ClusterSize", 1, 1, 5, 1));
    private final LinkedHashMap<Long, BitSet> sections = new LinkedHashMap<>();
    private final Set<Long> notified = new HashSet<>();
    private boolean dirty;
    private int previousSensitivity;

    public MotionScanner() {
        super("MotionScanner", "Groups received skylight anomalies below sea level");
    }

    @Override
    protected void reset(ClientWorld nextWorld) {
        super.reset(nextWorld);
        sections.clear();
        notified.clear();
        dirty = true;
    }

    public void receive(ClientWorld source, int x, int z, LightData data, boolean initial) {
        if (!isEnabled() || source == null || source != BytePhase.mc.world || !ready() || !loaded(x, z)) return;
        long key = ChunkPos.toLong(x, z);
        if (initial) {
            sections.remove(key);
            notified.remove(key);
        }
        BitSet zeroSections = sections.computeIfAbsent(key, ignored -> new BitSet());
        int index = 0;
        List<byte[]> sky = data.getSkyNibbles();
        BitSet inited = data.getInitedSky(), empty = data.getUninitedSky();
        for (int bit = 0; bit < source.countVerticalSections() + 2; bit++) {
            int sectionY = source.getBottomSectionCoord() + bit - 1;
            if (inited.get(bit)) {
                byte[] values = index < sky.size() ? sky.get(index) : null;
                index++;
                boolean underground = sectionY * 16 >= source.getBottomY() && sectionY * 16 + 15 < 62;
                zeroSections.set(bit, underground && allZero(values));
            } else if (empty.get(bit)) {
                // An empty mask carries no explicit zero-filled array.
                zeroSections.clear(bit);
            }
        }
        while (sections.size() > 1024) {
            long oldest = sections.keySet().iterator().next();
            sections.remove(oldest);
            notified.remove(oldest);
        }
        dirty = true;
    }

    private static boolean allZero(byte[] values) {
        if (values == null || values.length != 2048) return false;
        for (byte value : values) if (value != 0) return false;
        return true;
    }

    @Override
    public void onTick() {
        super.onTick();
        if (!ready()) return;
        if (sections.keySet().removeIf(key -> {
            ChunkPos chunk = new ChunkPos(key);
            if (loaded(chunk.x, chunk.z)) return false;
            notified.remove(key);
            return true;
        })) dirty = true;
        int threshold = sensitivity.asInt();
        if (threshold != previousSensitivity) dirty = true;
        previousSensitivity = threshold;
        if (!dirty || BytePhase.mc.player.getY() < 0) return;
        dirty = false;
        java.util.TreeSet<Long> remaining = new java.util.TreeSet<>();
        sections.forEach((key, value) -> { if (!value.isEmpty()) remaining.add(key); });
        findings.clear();
        while (!remaining.isEmpty()) {
            long start = remaining.first();
            remaining.remove(start);
            ArrayDeque<Long> queue = new ArrayDeque<>();
            Set<Long> group = new HashSet<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                long key = queue.removeFirst();
                group.add(key);
                ChunkPos chunk = new ChunkPos(key);
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    long neighbor = ChunkPos.toLong(chunk.x + dx, chunk.z + dz);
                    if (remaining.remove(neighbor)) queue.add(neighbor);
                }
            }
            if (group.size() < threshold) continue;
            ChunkPos player = BytePhase.mc.player.getChunkPos();
            long representative = group.stream().min(java.util.Comparator.<Long>comparingLong(key -> {
                ChunkPos chunk = new ChunkPos(key);
                long dx = (long) chunk.x - player.x, dz = (long) chunk.z - player.z;
                return dx * dx + dz * dz;
            }).thenComparingLong(Long::longValue)).orElseThrow();
            ChunkPos chunk = new ChunkPos(representative);
            BlockPos pos = new BlockPos(chunk.getStartX() + 8, Math.max(world.getBottomY(), 48), chunk.getStartZ() + 8);
            String label = "Skylight anomaly (" + group.size() + " chunks)";
            boolean alreadyKnown = group.stream().anyMatch(notified::contains);
            findings.put(representative, new Finding(pos, label));
            if (!alreadyKnown) notifyFinding(pos, label);
            notified.addAll(group);
            if (findings.size() >= 256) break;
        }
    }
}
