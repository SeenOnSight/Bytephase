package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.render.ChunkBuilderMode;
import net.minecraft.client.particle.Particle;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;

/** Overrides render policies at their use sites, preserving stored video preferences. */
public final class Performance extends Module {
    private final ModeSetting chunks = add(new ModeSetting("ChunkUpdates",
            new String[] {"Balanced", "Threaded", "Unchanged"}, "Balanced"));
    private final BooleanSetting streaming = add(new BooleanSetting("StreamingFpsLimit", false));
    private final NumberSetting fps = add(new NumberSetting("StreamingFps", 120, 30, 240, 1));
    private final BooleanSetting limitParticles = add(new BooleanSetting("LimitParticles", true));
    private final NumberSetting particlesPerTick = add(new NumberSetting("ParticlesPerTick", 128, 16, 512, 16));
    private final NumberSetting particleDistance = add(new NumberSetting("ParticleDistance", 48, 8, 128, 4));
    private final BooleanSetting cullItems = add(new BooleanSetting("CullDistantItems", true));
    private final NumberSetting itemDistance = add(new NumberSetting("ItemRenderDistance", 48, 16, 128, 4));
    private int spawnedParticles;

    public Performance() {
        super("Performance", Category.RENDER, "Limits particle work and distant item rendering, and controls chunk scheduling");
        fps.visibleWhen(streaming::getValue);
        particlesPerTick.visibleWhen(limitParticles::getValue);
        particleDistance.visibleWhen(limitParticles::getValue);
        itemDistance.visibleWhen(cullItems::getValue);
    }

    @Override
    protected void onEnable() {
        spawnedParticles = 0;
    }

    @Override
    public void onTick() {
        spawnedParticles = 0;
    }

    private static Performance active() {
        Performance module = BytePhase.modules == null ? null : BytePhase.modules.performance();
        return module != null && module.isEnabled() ? module : null;
    }

    public static boolean cullItem(Entity entity, double cameraX, double cameraY, double cameraZ) {
        Performance module = active();
        if (module == null || !module.cullItems.getValue() || !(entity instanceof ItemEntity)) return false;
        double range = module.itemDistance.getValue();
        return entity.squaredDistanceTo(cameraX, cameraY, cameraZ) > range * range;
    }

    public static boolean canCreateParticle(double x, double y, double z) {
        Performance module = active();
        return module == null || module.particleAllowed(x, y, z);
    }

    public static boolean allowParticle(Particle particle) {
        Performance module = active();
        if (module == null || !module.limitParticles.getValue()) return true;
        var mc = BytePhase.mc;
        if (mc == null || mc.world == null || mc.player == null) return true;
        var box = particle.getBoundingBox();
        if (!module.particleAllowed((box.minX + box.maxX) * 0.5,
                (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5)) return false;
        module.spawnedParticles++;
        return true;
    }

    private boolean particleAllowed(double x, double y, double z) {
        if (!limitParticles.getValue()) return true;
        var mc = BytePhase.mc;
        if (mc == null || mc.world == null || mc.player == null) return true;
        if (spawnedParticles >= particlesPerTick.asInt()) return false;
        Entity camera = mc.getCameraEntity();
        if (camera == null) camera = mc.player;
        double dx = x - camera.getX(), dy = y - camera.getY(), dz = z - camera.getZ();
        double range = particleDistance.getValue();
        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    public static ChunkBuilderMode chunkMode(ChunkBuilderMode original) {
        var module = active();
        if (module == null || !module.isEnabled()) return original;
        if (module.chunks.getValue().equals("Threaded")) return ChunkBuilderMode.NONE;
        return module.chunks.getValue().equals("Balanced") && original == ChunkBuilderMode.NEARBY
                ? ChunkBuilderMode.PLAYER_AFFECTED : original;
    }

    public static int cap(int original) {
        var module = active();
        return module != null && module.isEnabled() && module.streaming.getValue()
                ? Math.min(original, module.fps.asInt()) : original;
    }
}
