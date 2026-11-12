package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundEvents;

/**
 * Pulses a red screen border and plays a heartbeat when health is low.
 *
 * <p>Ported from Anubis' {@code LowHealthWarningModule}.</p>
 */
public final class LowHealthWarning extends Module {
    private final NumberSetting threshold = add(new NumberSetting("Health", 8.0, 1.0, 20.0, 1.0));
    private final BooleanSetting sound = add(new BooleanSetting("Sound", true));
    private final BooleanSetting absorption = add(new BooleanSetting("IncludeAbsorption", true));

    private long lastSound;

    public LowHealthWarning() {
        super("LowHealth", Category.RENDER, "Warns you when your health is low");
    }

    @Override
    protected void onDisable() {
        lastSound = 0L;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (!sound.getValue() || mc == null || mc.player == null || !low(mc.player)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSound < 1000L) {
            return;
        }
        lastSound = now;
        mc.player.playSound(SoundEvents.ENTITY_PLAYER_HURT, 0.35f, 1.6f);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || !low(mc.player)) {
            return;
        }
        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 180.0);
        int baseAlpha = (int) (40 + 60 * pulse);
        // Eight bands per edge instead of one fill per pixel row: the previous
        // version queued ~384 GUI fills every frame, which is a lot of render
        // state for a vignette.
        int layers = 8;
        int thickness = Math.max(layers, Math.min(width, height) / 14);
        for (int i = 0; i < layers; i++) {
            float falloff = 1.0f - (float) i / layers;
            int alpha = (int) (baseAlpha * falloff * 0.6f);
            if (alpha <= 0) {
                continue;
            }
            int argb = (alpha << 24) | 0xFF2B2B;
            int inner = i * thickness / layers;
            int outer = (i + 1) * thickness / layers;
            context.fill(inner, 0, outer, height, argb);
            context.fill(width - outer, 0, width - inner, height, argb);
            context.fill(0, inner, width, outer, argb);
            context.fill(0, height - outer, width, height - inner, argb);
        }
    }

    private boolean low(net.minecraft.entity.player.PlayerEntity player) {
        float health = player.getHealth();
        if (absorption.getValue()) {
            health += player.getAbsorptionAmount();
        }
        return health > 0.0f && health <= threshold.getValue();
    }
}
