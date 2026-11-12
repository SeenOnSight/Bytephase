package dev.bytephase.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.bytephase.BytePhase;
import dev.bytephase.module.render.SmallShield;
import dev.bytephase.module.render.ViewModel;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(HeldItemRenderer.class)
public abstract class FirstPersonItemMixin {
    /**
     * Both first-person hand modules transform the same matrix, so they are
     * combined into one push/translate/scale: ViewModel applies to either hand,
     * SmallShield only to the offhand. Every setting defaults to a no-op, so
     * nothing moves until one of them is changed.
     */
    @WrapMethod(method = "renderFirstPersonItem")
    private void bytephase$firstPersonTransform(AbstractClientPlayerEntity player, float tickDelta, float pitch,
                                                Hand hand, float swingProgress, ItemStack stack,
                                                float equipProgress, MatrixStack matrices,
                                                OrderedRenderCommandQueue queue, int light,
                                                Operation<Void> original) {
        if (BytePhase.mc == null || player != BytePhase.mc.player) {
            original.call(player, tickDelta, pitch, hand, swingProgress, stack, equipProgress,
                    matrices, queue, light);
            return;
        }
        ViewModel viewModel = BytePhase.modules == null ? null : BytePhase.modules.viewModel();
        boolean view = viewModel != null && viewModel.isEnabled();
        SmallShield small = hand == Hand.OFF_HAND && BytePhase.modules != null
                ? BytePhase.modules.smallShield() : null;
        boolean shield = small != null && small.isEnabled();
        if (!view && !shield) {
            original.call(player, tickDelta, pitch, hand, swingProgress, stack, equipProgress,
                    matrices, queue, light);
            return;
        }
        // The matrix is the shared camera space at this point, so a raw X
        // offset slides both hands the same way on screen and the pair looks
        // lopsided. ViewModel mirrors it on the left arm instead.
        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        float scale = view ? viewModel.scale() : 1.0f;
        float offsetX = view ? viewModel.offsetX(arm) : 0.0f;
        float offsetY = view ? viewModel.offsetY() : 0.0f;
        float offsetZ = view ? viewModel.offsetZ() : 0.0f;
        if (shield) {
            // SmallShield nudges only the offhand, so its offsets stay as typed.
            scale *= small.scale();
            offsetX += small.offsetX();
            offsetY += small.offsetY();
        }
        matrices.push();
        try {
            matrices.translate(offsetX, offsetY, offsetZ);
            matrices.scale(scale, scale, scale);
            original.call(player, tickDelta, pitch, hand, swingProgress, stack, equipProgress,
                    matrices, queue, light);
        } finally {
            matrices.pop();
        }
    }
}
