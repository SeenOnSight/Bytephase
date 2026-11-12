package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.EspRenderer;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.ModeSetting;
import net.minecraft.block.BlockState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class BlockHighlight extends Module {
    private final ColorSetting color = add(new ColorSetting("Color", 0xFF6466E9));
    private final ModeSetting style = add(new ModeSetting("Style",
            EspRenderer.STYLES, "Highlight"));

    public BlockHighlight() {
        super("Highlight", Category.RENDER, "Highlights the block you look at");
    }

    @Override
    public void onRender3D(MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider consumers, float tickDelta) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.crosshairTarget == null) {
            return;
        }
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        EspRenderer.drawBox(new Box(pos).expand(0.002), color.getValue(), style.getValue());
    }
}
