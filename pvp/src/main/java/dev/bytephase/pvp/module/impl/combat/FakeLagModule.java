package dev.bytephase.pvp.module.impl.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.Stage;
import dev.bytephase.pvp.event.impl.PacketReceiveNettyEvent;
import dev.bytephase.pvp.event.impl.PacketSendEvent;
import dev.bytephase.pvp.event.impl.Render3DEvent;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import dev.bytephase.pvp.module.setting.ModeSetting;
import dev.bytephase.pvp.module.setting.NumberSetting;
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
    private static final double AIM_CONE_COSINE = Math.cos(Math.toRadians(AIM_CONE_DEGREES));
    private static final int MAX_HELD_PACKETS = 1024;
    private static final int BOX_COLOR = -43691;
    private final ModeSetting mode = this.addSetting("mode", new ModeSetting("Mode", "Dynamic", "Dynamic", "Always"));
    private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay (ms)", 100.0F, 10.0F, 1000.0F).step(10.0F));
    private final NumberSetting range = this.addSetting("range", new NumberSetting("Range", 6.0F, 3.0F, 12.0F).step(0.5F));
    private final BooleanSetting render = this.addSetting("render", new BooleanSetting("Show Server Position", true));
    private final Minecraft mc = Minecraft.getInstance();
    private final Deque<FakeLagModule.Held> held = new ArrayDeque<>();
    private final ThreadLocal<Boolean> releasing = ThreadLocal.withInitial(() -> false);
    private ClientPacketListener heldFor;
    private LocalPlayer sessionPlayer;
    private ClientLevel sessionLevel;
    private ClientPacketListener sessionConnection;
    private volatile Vec3 serverFeet;
    private volatile boolean lagNow;

    public FakeLagModule() {
        super(
            "fake-lag",
            "Fake Lag",
            "Holds your outgoing packets for a moment so the server sees you a little behind. Dynamic: only while you walk at the player you aim at.",
            ModuleCategory.COMBAT
        );
        this.range.visibleWhen(() -> this.mode.is("Dynamic"));
    }

    @Override
    protected void onEnable() {
        synchronized (this.held) {
            this.held.clear();
        }

        this.serverFeet = null;
        this.on(TickEvent.TYPE, Stage.PRE, event -> this.tick());
        this.on(Render3DEvent.TYPE, Stage.POST, event -> this.render());
    }

    @Override
    protected void onDisable() {
        this.lagNow = false;
        this.releaseAll();
    }

    @EventHandler
    public void onPacketSend(PacketSendEvent event) {
        if (!this.releasing.get()) {
            if (this.mc.player == null || this.mc.level == null || this.mc.getConnection() == null) {
                this.drop();
                return;
            }
            this.syncSession();
            Packet<?> packet = event.packet;
            if (this.lagNow && !acts(packet)) {
                boolean full;
                synchronized (this.held) {
                    full = this.held.size() >= MAX_HELD_PACKETS;
                }
                if (full) this.releaseAll();
                synchronized (this.held) {
                    if (this.held.isEmpty()) {
                        this.heldFor = this.mc.getConnection();
                    }

                    this.held.addLast(new FakeLagModule.Held(packet, System.currentTimeMillis(),
                            this.mc.getConnection(), this.mc.player, this.mc.level));
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

                    this.send(next);
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
                if (look.dot(to) >= AIM_CONE_COSINE && motion.x * to.x + motion.z * to.z > TOWARDS) {
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

            this.send(next);
        }
    }

    private void drop() {
        synchronized (this.held) {
            this.held.clear();
            this.serverFeet = null;
            this.lagNow = false;
            this.heldFor = null;
            this.sessionPlayer = null;
            this.sessionLevel = null;
            this.sessionConnection = null;
        }
    }

    private void syncSession() {
        synchronized (this.held) {
            if (this.mc.player != this.sessionPlayer || this.mc.level != this.sessionLevel
                    || this.mc.getConnection() != this.sessionConnection) {
                this.drop();
                this.sessionPlayer = this.mc.player;
                this.sessionLevel = this.mc.level;
                this.sessionConnection = this.mc.getConnection();
            }
        }
    }

    private void send(Held next) {
        ClientPacketListener connection = this.mc.getConnection();
        if (connection != null && connection == next.connection()
                && this.mc.player == next.player() && this.mc.level == next.level()) {
            boolean alreadyReleasing = this.releasing.get();
            this.releasing.set(true);

            try {
                connection.send(next.packet());
            } finally {
                this.releasing.set(alreadyReleasing);
            }

            this.track(next.packet());
        }
    }

    private void track(Packet<?> packet) {
        if (packet instanceof ServerboundMovePlayerPacket move && move.hasPosition() && this.mc.player != null) {
            this.serverFeet = new Vec3(move.getX(this.mc.player.getX()), move.getY(this.mc.player.getY()), move.getZ(this.mc.player.getZ()));
        }
    }

    private void render() {
        LocalPlayer player = this.mc.player;
        Vec3 feet = this.serverFeet;
        boolean holding;
        synchronized (this.held) {
            holding = !this.held.isEmpty();
        }

        if (this.render.get() && player != null && feet != null && holding) {
            double half = player.getBbWidth() / 2.0;
            Gizmos.cuboid(
                new AABB(feet.x - half, feet.y, feet.z - half, feet.x + half, feet.y + player.getBbHeight(), feet.z + half), GizmoStyle.stroke(-43691, 2.0F)
            );
        }
    }

    @Environment(EnvType.CLIENT)
    private record Held(Packet<?> packet, long at, ClientPacketListener connection,
            LocalPlayer player, ClientLevel level) {
    }
}
