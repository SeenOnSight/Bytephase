package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.setting.NumberSetting;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import net.minecraft.block.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.LightData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/** Tracks changes against received underground block-light baselines. */
public final class PrimeChunkFinder extends DiscoveryOverlay {
    private final NumberSetting increase = add(new NumberSetting("MinLightIncrease", 3, 1, 15, 1));
    private final LinkedHashMap<Long, byte[][]> baselines = new LinkedHashMap<>();
    private final ArrayDeque<BlockPos> changes = new ArrayDeque<>();
    private static final byte[] EMPTY = new byte[2048];
    private int scanCursor;
    private int scanRadius;
    private final java.util.List<ChunkPos> scanOffsets = new java.util.ArrayList<>();
    private final java.util.Set<Long> visited = new java.util.HashSet<>();

    public PrimeChunkFinder() {
        super("PrimeChunkFinder", "Finds received underground light changes and redstone activity");
    }

    @Override
    protected void reset(ClientWorld nextWorld) {
        super.reset(nextWorld);
        baselines.clear();
        changes.clear();
        scanOffsets.clear();
        visited.clear();
        scanCursor = 0;
    }

    public void receive(ClientWorld source, int x, int z, LightData data, boolean initial) {
        if (!isEnabled() || source == null || source != BytePhase.mc.world || !ready() || !loaded(x, z)) return;
        long key = ChunkPos.toLong(x, z);
        if (initial) {
            baselines.remove(key);
            findings.remove(key);
        }
        byte[][] baseline = baselines.computeIfAbsent(key, ignored -> new byte[4][]);
        BitSet inited = data.getInitedBlock(), empty = data.getUninitedBlock();
        int index = 0;
        for (int bit = 0; bit < source.countVerticalSections() + 2; bit++) {
            byte[] values = null;
            if (inited.get(bit)) {
                values = index < data.getBlockNibbles().size() ? data.getBlockNibbles().get(index) : null;
                index++;
            } else if (empty.get(bit)) values = EMPTY;
            int section = source.getChunkManager().getLightingProvider().getBottomY() + bit;
            if (section < -4 || section >= 0 || values == null || values.length != 2048) continue;
            byte[] before = baseline[section + 4];
            if (!initial && before != null && !Arrays.equals(before, values)) {
                int strongest = -1, delta = increase.asInt() - 1;
                for (int cell = 0; cell < 4096; cell++) {
                    int shift = (cell & 1) * 4;
                    int difference = (values[cell >> 1] >> shift & 15) - (before[cell >> 1] >> shift & 15);
                    if (difference > delta) { delta = difference; strongest = cell; }
                }
                if (strongest >= 0) {
                    changes.add(new BlockPos((x << 4) + (strongest & 15), (section << 4) + (strongest >> 8),
                            (z << 4) + (strongest >> 4 & 15)));
                    if (changes.size() > 256) changes.removeFirst();
                }
            }
            baseline[section + 4] = values == EMPTY ? EMPTY : values.clone();
        }
        while (baselines.size() > 625) baselines.remove(baselines.keySet().iterator().next());
    }

    public void blockUpdate(ClientWorld source, BlockPos pos, BlockState before, BlockState after) {
        if (!isEnabled() || source != BytePhase.mc.world || !ready() || pos.getY() >= 0
                || before.equals(after) || !loaded(pos.getX() >> 4, pos.getZ() >> 4)) return;
        if (redstone(before) || redstone(after)) mark(pos, "Redstone activity");
    }

    private static boolean redstone(BlockState state) {
        Block block = state.getBlock();
        return block instanceof RedstoneWireBlock || block instanceof AbstractRedstoneGateBlock
                || block instanceof PistonBlock || block instanceof ObserverBlock || block instanceof LeverBlock
                || block instanceof ButtonBlock || block instanceof AbstractPressurePlateBlock
                || block instanceof HopperBlock || block instanceof DispenserBlock
                || block instanceof RedstoneTorchBlock || state.isOf(Blocks.REDSTONE_LAMP)
                || state.isOf(Blocks.TRIPWIRE) || state.isOf(Blocks.TRIPWIRE_HOOK);
    }

    private void mark(BlockPos pos, String reason) {
        if (!loaded(pos.getX() >> 4, pos.getZ() >> 4)
                || visited.contains(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4))
                || net.minecraft.util.math.Vec3d.ofCenter(pos).squaredDistanceTo(BytePhase.mc.player.getEntityPos()) < 256) return;
        found(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4), pos, reason);
    }

    @Override
    public void onTick() {
        super.onTick();
        if (!ready()) return;
        baselines.keySet().removeIf(key -> { ChunkPos chunk = new ChunkPos(key); return !loaded(chunk.x, chunk.z); });
        visited.removeIf(key -> { ChunkPos chunk = new ChunkPos(key); return !loaded(chunk.x, chunk.z); });
        ChunkPos center = BytePhase.mc.player.getChunkPos();
        if (BytePhase.mc.player.getY() < 0) {
            visited.add(center.toLong());
            findings.remove(center.toLong());
        }
        if (scanOffsets.isEmpty() || scanRadius != radius.asInt()) {
            scanRadius = radius.asInt();
            scanOffsets.clear();
            for (int dx = -scanRadius; dx <= scanRadius; dx++) {
                for (int dz = -scanRadius; dz <= scanRadius; dz++) scanOffsets.add(new ChunkPos(dx, dz));
            }
            scanOffsets.sort(java.util.Comparator.comparingInt(offset -> offset.x * offset.x + offset.z * offset.z));
            scanCursor = 0;
        }
        for (int budget = 0; budget < 2; budget++) {
            ChunkPos offset = scanOffsets.get(scanCursor);
            scanCursor = (scanCursor + 1) % scanOffsets.size();
            int x = center.x + offset.x, z = center.z + offset.z;
            long key = ChunkPos.toLong(x, z);
            if (!loaded(x, z) || baselines.containsKey(key)) continue;
            var provider = world.getChunkManager().getLightingProvider();
            byte[][] baseline = new byte[4][];
            for (int section = Math.max(-4, world.getBottomSectionCoord()); section < 0; section++) {
                var nibbles = provider.get(net.minecraft.world.LightType.BLOCK).getLightSection(
                        net.minecraft.util.math.ChunkSectionPos.from(x, section, z));
                if (nibbles != null) baseline[section + 4] = nibbles.asByteArray().clone();
            }
            baselines.put(key, baseline);
        }
        for (int budget = 0; budget < 16 && !changes.isEmpty(); budget++) {
            BlockPos pos = changes.removeFirst();
            if (!loaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            boolean natural = false;
            for (BlockPos near : BlockPos.iterate(pos.add(-2, -2, -2), pos.add(2, 2, 2))) {
                if (!world.isChunkLoaded(near)) continue;
                BlockState state = world.getBlockState(near);
                if (state.isOf(Blocks.LAVA) || state.isOf(Blocks.BUDDING_AMETHYST)
                        || state.isOf(Blocks.AMETHYST_CLUSTER) || state.isOf(Blocks.SMALL_AMETHYST_BUD)
                        || state.isOf(Blocks.MEDIUM_AMETHYST_BUD) || state.isOf(Blocks.LARGE_AMETHYST_BUD)) {
                    natural = true;
                    break;
                }
            }
            if (!natural) mark(pos, "Underground light increase");
        }
    }
}
