package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;

public final class Coordinates extends Module {
    private String cachedText = "";

    public Coordinates() {
        super("Coordinates", Category.RENDER, "Shows current and nether/swapped coordinates");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            cachedText = "";
            return;
        }
        Vec3d pos = mc.player.getEntityPos();
        String dimension = mc.world.getRegistryKey().getValue().getPath();
        String main;
        if (dimension.contains("nether")) {
            main = String.format("(Nether) %.1f %.1f %.1f  |  (Overworld) %.0f %.0f %.0f",
                    pos.x, pos.y, pos.z, pos.x * 8, pos.y, pos.z * 8);
        } else {
            main = String.format("(XYZ) %.1f %.1f %.1f", pos.x, pos.y, pos.z);
            if (dimension.equals("overworld")) {
                main += String.format("  |  (Nether) %.1f %.1f %.1f", pos.x / 8, pos.y, pos.z / 8);
            }
        }
        cachedText = main;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var mc = BytePhase.mc;
        if (mc != null && !cachedText.isEmpty()) {
            context.drawTextWithShadow(mc.textRenderer, cachedText, 4, 40, 0xFFFFFFFF);
        }
    }
}
