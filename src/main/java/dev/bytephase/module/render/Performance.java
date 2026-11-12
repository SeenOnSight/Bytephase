package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.render.ChunkBuilderMode;

/** Overrides render policies at their use sites, preserving stored video preferences. */
public final class Performance extends Module {
    private final ModeSetting chunks = add(new ModeSetting("ChunkUpdates",
            new String[] {"Balanced", "Threaded", "Unchanged"}, "Balanced"));
    private final BooleanSetting streaming = add(new BooleanSetting("StreamingFpsLimit", false));
    private final NumberSetting fps = add(new NumberSetting("StreamingFps", 120, 30, 240, 1));

    public Performance() {
        super("Performance", Category.RENDER, "Controls chunk update scheduling and an optional recording FPS cap");
    }

    public static ChunkBuilderMode chunkMode(ChunkBuilderMode original) {
        var module = BytePhase.modules == null ? null : BytePhase.modules.get(Performance.class);
        if (module == null || !module.isEnabled()) return original;
        if (module.chunks.getValue().equals("Threaded")) return ChunkBuilderMode.NONE;
        return module.chunks.getValue().equals("Balanced") && original == ChunkBuilderMode.NEARBY
                ? ChunkBuilderMode.PLAYER_AFFECTED : original;
    }

    public static int cap(int original) {
        var module = BytePhase.modules == null ? null : BytePhase.modules.get(Performance.class);
        return module != null && module.isEnabled() && module.streaming.getValue()
                ? Math.min(original, module.fps.asInt()) : original;
    }
}
