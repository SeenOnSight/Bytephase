package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;

public final class StatusEffects extends Module {
    private List<String> cachedLines = List.of();

    public StatusEffects() {
        super("Effects", Category.RENDER, "Shows active potion effects");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) {
            cachedLines = List.of();
            return;
        }
        List<String> lines = new ArrayList<>();
        for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
            String name = net.minecraft.client.resource.language.I18n.translate(
                    effect.getEffectType().value().getTranslationKey());
            lines.add(name + " " + (effect.getAmplifier() + 1) + " " + formatDuration(effect.getDuration()));
        }
        cachedLines = List.copyOf(lines);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) {
            return;
        }
        int y = mc.getWindow().getScaledHeight() / 2;
        for (String line : cachedLines) {
            context.drawTextWithShadow(mc.textRenderer, line, 4, y, 0xFFFFFFFF);
            y += 10;
        }
    }

    private String formatDuration(int ticks) {
        if (ticks == StatusEffectInstance.INFINITE) {
            return "inf";
        }
        int seconds = ticks / 20;
        int remainder = seconds % 60;
        return (seconds / 60) + ":" + (remainder < 10 ? "0" : "") + remainder;
    }
}
