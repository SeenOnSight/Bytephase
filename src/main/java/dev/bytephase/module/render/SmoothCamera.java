package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;

/** Uses the game's own cinematic mouse smoothing and restores its previous value. */
public final class SmoothCamera extends Module {
    private boolean previous;
    private boolean captured;

    public SmoothCamera() {
        super("SmoothCamera", Category.RENDER, "Enables vanilla cinematic mouse smoothing");
    }

    @Override
    protected void onEnable() {
        previous = BytePhase.mc.options.smoothCameraEnabled;
        captured = true;
        BytePhase.mc.options.smoothCameraEnabled = true;
    }

    @Override
    protected void onDisable() {
        if (captured && BytePhase.mc.options.smoothCameraEnabled) {
            BytePhase.mc.options.smoothCameraEnabled = previous;
        }
        captured = false;
    }
}
