package dev.bytephase.module.misc;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

public final class VisualRange extends Module {
    private final BooleanSetting announceLeave = add(new BooleanSetting("AnnounceLeave", true));
    private final NumberSetting range = add(new NumberSetting("Range", 64.0, 8.0, 256.0, 1.0));
    private Set<UUID> known = new HashSet<>();
    private Set<UUID> current = new HashSet<>();
    // Names are resolved once per player, not every tick for everyone in range.
    private final Map<UUID, String> names = new HashMap<>();
    private World lastWorld;

    public VisualRange() {
        super("VisualRange", Category.MISC, "Announces players entering your visual range");
    }

    @Override
    protected void onEnable() {
        known.clear();
        current.clear();
        names.clear();
        lastWorld = null;
        // Seed the list so enabling the module does not announce everyone
        // already visible.
        snapshotInto(known);
    }

    @Override
    protected void onDisable() {
        known.clear();
        current.clear();
        names.clear();
        lastWorld = null;
    }

    private void announce(String message) {
        var mc = BytePhase.mc;
        if (mc == null) {
            return;
        }
        mc.inGameHud.getChatHud().addMessage(Text.literal("[BytePhase] " + message));
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        snapshotInto(current);
        if (mc.world != lastWorld) {
            // Joining a world or changing dimension should not spam the chat
            // with every player who is already visible.
            lastWorld = mc.world;
            known.clear();
            known.addAll(current);
            return;
        }
        for (UUID id : current) {
            if (!known.contains(id)) {
                announce(names.get(id) + " has entered your visual range");
            }
        }
        if (announceLeave.getValue()) {
            for (UUID id : known) {
                if (!current.contains(id)) {
                    announce(names.getOrDefault(id, "A player") + " has left your visual range");
                }
            }
        }
        names.keySet().retainAll(current);
        Set<UUID> swap = known;
        known = current;
        current = swap;
    }

    private void snapshotInto(Set<UUID> destination) {
        destination.clear();
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        double rangeSq = range.getValue() * range.getValue();
        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.squaredDistanceTo(mc.player) > rangeSq) {
                continue;
            }
            UUID id = player.getUuid();
            destination.add(id);
            names.computeIfAbsent(id, ignored -> player.getName().getString());
        }
    }
}
