package dev.bytephase.pvp.module.impl.combat;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Arrays;
import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.EventPriority;
import dev.bytephase.pvp.event.impl.PacketReceiveEvent;
import dev.bytephase.pvp.event.impl.PacketSendEvent;
import dev.bytephase.pvp.event.impl.PlayerAttackEntityEvent;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.impl.combat.crystal.Crystals;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket.Handler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class CrystalOptimizerModule extends ClientModule {
    private static final int SAMPLE_CAPACITY = 64;
    private final ItemStack[] heldSamples = new ItemStack[64];
    private final int[] sampleTicks = new int[64];
    private int sampleCursor;
    private int sampleCount;
    private int ticks;
    private int trackedSince;
    private LocalPlayer trackedPlayer;
    private ClientLevel trackedLevel;
    private boolean attackSent;
    private EndCrystal pendingRemoval;
    private final CrystalOptimizerModule.AttackProbe attackProbe = new CrystalOptimizerModule.AttackProbe();
    private final Int2ObjectMap<CrystalOptimizerModule.Ghost> ghosts = new Int2ObjectOpenHashMap<>();
    private final IntSet stubborn = new IntOpenHashSet();
    private final IntSet serverRemoved = new IntOpenHashSet();

    public CrystalOptimizerModule() {
        super("crystal-optimizer", "Crystal Optimizer", "Removes crystals client-side as soon as your hit breaks them.", ModuleCategory.COMBAT);
    }

    @Override
    protected void onEnable() {
        var other = dev.bytephase.pvp.module.ModuleRegistry.get(MarlowOptimizerModule.class);
        if (other != null && other.isEnabled()) other.setEnabled(false);
        this.reset();
    }

    @Override
    protected void onDisable() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level == this.trackedLevel) {
            for (CrystalOptimizerModule.Ghost ghost : this.ghosts.values()) {
                revive(level, ghost.crystal());
            }
        }

        this.reset();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTick(TickEvent event) {
        this.attackSent = false;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player != null && level != null) {
            if (player != this.trackedPlayer || level != this.trackedLevel) {
                this.reset();
                this.trackedPlayer = player;
                this.trackedLevel = level;
            }

            if (this.pendingRemoval != null && !this.pendingRemoval.isRemoved()) {
                level.removeEntity(this.pendingRemoval.getId(), RemovalReason.DISCARDED);
                this.ghosts.put(this.pendingRemoval.getId(), new CrystalOptimizerModule.Ghost(this.pendingRemoval, this.ticks));
            }

            this.pendingRemoval = null;
            this.ticks++;
            this.serverRemoved.removeIf(id -> level.getEntity(id) == null);
            this.sample(player.getMainHandItem());
            this.restoreGhosts(mc, player, level);
        } else {
            if (this.trackedPlayer != null || this.sampleCount > 0) {
                this.reset();
            }
        }
    }

    @EventHandler
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.packet instanceof ClientboundRemoveEntitiesPacket remove) {
            for (int id : remove.getEntityIds()) {
                this.ghosts.remove(id);
                this.stubborn.remove(id);
                if (this.pendingRemoval != null && this.pendingRemoval.getId() == id) {
                    this.pendingRemoval = null;
                }

                if (this.trackedLevel != null && this.trackedLevel.getEntity(id) instanceof EndCrystal) {
                    this.serverRemoved.add(id);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPacketSend(PacketSendEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            if (event.packet instanceof ServerboundInteractPacket interact) {
                this.attackSent = this.attackProbe.isAttack(interact);
            } else if (event.packet instanceof ServerboundSetCarriedItemPacket carried) {
                LocalPlayer player = mc.player;
                if (player != null && player == this.trackedPlayer && Inventory.isHotbarSlot(carried.getSlot())) {
                    this.sample(player.getInventory().getItem(carried.getSlot()));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(PlayerAttackEntityEvent event) {
        boolean sent = this.attackSent;
        this.attackSent = false;
        if (sent && event.target instanceof EndCrystal crystal) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            ClientLevel level = mc.level;
            if (player != null && level != null && player == this.trackedPlayer && crystal.level() == level) {
                if (!this.stubborn.contains(crystal.getId())
                    && !this.serverRemoved.contains(crystal.getId())
                    && this.breaksOnServer(mc, player, level, crystal)) {
                    this.pendingRemoval = crystal;
                }
            }
        }
    }

    private void restoreGhosts(Minecraft mc, LocalPlayer player, ClientLevel level) {
        if (!this.ghosts.isEmpty()) {
            int window = Crystals.confirmWindow(mc, player);
            ObjectIterator<CrystalOptimizerModule.Ghost> iterator = this.ghosts.values().iterator();

            while (iterator.hasNext()) {
                CrystalOptimizerModule.Ghost ghost = iterator.next();
                if (this.ticks - ghost.removedAt() > window) {
                    iterator.remove();
                    if (revive(level, ghost.crystal())) {
                        this.stubborn.add(ghost.crystal().getId());
                    }
                }
            }
        }
    }

    private static boolean revive(ClientLevel level, EndCrystal old) {
        if (level.getEntity(old.getId()) != null) {
            return false;
        }

        EndCrystal copy = new EndCrystal(level, old.getX(), old.getY(), old.getZ());
        copy.setId(old.getId());
        copy.setUUID(old.getUUID());
        copy.setShowBottom(old.showsBottom());
        level.addEntity(copy);
        return true;
    }

    private boolean breaksOnServer(Minecraft mc, LocalPlayer player, ClientLevel level, EndCrystal crystal) {
        if (crystal.isRemoved()) {
            return false;
        }

        if (!player.isSpectator() && !player.isDeadOrDying() && !player.isAutoSpinAttack()) {
            ItemStack weapon = player.getMainHandItem();
            if (weapon.isItemEnabled(level.enabledFeatures()) && !player.cannotAttackWithItem(weapon, 0)) {
                if (!level.getWorldBorder().isWithinBounds(crystal.blockPosition())) {
                    return false;
                } else {
                    return !player.isWithinAttackRange(crystal.getBoundingBox(), 0.0)
                        ? false
                        : Crystals.enchantsAddDamage(weapon) || this.recentHandsDealDamage(mc, player, weapon);
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean recentHandsDealDamage(Minecraft mc, LocalPlayer player, ItemStack held) {
        if (!Crystals.dealsDamage(player, held)) {
            return false;
        }

        int window = Crystals.confirmWindow(mc, player);
        boolean complete = this.ticks - this.trackedSince >= window;
        ItemStack checked = held;

        int walked;
        for (walked = 0; walked < this.sampleCount; walked++) {
            int index = Math.floorMod(this.sampleCursor - 1 - walked, 64);
            if (this.ticks - this.sampleTicks[index] > window) {
                break;
            }

            ItemStack sample = this.heldSamples[index];
            if (sample != checked) {
                if (!Crystals.dealsDamage(player, sample)) {
                    return false;
                }

                checked = sample;
            }
        }

        if (walked == 64) {
            complete = false;
        }

        return complete || Crystals.dealsDamage(player, ItemStack.EMPTY);
    }

    private void sample(ItemStack stack) {
        this.heldSamples[this.sampleCursor] = stack;
        this.sampleTicks[this.sampleCursor] = this.ticks;
        this.sampleCursor = (this.sampleCursor + 1) % 64;
        if (this.sampleCount < 64) {
            this.sampleCount++;
        }
    }

    private void reset() {
        Arrays.fill(this.heldSamples, null);
        this.sampleCursor = 0;
        this.sampleCount = 0;
        this.trackedSince = this.ticks;
        this.trackedPlayer = null;
        this.trackedLevel = null;
        this.attackSent = false;
        this.pendingRemoval = null;
        this.ghosts.clear();
        this.stubborn.clear();
        this.serverRemoved.clear();
    }

    @Environment(EnvType.CLIENT)
    private static final class AttackProbe implements Handler {
        private boolean attack;

        boolean isAttack(ServerboundInteractPacket packet) {
            this.attack = false;
            packet.dispatch(this);
            return this.attack;
        }

        @Override
        public void onInteraction(InteractionHand hand) {
        }

        @Override
        public void onInteraction(InteractionHand hand, Vec3 location) {
        }

        @Override
        public void onAttack() {
            this.attack = true;
        }
    }

    @Environment(EnvType.CLIENT)
    private record Ghost(EndCrystal crystal, int removedAt) {
    }
}
