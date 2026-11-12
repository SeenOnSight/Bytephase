package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

public final class Ping extends Module {
    private String cachedText = "";

    public Ping() {
        super("Ping", Category.RENDER, "Shows your connection latency");
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.getNetworkHandler() == null) {
            cachedText = "";
            return;
        }
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        int latency = entry == null ? 0 : entry.getLatency();
        cachedText = "Ping " + latency + "ms";
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!cachedText.isEmpty() && BytePhase.mc != null) {
            context.drawTextWithShadow(BytePhase.mc.textRenderer, cachedText, 4, 68, 0xFFFFFFFF);
        }
    }
}
