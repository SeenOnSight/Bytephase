package dev.bytephase.pvp.module.impl.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;
import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.Stage;
import dev.bytephase.pvp.event.impl.PacketReceiveNettyEvent;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import dev.bytephase.pvp.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.player.Player;

@Environment(EnvType.CLIENT)
public final class KnockbackDelayModule extends ClientModule {
    private static final double FIGHT_RANGE = 6.0;
    private final NumberSetting chance = this.addSetting("chance", new NumberSetting("Chance (%)", 40.0F, 0.0F, 100.0F).step(5.0F));
    private final NumberSetting airMin = this.addSetting("air-min", new NumberSetting("Air Delay Min (ms)", 50.0F, 0.0F, 500.0F).step(10.0F));
    private final NumberSetting airMax = this.addSetting("air-max", new NumberSetting("Air Delay Max (ms)", 100.0F, 0.0F, 500.0F).step(10.0F));
    private final NumberSetting groundMin = this.addSetting("ground-min", new NumberSetting("Ground Delay Min (ms)", 200.0F, 0.0F, 1000.0F).step(10.0F));
    private final NumberSetting groundMax = this.addSetting("ground-max", new NumberSetting("Ground Delay Max (ms)", 250.0F, 0.0F, 1000.0F).step(10.0F));
    private final BooleanSetting waterCheck = this.addSetting("water-check", new BooleanSetting("Not In Water", false));
    private final BooleanSetting onlyFights = this.addSetting("only-fights", new BooleanSetting("Only Near Enemies", true));
    private final Minecraft mc = Minecraft.getInstance();
    private final Deque<KnockbackDelayModule.Held> held = new ArrayDeque<>();
    private ClientPacketListener heldFor;
    private volatile boolean fightNear;

    public KnockbackDelayModule() {
        super(
            "knockback-delay",
            "Knockback Delay",
            "Takes some hits' knockback a random moment late: a little in the air, longer on the ground.",
            ModuleCategory.COMBAT
        );
    }

    @Override
    protected void onEnable() {
        synchronized (this.held) {
            this.held.clear();
        }

        this.on(TickEvent.TYPE, Stage.PRE, event -> {
            this.fightNear = this.mc.player != null && this.enemyNear(this.mc.player);
            this.applyDue(false);
        });
    }

    @Override
    protected void onDisable() {
        this.mc.execute(() -> this.applyDue(true));
    }

    @EventHandler
    public void onPacketReceive(PacketReceiveNettyEvent event) {
        LocalPlayer player = this.mc.player;
        if (player != null && pushesMe(event.packet, player)) {
            boolean waiting;
            synchronized (this.held) {
                waiting = !this.held.isEmpty();
            }

            if (waiting || this.delays(player)) {
                boolean ground = player.onGround();
                long wait = waiting ? 0L : randomBetween(ground ? this.groundMin.get() : this.airMin.get(), ground ? this.groundMax.get() : this.airMax.get());
                synchronized (this.held) {
                    if (this.held.isEmpty()) {
                        this.heldFor = player.connection;
                    }

                    long due = Math.max(System.currentTimeMillis() + wait, this.held.isEmpty() ? 0L : this.held.peekLast().due());
                    this.held.addLast(new KnockbackDelayModule.Held(event.packet, due));
                }

                event.setCancelled(true);
            }
        }
    }

    private static boolean pushesMe(Packet<?> packet, LocalPlayer player) {
        return packet instanceof ClientboundSetEntityMotionPacket motion
            ? motion.getId() == player.getId()
            : packet instanceof ClientboundExplodePacket explode && explode.playerKnockback().isPresent();
    }

    private boolean delays(LocalPlayer player) {
        if (this.waterCheck.get() && player.isInWater()) {
            return false;
        } else {
            return this.onlyFights.get() && !this.fightNear ? false : ThreadLocalRandom.current().nextDouble(100.0) < this.chance.get();
        }
    }

    private boolean enemyNear(LocalPlayer player) {
        for (Player other : player.level().players()) {
            if (other != player && other.distanceTo(player) <= 6.0 && MeleeAttacks.isEnemy(player, other)) {
                return true;
            }
        }

        return false;
    }

    private static long randomBetween(float a, float b) {
        long low = Math.round(Math.min(a, b));
        long high = Math.round(Math.max(a, b));
        return high <= low ? low : ThreadLocalRandom.current().nextLong(low, high + 1L);
    }

    private void applyDue(boolean all) {
        ClientPacketListener connection = this.mc.getConnection();
        long now = System.currentTimeMillis();

        while (true) {
            KnockbackDelayModule.Held next;
            synchronized (this.held) {
                next = this.held.peekFirst();
                if (next == null || !all && next.due() > now) {
                    return;
                }

                this.held.pollFirst();
                if (connection == null || connection != this.heldFor) {
                    this.held.clear();
                    return;
                }
            }

            ((Packet<ClientPacketListener>)next.packet()).handle(connection);
        }
    }

    @Environment(EnvType.CLIENT)
    private record Held(Packet<?> packet, long due) {
    }
}
