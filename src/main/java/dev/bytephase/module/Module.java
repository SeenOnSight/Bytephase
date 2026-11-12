package dev.bytephase.module;

import dev.bytephase.BytePhase;
import dev.bytephase.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;

public abstract class Module {
    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();

    private volatile boolean enabled;

    protected Module(String name, Category category, String description) {
        this.name = name;
        this.category = category;
        this.description = description;
    }

    protected <T extends Setting<?>> T add(T setting) {
        settings.add(setting);
        return setting;
    }

    public String getName() {
        return name;
    }

    /** Text displayed by the enabled-module HUD. */
    public String getHudText() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        if (enabled == value) {
            return;
        }
        enabled = value;
        try {
            if (enabled) {
                onEnable();
            } else {
                onDisable();
            }
        } catch (Exception e) {
            enabled = false;
            BytePhase.LOGGER.error("{} failed to {}", name, value ? "enable" : "disable", e);
            if (value) {
                try {
                    onDisable();
                } catch (Exception cleanupError) {
                    e.addSuppressed(cleanupError);
                    BytePhase.LOGGER.error("{} failed to clean up after enable", name, cleanupError);
                }
            }
        }
        if (BytePhase.modules != null) {
            BytePhase.modules.moduleStateChanged();
        }
        if (BytePhase.config != null) {
            BytePhase.config.requestSave();
        }
        dev.bytephase.module.misc.Notifications.onToggled(this);
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    /**
     * Restores any global client state without changing the configured enabled
     * state. Called for enabled modules when the client is shutting down.
     */
    public final void shutdown() {
        if (!enabled) {
            return;
        }
        try {
            onDisable();
        } catch (Exception e) {
            BytePhase.LOGGER.error("{} failed to shut down cleanly", name, e);
        }
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    /** Called every client tick while enabled. */
    public void onTick() {
    }

    /** Called every client tick regardless of enabled state. */
    public void onTickAlways() {
    }

    /** Runs on the client thread before vanilla acknowledges a server position correction. */
    public void onServerCorrection() {
    }

    /**
     * Whether this module reads {@code EntitySnapshot}. The snapshot is only
     * rebuilt while at least one enabled module actually needs it.
     */
    public boolean usesEntitySnapshot() {
        return false;
    }

    /** Whether this module's enabled HUD work consumes the current projection matrices. */
    public boolean usesProjection() {
        return false;
    }

    /** Called each frame while the in-game HUD is rendering, when enabled. */
    public void onRender2D(DrawContext context, float tickDelta) {
    }

    /**
     * Called once per rendered frame while enabled, before any world-render
     * setup. Unlike {@link #onRender3D} it costs nothing extra to override, so
     * logic that must run between client ticks belongs here.
     */
    public void onFrame() {
    }

    /** Called after world rendering, matrices are camera-relative, when enabled. */
    public void onRender3D(MatrixStack matrices, VertexConsumerProvider consumers, float tickDelta) {
    }

    /**
     * Called immediately before the attack packet is sent, while the client is
     * still allowed to change its held item or sprint state for this hit.
     */
    public void onBeforeAttack(PlayerEntity self, Entity target) {
    }

    /** Called whenever the local player attacks an entity. */
    public void onAttack(PlayerEntity self, Entity target) {
    }

    /** @return true to cancel the packet. */
    public boolean onPacketSend(Packet<?> packet) {
        return false;
    }

    /**
     * Called for every inbound packet while enabled.
     *
     * @return true to drop the packet before the network handler sees it.
     */
    public boolean onPacketReceive(Packet<?> packet) {
        return false;
    }
}
