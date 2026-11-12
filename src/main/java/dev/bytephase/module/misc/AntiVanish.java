package dev.bytephase.module.misc;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Keeps track of players who vanish.
 *
 * <p>Derived from Anubis' {@code AntiVanishModule}. Anubis combines packet,
 * sound and particle heuristics; this port keeps the reliable part: a player
 * who is still in the tab list but no longer in the world has been hidden by
 * the server. Their last position is kept as a ghost and an alert is sent, so
 * the client stops relying on a vanished staff member being gone.</p>
 */
public final class AntiVanish extends Module {
    private static final long ALERT_COOLDOWN_MS = 1500L;
    private static final long RECENT_SEEN_MS = 4000L;
    private static final double NEARBY_DISTANCE_SQ = 32.0 * 32.0;

    private static final class Ghost {
        final String name;
        final Vec3d pos;
        final Box bounds;
        final long since;

        Ghost(String name, Vec3d pos, long since) {
            this.name = name;
            this.pos = pos;
            this.bounds = new Box(pos.x - 0.3, pos.y, pos.z - 0.3,
                    pos.x + 0.3, pos.y + 1.95, pos.z + 0.3);
            this.since = since;
        }
    }

    private final BooleanSetting alerts = add(new BooleanSetting("Alerts", true));
    private final BooleanSetting ghosts = add(new BooleanSetting("Ghosts", true));
    private final BooleanSetting sound = add(new BooleanSetting("Sound", true));
    private final NumberSetting timeout = add(new NumberSetting("Timeout", 120.0, 10.0, 600.0, 1.0));
    private final ModeSetting style = add(new ModeSetting("Style",
            EspRenderer.STYLES, "Highlight"));
    private final ColorSetting color = add(new ColorSetting("Color", 0xFF6466E9));

    private final Map<UUID, Ghost> vanished = new LinkedHashMap<>();
    private final Map<UUID, Vec3d> lastSeen = new HashMap<>();
    private final Map<UUID, Long> lastSeenAt = new HashMap<>();
    private final Set<UUID> visiblePlayers = new HashSet<>();
    private final List<Box> boxes = new ArrayList<>();
    private int cooldown;
    private long lastAlert;
    private long lastSound;
    private ClientWorld lastWorld;

    public AntiVanish() {
        super("AntiVanish", Category.MISC, "Tracks players who vanish from the world");
    }

    @Override
    public boolean usesProjection() {
        return true;
    }

    @Override
    protected void onEnable() {
        clear();
        cooldown = 0;
        lastAlert = 0L;
        lastSound = 0L;
        lastWorld = null;
    }

    @Override
    protected void onDisable() {
        clear();
        lastWorld = null;
    }

    private void clear() {
        vanished.clear();
        lastSeen.clear();
        lastSeenAt.clear();
        visiblePlayers.clear();
        boxes.clear();
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        if (mc.world != lastWorld) {
            clear();
            lastWorld = mc.world;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        cooldown = 5;

        long now = System.currentTimeMillis();
        visiblePlayers.clear();
        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            UUID uuid = player.getUuid();
            visiblePlayers.add(uuid);
            lastSeen.put(uuid, player.getEntityPos());
            lastSeenAt.put(uuid, now);
            vanished.remove(uuid);
        }

        if (mc.getNetworkHandler() == null) {
            return;
        }
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            UUID uuid = entry.getProfile().id();
            String name = entry.getProfile().name();
            if (uuid == null || uuid.equals(mc.player.getUuid()) || visiblePlayers.contains(uuid)) {
                continue;
            }
            if (vanished.containsKey(uuid)) {
                continue;
            }
            Vec3d pos = lastSeen.get(uuid);
            Long seenAt = lastSeenAt.get(uuid);
            if (pos == null || seenAt == null || now - seenAt > RECENT_SEEN_MS) {
                continue;
            }
            // A player who simply walked out of entity-tracking range is not
            // necessarily vanished. Only alert when we saw them close by very
            // recently; that is the signature of a staff member toggling
            // vanish, not of normal render-distance churn.
            if (pos.squaredDistanceTo(mc.player.getEntityPos()) > NEARBY_DISTANCE_SQ) {
                continue;
            }
            // Keep ghost rendering bounded if a server produces a large or
            // rapidly changing tab list.
            while (vanished.size() >= 256) {
                UUID oldest = vanished.keySet().iterator().next();
                vanished.remove(oldest);
            }
            vanished.put(uuid, new Ghost(name == null ? "player" : name, pos, now));
            alert(name, pos);
        }

        Iterator<Map.Entry<UUID, Long>> seen = lastSeenAt.entrySet().iterator();
        while (seen.hasNext()) {
            Map.Entry<UUID, Long> entry = seen.next();
            if (now - entry.getValue() > RECENT_SEEN_MS) {
                UUID uuid = entry.getKey();
                seen.remove();
                lastSeen.remove(uuid);
            }
        }

        long limit = (long) (timeout.getValue() * 1000.0);
        Iterator<Map.Entry<UUID, Ghost>> iterator = vanished.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Ghost> entry = iterator.next();
            boolean stillListed = mc.getNetworkHandler().getPlayerListEntry(entry.getKey()) != null;
            if (!stillListed || now - entry.getValue().since > limit) {
                iterator.remove();
            }
        }
    }

    private void alert(String name, Vec3d pos) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (sound.getValue() && now - lastSound >= ALERT_COOLDOWN_MS) {
            lastSound = now;
            mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.6f, 1.4f);
        }
        if (!alerts.getValue() || now - lastAlert < ALERT_COOLDOWN_MS) {
            return;
        }
        lastAlert = now;
        String label = name == null ? "A player" : name;
        mc.inGameHud.getChatHud().addMessage(Text.literal(String.format(
                "[BytePhase] %s vanished near %.0f %.0f %.0f",
                label, pos.x, pos.y, pos.z)).formatted(Formatting.RED));
    }

    @Override
    public void onRender3D(MatrixStack matrices,
                           net.minecraft.client.render.VertexConsumerProvider consumers,
                           float tickDelta) {
        if (!ghosts.getValue() || vanished.isEmpty()) {
            return;
        }
        boxes.clear();
        for (Ghost ghost : vanished.values()) {
            boxes.add(ghost.bounds);
        }
        EspRenderer.drawBoxes(boxes, color.getValue(), style.getValue());
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!ghosts.getValue() || vanished.isEmpty()) {
            return;
        }
        var mc = BytePhase.mc;
        if (mc == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Ghost ghost : vanished.values()) {
            double[] projected = RenderProjection.project(ghost.pos.add(0, 2.1, 0));
            if (!RenderProjection.isOnScreen(projected, mc.getWindow().getScaledWidth(),
                    mc.getWindow().getScaledHeight())) {
                continue;
            }
            long seconds = Math.max(0, (now - ghost.since) / 1000L);
            String label = ghost.name + " vanished " + seconds + "s";
            int width = mc.textRenderer.getWidth(label);
            int x = (int) projected[0] - width / 2;
            int y = (int) projected[1];
            context.fill(x - 2, y - 1, x + width + 2, y + 10, 0x90000000);
            context.drawTextWithShadow(mc.textRenderer, label, x, y, 0xFF000000 | color.rgb());
        }
    }
}
