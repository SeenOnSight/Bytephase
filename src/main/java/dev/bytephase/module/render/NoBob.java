package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;

public final class NoBob extends Module {
    private boolean changed;

    public NoBob() {
        super("NoBob", Category.RENDER, "Disables view bobbing");
    }

    public boolean originalBob() { return changed; }

    @Override
    protected void onEnable() {
        var mc = BytePhase.mc;
        if (mc == null) {
            return;
        }
        changed = mc.options.getBobView().getValue();
        if (changed) {
            mc.options.getBobView().setValue(false);
        }
    }

    @Override
    protected void onDisable() {
        var mc = BytePhase.mc;
        if (mc != null && changed) {
            mc.options.getBobView().setValue(true);
            changed = false;
        }
    }
}
