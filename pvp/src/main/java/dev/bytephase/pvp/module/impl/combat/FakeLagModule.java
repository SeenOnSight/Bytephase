package dev.bytephase.pvp.module.impl.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import dev.bytephase.pvp.CombatIntegration;
import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.Stage;
import dev.bytephase.pvp.event.impl.PacketReceiveNettyEvent;
import dev.bytephase.pvp.event.impl.PacketSendEvent;
import dev.bytephase.pvp.event.impl.Render3DEvent;
import dev.bytephase.pvp.event.impl.Render2DEvent;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import dev.bytephase.pvp.module.setting.ModeSetting;
import dev.bytephase.pvp.module.setting.NumberSetting;
import dev.bytephase.pvp.module.setting.ColorSetting;
import dev.bytephase.pvp.mixin.AccessorLocalPlayer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class FakeLagModule extends ClientModule {
    private static final String DYNAMIC = "Dynamic";
    private static final String ALWAYS = "Always";
    private static final double AIM_CONE_DEGREES = 30.0;
    private static final double TOWARDS = 0.02;
    private final ModeSetting mode = this.addSetting("mode", new ModeSetting("Mode", "Dynamic", "Dynamic", "Always"));
    private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay (ms)", 100.0F, 10.0F, 1000.0F).step(10.0F));
    private final NumberSetting range = this.addSetting("range", new NumberSetting("Range", 6.0F, 3.0F, 12.0F).step(0.5F));
    private final BooleanSetting render = this.addSetting("render", new BooleanSetting("Show Server Position", true));
    private final BooleanSetting tether = this.addSetting("tether", new BooleanSetting("Show Tether", true));
    private final BooleanSetting worldLabel = this.addSetting("world-label", new BooleanSetting("Show World Label", true));
    private final BooleanSetting queueHud = this.addSetting("queue-hud", new BooleanSetting("Show Queue HUD", true));
    private final BooleanSetting throughWalls = this.addSetting("through-walls", new BooleanSetting("Through Walls", true));
    private final ColorSetting positionColor = this.addSetting("position-color", new ColorSetting("Position Color", 0xFFFFAA55));
    private final NumberSetting fillOpacity = this.addSetting("fill-opacity", new NumberSetting("Fill Opacity (%)", 12.0F, 0.0F, 50.0F).step(1.0F));
    private final Minecraft mc = Minecraft.getInstance();
    private final Deque<FakeLagModule.Held> held = new ArrayDeque<>();
    private boolean releasing;
    private ClientPacketListener heldFor;
    private volatile Vec3 serverFeet;
    private volatile boolean lagNow;
    private LocalPlayer sessionPlayer;
    private ClientLevel sessionLevel;
    private ClientPacketListener sessionConnection;

    public FakeLagModule() {
        super(
            "fake-lag",
            "Fake Lag",
            "Holds your outgoing packets for a moment so the server sees you a little behind. Dynamic: only while you walk at the player you aim at.",
            ModuleCategory.COMBAT
        );
        this.range.visibleWhen(() -> this.mode.is("Dynamic"));
        this.tether.visibleWhen(this.render::get);
        this.worldLabel.visibleWhen(this.render::get);
        this.throughWalls.visibleWhen(this.render::get);
        this.positionColor.visibleWhen(() -> this.render.get() || this.queueHud.get());
        this.fillOpacity.visibleWhen(this.render::get);
    }

    @Override
    protected void onEnable() {
        this.drop();
        this.syncSession();
        this.on(TickEvent.TYPE, Stage.PRE, event -> this.tick());
        this.on(Render3DEvent.TYPE, Stage.POST, event -> {
            var services = CombatIntegration.services();
            if (services == null || !services.supportsWorldOverlays()) this.renderWorldOverlay();
        });
    }

    @Override
    protected void onDisable() {
        this.lagNow = false;
        this.releaseAll();
        this.drop();
    }

    @EventHandler
    public void onPacketSend(PacketSendEvent event) {
        if (!this.releasing) {
            if (this.mc.player == null || this.mc.level == null || this.mc.getConnection() == null) {
                this.drop();
                return;
            }
            this.syncSession();
            Packet<?> packet = event.packet;
            if (this.lagNow && !acts(packet)) {
                synchronized (this.held) {
                    if (this.held.isEmpty()) {
                        this.heldFor = this.mc.getConnection();
                    }

                    this.held.addLast(new FakeLagModule.Held(packet, System.currentTimeMillis()));
                }

                event.setCancelled(true);
            } else {
                this.releaseAll();
                this.track(packet);
            }
        }
    }

    @EventHandler
    public void onPacketReceive(PacketReceiveNettyEvent event) {
        if (event.packet instanceof ClientboundPlayerPositionPacket) {
            this.mc.execute(this::releaseAll);
        }
    }

    private void tick() {
        if (this.mc.getConnection() != null && this.mc.player != null && this.mc.level != null) {
            this.syncSession();
            this.lagNow = this.lagging();
            if (!this.lagNow) {
                this.releaseAll();
            } else {
                long due = System.currentTimeMillis() - Math.round(this.delay.get());

                while (true) {
                    FakeLagModule.Held next;
                    synchronized (this.held) {
                        next = this.held.peekFirst();
                        if (next == null || next.at() > due) {
                            return;
                        }

                        this.held.pollFirst();
                    }

                    this.send(next.packet());
                }
            }
        } else {
            this.lagNow = false;
            this.drop();
        }
    }

    private boolean lagging() {
        LocalPlayer player = this.mc.player;
        return player != null && this.mc.getConnection() != null && player.isAlive() ? this.mode.is("Always") || this.walkingAtTarget(player) : false;
    }

    private boolean walkingAtTarget(LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 motion = player.getDeltaMovement();
        double reach = this.range.get();

        for (Player other : this.mc.level.players()) {
            if (other != player && MeleeAttacks.isEnemy(player, other) && !(other.distanceTo(player) > reach)) {
                Vec3 to = other.getBoundingBox().getCenter().subtract(eye).normalize();
                if (!(Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, look.dot(to))))) > 30.0) && motion.x * to.x + motion.z * to.z > 0.02) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean acts(Packet<?> packet) {
        return packet instanceof ServerboundInteractPacket
            || packet instanceof ServerboundUseItemOnPacket
            || packet instanceof ServerboundUseItemPacket
            || packet instanceof ServerboundPlayerActionPacket
            || packet instanceof ServerboundSwingPacket
            || packet instanceof ServerboundContainerClickPacket
            || packet instanceof ServerboundContainerButtonClickPacket
            || packet instanceof ServerboundContainerClosePacket
            || packet instanceof ServerboundChatPacket
            || packet instanceof ServerboundChatCommandPacket
            || packet instanceof ServerboundChatCommandSignedPacket
            || packet instanceof ServerboundCommandSuggestionPacket
            || packet instanceof ServerboundSignUpdatePacket
            || packet instanceof ServerboundEditBookPacket
            || packet instanceof ServerboundPlaceRecipePacket
            || packet instanceof ServerboundSelectTradePacket
            || packet instanceof ServerboundSetCreativeModeSlotPacket
            || packet instanceof ServerboundPickItemFromBlockPacket
            || packet instanceof ServerboundPickItemFromEntityPacket
            || packet instanceof ServerboundAcceptTeleportationPacket;
    }

    private void releaseAll() {
        while (true) {
            FakeLagModule.Held next;
            synchronized (this.held) {
                next = this.held.pollFirst();
            }

            if (next == null) {
                return;
            }

            this.send(next.packet());
        }
    }

    private void drop() {
        synchronized (this.held) {
            this.held.clear();
        }

        this.serverFeet = null;
        this.lagNow = false;
        this.heldFor = null;
        this.sessionPlayer = null;
        this.sessionLevel = null;
        this.sessionConnection = null;
    }

    private void syncSession() {
        LocalPlayer player = this.mc.player;
        ClientPacketListener connection = this.mc.getConnection();
        if (player == null || this.mc.level == null || connection == null) return;
        if (player != this.sessionPlayer || this.mc.level != this.sessionLevel || connection != this.sessionConnection) {
            this.drop();
            this.sessionPlayer = player;
            this.sessionLevel = this.mc.level;
            this.sessionConnection = connection;
            AccessorLocalPlayer sent = (AccessorLocalPlayer) player;
            this.serverFeet = player.tickCount <= 1 ? player.position()
                : new Vec3(sent.pvp$lastSentX(), sent.pvp$lastSentY(), sent.pvp$lastSentZ());
        }
    }

    private void send(Packet<?> packet) {
        ClientPacketListener connection = this.mc.getConnection();
        if (connection != null && (connection == this.heldFor || this.heldFor == null)) {
            this.releasing = true;

            try {
                connection.send(packet);
            } finally {
                this.releasing = false;
            }

            this.track(packet);
        } else {
            this.drop();
        }
    }

    private void track(Packet<?> packet) {
        if (packet instanceof ServerboundMovePlayerPacket move && move.hasPosition() && this.mc.player != null) {
            this.serverFeet = new Vec3(move.getX(this.mc.player.getX()), move.getY(this.mc.player.getY()), move.getZ(this.mc.player.getZ()));
        } else if (packet instanceof ServerboundAcceptTeleportationPacket && this.mc.player != null) {
            this.serverFeet = this.mc.player.position();
        }
    }

    public void renderWorldOverlay() {
        LocalPlayer player = this.mc.player;
        Vec3 feet = this.serverFeet;
        if (this.render.get() && this.sameSession() && feet != null && this.queue().count() > 0) {
            double half = player.getBbWidth() / 2.0;
            AABB box = new AABB(feet.x - half, feet.y, feet.z - half,
                feet.x + half, feet.y + player.getBbHeight(), feet.z + half);
            int outline = this.positionColor.argb();
            var services = CombatIntegration.services();
            if (services != null && services.supportsWorldOverlays()) {
                // Avoid tinting the entire first-person view while the ghost
                // and local player occupy almost the same bounding box.
                int fill = player.position().distanceToSqr(feet) >= 0.04
                    ? this.positionColor.argbWithAlpha(Math.min(outline >>> 24,
                        Math.round(this.fillOpacity.get() * 2.55F))) : 0;
                services.drawWorldBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                    fill, outline, this.throughWalls.get());
                if (this.tether.get()) {
                    Vec3 local = player.getPosition(this.mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
                    services.drawWorldLine(local.x, local.y + 0.04, local.z,
                        feet.x, feet.y + 0.04, feet.z,
                        this.positionColor.argbWithAlpha(Math.min(outline >>> 24, 180)), this.throughWalls.get());
                }
            } else {
                Gizmos.cuboid(box, GizmoStyle.stroke(outline, 2.0F));
            }
        }
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (!this.sameSession()) return;
        QueueView queue = this.queue();
        Vec3 feet = this.serverFeet;
        double offset = feet == null ? 0.0 : this.mc.player.position().distanceTo(feet);
        int color = this.positionColor.argb();
        var services = CombatIntegration.services();
        if (this.render.get() && this.worldLabel.get() && feet != null && queue.count() > 0 && services != null) {
            double[] point = services.project(feet.x, feet.y + this.mc.player.getBbHeight() + 0.2, feet.z);
            if (point != null) {
                String text = String.format(Locale.ROOT, "Server position · %.2f m", offset);
                int width = this.mc.font.width(text) + 10;
                int x = (int)Math.round(point[0] - width / 2.0), y = (int)Math.round(point[1] - 13);
                if (services.reserveLabel(x, y, width, 15)) {
                    event.graphics.fill(x, y, x + width, y + 15, 0xB0101020);
                    event.graphics.drawString(this.mc.font, text, x + 5, y + 3, color, false);
                }
            }
        }
        if (!this.queueHud.get()) return;
        String text = queue.count() == 0 ? "FakeLag · ready"
            : String.format(Locale.ROOT, "FakeLag · %d ms · %d packets · %.2f m",
                queue.ageMs(), queue.count(), offset);
        int screenWidth = this.mc.getWindow().getGuiScaledWidth();
        text = this.mc.font.plainSubstrByWidth(text, Math.max(1, screenWidth - 26));
        int width = this.mc.font.width(text) + 12;
        int x = 8, y = Math.max(8, this.mc.getWindow().getGuiScaledHeight() - 57);
        event.graphics.fill(x, y, x + width, y + 21, 0xBD101020);
        event.graphics.drawString(this.mc.font, text, x + 6, y + 4, queue.count() == 0 ? 0xFFA6A9D6 : color, false);
        event.graphics.fill(x + 5, y + 16, x + width - 5, y + 18, 0x32FFFFFF);
        float progress = Math.min(1.0F, queue.ageMs() / Math.max(1.0F, this.delay.get()));
        int end = x + 5 + Math.round((width - 10) * progress);
        if (end > x + 5) event.graphics.fill(x + 5, y + 16, end, y + 18, color);
    }

    private boolean sameSession() {
        return this.mc.player != null && this.mc.level != null
            && this.mc.player == this.sessionPlayer && this.mc.level == this.sessionLevel
            && this.mc.getConnection() == this.sessionConnection;
    }

    private QueueView queue() {
        synchronized (this.held) {
            Held first = this.held.peekFirst();
            return new QueueView(this.held.size(), first == null ? 0
                : Math.max(0, System.currentTimeMillis() - first.at()));
        }
    }

    private record QueueView(int count, long ageMs) { }

    @Environment(EnvType.CLIENT)
    private record Held(Packet<?> packet, long at) {
    }
}
