package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.setting.BooleanSetting;
import java.util.ArrayList;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;

public final class SpawnerFinder extends DiscoveryOverlay {
    private final BooleanSetting trial = add(new BooleanSetting("TrialSpawners", false));
    private int cursor;
    private int previousX = Integer.MIN_VALUE, previousZ;
    private int previousRadius;
    private final java.util.List<ChunkPos> offsets = new ArrayList<>();

    public SpawnerFinder() {
        super("SpawnerFinder", "Finds exact received spawner positions and mob labels in loaded chunks");
    }

    @Override
    protected void reset(ClientWorld nextWorld) {
        super.reset(nextWorld);
        cursor = 0;
        previousX = Integer.MIN_VALUE;
        offsets.clear();
    }

    @Override
    protected Box box(Finding finding) { return new Box(finding.pos()).expand(0.03); }

    @Override
    public void onTick() {
        super.onTick();
        if (!ready()) return;
        findings.values().removeIf(finding -> !(world.getBlockEntity(finding.pos()) instanceof MobSpawnerBlockEntity)
                && !(trial.getValue() && world.getBlockEntity(finding.pos()) instanceof TrialSpawnerBlockEntity));
        ChunkPos center = BytePhase.mc.player.getChunkPos();
        int r = radius.asInt(), side = r * 2 + 1, count = side * side;
        if (center.x != previousX || center.z != previousZ || r != previousRadius) cursor = 0;
        if (offsets.isEmpty() || r != previousRadius) {
            offsets.clear();
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) offsets.add(new ChunkPos(dx, dz));
            offsets.sort(java.util.Comparator.comparingInt(offset -> offset.x * offset.x + offset.z * offset.z));
        }
        previousX = center.x;
        previousZ = center.z;
        previousRadius = r;
        for (int budget = 0; budget < 2; budget++) {
            ChunkPos offset = offsets.get(cursor);
            cursor = (cursor + 1) % count;
            int x = center.x + offset.x, z = center.z + offset.z;
            var chunk = world.getChunkManager().getWorldChunk(x, z);
            if (chunk == null) continue;
            for (var entry : new ArrayList<>(chunk.getBlockEntities().values())) {
                if (!(entry instanceof MobSpawnerBlockEntity) && !(trial.getValue() && entry instanceof TrialSpawnerBlockEntity)) continue;
                String label = entry instanceof TrialSpawnerBlockEntity ? "Trial spawner" : "Spawner";
                BlockPos pos = entry.getPos();
                if (entry instanceof MobSpawnerBlockEntity spawner) {
                    try {
                        var entity = spawner.getLogic().getRenderedEntity(world, pos);
                        if (entity != null) label = entity.getType().getName().getString() + " spawner";
                    } catch (RuntimeException error) {
                        BytePhase.LOGGER.debug("Spawner display entity unavailable at {}", pos, error);
                    }
                }
                found(pos.asLong(), pos, label);
            }
        }
    }
}
