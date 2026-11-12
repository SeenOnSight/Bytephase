package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.EspRenderer;
import dev.bytephase.render.RenderProjection;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class LogoutSpots extends Module {
    private static final class Spot {
        final String name;
        final Vec3d pos;
        final Box bounds;
        final long time;

        Spot(String name, Vec3d pos, long time) {
            this.name = name;
            this.pos = pos;
            this.bounds = new Box(pos.x - 0.3, pos.y, pos.z - 0.3,
                    pos.x + 0.3, pos.y + 2.0, pos.z + 0.3);
            this.time = time;
        }
    }

    private final BooleanSetting showLabels = add(new BooleanSetting("Labels", true));
    private final NumberSetting lastSeenTimeout = add(new NumberSetting("LastSeenTimeout", 60.0, 5.0, 300.0, 5.0));
    private final ModeSetting style = add(new ModeSetting("Style",
            EspRenderer.STYLES, "Box"));
    private final ColorSetting color = add(new ColorSetting("Color", 0xFF6466E9));
    // Keep the newest spots first-class while bounding memory during long sessions.
    private final Map<UUID, Spot> spots = new LinkedHashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Vec3d> lastSeen = new HashMap<>();
    private final Map<UUID, Long> lastSeenAt = new HashMap<>();
    private final List<Box> boxes = new ArrayList<>();
    private World lastWorld;

    public LogoutSpots() {
        super("LogoutSpots", Category.RENDER, "Marks where players logged out");
    }

    @Override
    public boolean usesProjection() {
        return true;
    }

    @Override
    protected void onEnable() {
        clear();
    }

    @Override
    protected void onDisable() {
        clear();
    }

    private void clear() {
        spots.clear();
        names.clear();
        lastSeen.clear();
        lastSeenAt.clear();
        boxes.clear();
        lastWorld = null;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            return;
        }
        if (mc.world != lastWorld) {
            clear();
            lastWorld = mc.world;
            return;
        }
        long now = System.currentTimeMillis();
        // Keep last-seen entries for players who are no longer in the world:
        // that position is what the logout-spot feature needs. Entries for
        // players still online are refreshed and any active spot is removed.
        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            UUID uuid = player.getUuid();
            names.computeIfAbsent(uuid, ignored -> player.getName().getString());
            lastSeen.put(uuid, player.getEntityPos());
            lastSeenAt.put(uuid, now);
            spots.remove(uuid);
        }
        long cacheLimit = (long) (lastSeenTimeout.getValue() * 1000.0);
        Iterator<Map.Entry<UUID, Long>> seen = lastSeenAt.entrySet().iterator();
        while (seen.hasNext()) {
            Map.Entry<UUID, Long> entry = seen.next();
            if (now - entry.getValue() > cacheLimit) {
                UUID uuid = entry.getKey();
                seen.remove();
                lastSeen.remove(uuid);
                names.remove(uuid);
            }
        }
    }

    /**
     * Called from the network handler when the server removes players from the
     * tab list. This is the reliable logout signal; polling the entity list
     * cannot distinguish a logout from a player leaving render distance.
     */
    public void onPlayerRemove(List<UUID> removed) {
        var mc = BytePhase.mc;
        // Player-list packets can be queued while connecting or disconnecting.
        // The network mixin deliberately hands this work to the client thread,
        // where the client world may already have been torn down.
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        for (UUID uuid : removed) {
            if (uuid.equals(mc.player.getUuid())) {
                continue;
            }
            Vec3d pos = lastSeen.get(uuid);
            Long seenAt = lastSeenAt.get(uuid);
            long now = System.currentTimeMillis();
            boolean recentlySeen = seenAt != null
                    && now - seenAt <= (long) (lastSeenTimeout.getValue() * 1000.0);
            if (pos != null && recentlySeen && !spots.containsKey(uuid)) {
                String name = names.getOrDefault(uuid, "player");
                // A server can report many joins/leaves over a long session.
                // Keep recent markers without allowing this cache to grow forever.
                while (spots.size() >= 256) {
                    UUID oldest = spots.keySet().iterator().next();
                    spots.remove(oldest);
                }
                spots.put(uuid, new Spot(name, pos, now));
            }
            names.remove(uuid);
            lastSeen.remove(uuid);
            lastSeenAt.remove(uuid);
        }
    }

    @Override
    public void onRender3D(net.minecraft.client.util.math.MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider consumers, float tickDelta) {
        boxes.clear();
        for (Spot spot : spots.values()) {
            boxes.add(spot.bounds);
        }
        EspRenderer.drawBoxes(boxes, color.getValue(), style.getValue());
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!showLabels.getValue()) {
            return;
        }
        var mc = BytePhase.mc;
        if (mc == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Spot spot : spots.values()) {
            double[] projected = RenderProjection.project(spot.pos.add(0, 1.0, 0));
            if (!RenderProjection.isOnScreen(projected, mc.getWindow().getScaledWidth(),
                    mc.getWindow().getScaledHeight())) {
                continue;
            }
            long minutes = Math.max(0, (now - spot.time) / 60000L);
            String text = spot.name + " (" + minutes + "m)";
            int width = mc.textRenderer.getWidth(text);
            int x = (int) projected[0] - width / 2;
            int y = (int) projected[1];
            if (!dev.bytephase.render.OverlayLabels.reserve(x - 2, y - 1, width + 4, 11)) continue;
            context.fill(x - 2, y - 1, x + width + 2, y + 9, 0x80000000);
            context.drawText(mc.textRenderer, text, x, y, 0xFF000000 | color.rgb(), true);
        }
    }
}
