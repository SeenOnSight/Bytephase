package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.ClarityTargetRenderer;
import dev.bytephase.render.RenderProjection;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

/** The supplied Clarity world target reticle, drawn on the crosshair entity. */
public final class TargetFx extends Module {
    private final ModeSetting style = add(new ModeSetting("Style", ClarityTargetRenderer.STYLES, "Reticle"));
    private final NumberSetting range = add(new NumberSetting("Range", 32.0, 4.0, 96.0, 1.0));
    private final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.05));
    private final ColorSetting primary = add(new ColorSetting("Primary", 0xFF7AA2F7));
    private final ColorSetting secondary = add(new ColorSetting("Secondary", 0xFF3D59A1));
    private final BooleanSetting throughWalls = add(new BooleanSetting("ThroughWalls", false));

    public TargetFx() {
        super("TargetFX", Category.RENDER, "Draws the supplied worldfx target reticle");
    }

    public int styleIndex() {
        String selected = style.getValue();
        for (int i = 0; i < ClarityTargetRenderer.STYLES.length; i++) {
            if (ClarityTargetRenderer.STYLES[i].equals(selected)) {
                return i;
            }
        }
        return 1;
    }

    public float range() {
        return range.asFloat();
    }

    public float scale() {
        return scale.asFloat();
    }

    public ColorSetting primary() {
        return primary;
    }

    public ColorSetting secondary() {
        return secondary;
    }

    public boolean throughWalls() {
        return throughWalls.getValue();
    }

    @Override
    public void onRender3D(MatrixStack matrices, VertexConsumerProvider consumers, float tickDelta) {
        if (BytePhase.mc == null) {
            return;
        }
        ClarityTargetRenderer.render(this, RenderProjection.tickDelta());
    }
}
