package dev.bytephase.pvp.module.impl.combat;

import java.util.ArrayDeque;
import java.util.Iterator;
import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.impl.PacketAppliedEvent;
import dev.bytephase.pvp.event.impl.PlayerAttackEntityEvent;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.mixin.AccessorCombatMinecraft;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.CombatMacroLock;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.impl.combat.crystal.Crystals;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.impl.combat.melee.SlotReturn;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import dev.bytephase.pvp.module.setting.NumberSetting;
import dev.bytephase.pvp.util.pvp.PlacementRate;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class CrystalMacroModule extends ClientModule {
    private static final int MAX_PENDING = 32;
    private static final int MIN_EXPIRY_TICKS = 20;
    private final NumberSetting placeCps = this.addSetting("place-cps", new NumberSetting("Place CPS", 20.0F, 0.5F, 20.0F).step(0.5F));
    private final PlacementRate placementRate = new PlacementRate();
    private final NumberSetting breakCps = this.addSetting("break-cps", new NumberSetting("Break CPS", 20.0F, 0.5F, 20.0F).step(0.5F));
    private final PlacementRate breakRate = new PlacementRate();
    private final BooleanSetting switchBack = this.addSetting("switch-back", new BooleanSetting("Switch Back", true));
    private final SlotReturn slots = new SlotReturn(this);
    private final ArrayDeque<Placement> placements = new ArrayDeque<>();
    private final ArrayDeque<SpawnedCrystal> spawned = new ArrayDeque<>();
    private LocalPlayer trackedPlayer;
    private ClientLevel trackedLevel;

    public CrystalMacroModule() {
        super("crystal-macro", "Crystal Macro", "Places crystals while you hold use and breaks your new crystals at separate adjustable speeds.", ModuleCategory.COMBAT);
    }

    @Override
    protected void onEnable() {
        this.resetState(Minecraft.getInstance());
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        this.slots.drop(mc, this.switchBack.get());
        this.resetState(mc);
    }

    /** Called only after a successful use of a crystal on its base block. */
    public void placedCrystal(LocalPlayer player, BlockPos base) {
        Minecraft mc = Minecraft.getInstance();
        if (!this.isEnabled() || mc.player != player || mc.level == null) {
            return;
        }
        this.refreshSession(mc);
        this.prune(player);
        this.placementRate.placed(player.tickCount, this.placeCps.get());
        if (this.placements.size() >= MAX_PENDING) {
            this.placements.removeFirst();
        }
        this.placements.addLast(new Placement(base.immutable(), this.expiresAt(mc, player)));
    }

    public boolean canPlaceCrystal(LocalPlayer player, BlockPos base) {
        this.refreshSession(Minecraft.getInstance());
        this.prune(player);
        return this.placementRate.ready(player.tickCount, this.placeCps.get())
            && this.placements.stream().noneMatch(placement -> placement.base().equals(base));
    }

    @EventHandler
    public void onPacketApplied(PacketAppliedEvent event) {
        if (!(event.packet instanceof ClientboundAddEntityPacket packet)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        this.refreshSession(mc);
        if (mc.player == null || mc.level == null
                || !(mc.level.getEntity(packet.getId()) instanceof EndCrystal crystal)) {
            return;
        }
        this.prune(mc.player);
        BlockPos base = crystal.blockPosition().below();
        Iterator<Placement> iterator = this.placements.iterator();
        while (iterator.hasNext()) {
            Placement placement = iterator.next();
            if (placement.base().equals(base)) {
                iterator.remove();
                if (this.spawned.size() >= MAX_PENDING) {
                    this.spawned.removeFirst();
                }
                SpawnedCrystal pending = new SpawnedCrystal(crystal, mc.player.tickCount);
                this.spawned.addLast(pending);
                // Runs after the spawn is applied on the client thread,
                // rather than waiting for the next module tick.
                if (this.spawned.peekFirst() == pending && this.tryBreak(mc, pending)) {
                    this.spawned.remove(pending);
                }
                return;
            }
        }
    }

    @EventHandler
    public void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        this.refreshSession(mc);
        if (mc.player == null || mc.level == null) {
            return;
        }
        this.slots.tick(mc, this.switchBack.get());
        this.prune(mc.player);
        SpawnedCrystal pending = this.spawned.peekFirst();
        if (pending != null && this.tryBreak(mc, pending)) {
            this.spawned.remove(pending);
        }
        this.speedUpPlacement(mc);
    }

    @EventHandler
    public void onAttack(PlayerAttackEntityEvent event) {
        if (event.target instanceof EndCrystal crystal) {
            // A manual attack also completes this placement's queued action.
            this.spawned.removeIf(pending -> pending.crystal() == crystal);
        }
    }

    private boolean tryBreak(Minecraft mc, SpawnedCrystal pending) {
        LocalPlayer player = mc.player;
        EndCrystal crystal = pending.crystal();
        if (!MeleeAttacks.canAct(mc) || !mc.isWindowActive() || player.isDeadOrDying()
                || player.isSpectator() || AutoInvTotemModule.wantsHotbar()
                || CombatMacroLock.heldByOther(this) || ServerRotation.pending()
                || ServerRotation.desynced() || MeleeAttacks.attackedThisTick(player)
                || !this.breakRate.ready(player.tickCount, this.breakCps.get())
                || !MeleeAttacks.handsFree(mc, player)
                || crystal.level() != mc.level || crystal.isRemoved()
                || dev.bytephase.pvp.marlow.MarlowCrystal.hidden(crystal)
                || !mc.level.getWorldBorder().isWithinBounds(crystal.blockPosition())
                || !player.isWithinAttackRange(crystal.getBoundingBox(), 0.0)) {
            return false;
        }

        Vec3 eye = player.getEyePosition();
        AABB box = crystal.getBoundingBox();
        Vec3 nearest = new Vec3(
            Mth.clamp(eye.x, box.minX, box.maxX),
            Mth.clamp(eye.y, box.minY, box.maxY),
            Mth.clamp(eye.z, box.minZ, box.maxZ)
        );
        if (!MeleeAttacks.lineIsClear(player, eye, nearest)) {
            return false;
        }

        int route = Crystals.weaponRoute(player);
        if (route == Crystals.NO_ROUTE) {
            return false;
        }
        if (Inventory.isHotbarSlot(route) && !this.slots.take(player, route)) {
            return false;
        }
        if (!Crystals.strikePlaced(mc, player, crystal)) {
            return false;
        }
        this.breakRate.placed(player.tickCount, this.breakCps.get());
        MeleeAttacks.claimTick(mc);
        this.speedUpPlacement(mc);
        return true;
    }

    private void speedUpPlacement(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (!MeleeAttacks.canAct(mc) || !mc.isWindowActive() || player.isDeadOrDying()
                || player.isSpectator() || AutoInvTotemModule.wantsHotbar()
                || CombatMacroLock.heldByOther(this) || ServerRotation.pending()
                || ServerRotation.desynced() || !MeleeAttacks.handsFree(mc, player)
                || !Crystals.isPhysicallyDown(mc, mc.options.keyUse)) {
            return;
        }
        if (!(dev.bytephase.pvp.marlow.MarlowCrystal.raycast(player) instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos base = hit.getBlockPos();
        var state = mc.level.getBlockState(base);
        boolean crystalInHand = player.getMainHandItem().is(Items.END_CRYSTAL)
            || player.getOffhandItem().is(Items.END_CRYSTAL)
                && Crystals.letsClickThrough(player.getMainHandItem(), state);
        if (crystalInHand && Crystals.isBase(state)
                && mc.level.getWorldBorder().isWithinBounds(base)
                && this.canPlaceCrystal(player, base) && Crystals.canHost(mc.level, base, null)) {
            // Vanilla still handles the held use button and the placement.
            // Remove its repeat delay only while a crystal base is ready.
            ((AccessorCombatMinecraft)mc).pvp$setRightClickDelay(0);
        }
    }

    private int expiresAt(Minecraft mc, LocalPlayer player) {
        return player.tickCount + Math.max(MIN_EXPIRY_TICKS, Crystals.confirmWindow(mc, player) * 2);
    }

    private void prune(LocalPlayer player) {
        this.placements.removeIf(placement -> player.tickCount > placement.expiresAt());
        int breakWindow = Math.max(MIN_EXPIRY_TICKS, Crystals.confirmWindow(Minecraft.getInstance(), player) * 2)
            + (int)Math.ceil(20.0 / this.breakCps.get()) * MAX_PENDING;
        this.spawned.removeIf(pending -> player.tickCount - pending.spawnedAt() > breakWindow
            || pending.crystal().isRemoved()
            || dev.bytephase.pvp.marlow.MarlowCrystal.hidden(pending.crystal()));
    }

    private void refreshSession(Minecraft mc) {
        if (this.trackedPlayer != mc.player || this.trackedLevel != mc.level) {
            this.resetState(mc);
            this.trackedPlayer = mc.player;
            this.trackedLevel = mc.level;
        }
    }

    private void resetState(Minecraft mc) {
        this.placements.clear();
        this.spawned.clear();
        this.placementRate.reset();
        this.breakRate.reset();
        this.slots.drop(mc, false);
        this.trackedPlayer = null;
        this.trackedLevel = null;
    }

    private record Placement(BlockPos base, int expiresAt) { }
    private record SpawnedCrystal(EndCrystal crystal, int spawnedAt) { }
}
