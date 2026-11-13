package dev.bytephase.pvp.module.impl.combat;

import dev.bytephase.pvp.gui.OverlayCanvas;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.impl.PacketReceiveEvent;
import dev.bytephase.pvp.event.impl.Render3DEvent;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.CombatMacroLock;
import dev.bytephase.pvp.module.HudOverlayProvider;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.ModuleRegistry;
import dev.bytephase.pvp.module.impl.combat.anchor.AnchorGuard;
import dev.bytephase.pvp.module.impl.combat.crystal.Combo;
import dev.bytephase.pvp.module.impl.combat.crystal.CrystalDamage;
import dev.bytephase.pvp.module.impl.combat.crystal.Crystals;
import dev.bytephase.pvp.module.impl.combat.crystal.HurtClock;
import dev.bytephase.pvp.module.impl.combat.crystal.LootGuard;
import dev.bytephase.pvp.module.impl.combat.crystal.ShotView;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.impl.combat.melee.SlotReturn;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import dev.bytephase.pvp.module.setting.ColorSetting;
import dev.bytephase.pvp.module.setting.NumberSetting;
import dev.bytephase.pvp.util.pvp.Rotation;
import dev.bytephase.pvp.util.pvp.RotationUtils;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class AnchorAuraModule extends ClientModule implements HudOverlayProvider {
    private static final double REACH_MARGIN = 0.01;
    private static final int ARM_TICKS = 2;
    private static final int HOLD_TICKS = 10;
    private static final int MAX_LEAD_TICKS = 5;
    private static final int AIM_TIMEOUT_TICKS = 12;
    private static final int SKIP_TICKS = 40;
    private final NumberSetting minDamage = this.addSetting("min-damage", new NumberSetting("Min Damage", 6.0F, 0.0F, 20.0F).step(0.5F));
    private final NumberSetting maxSelfDamage = this.addSetting("max-self-damage", new NumberSetting("Max Self Damage", 6.0F, 0.0F, 20.0F).step(0.5F));
    private final BooleanSetting render = this.addSetting("render", new BooleanSetting("Render", true));
    private final ColorSetting renderColor = this.addSetting("render-color", new ColorSetting("Render Color", -26052));
    private final NumberSetting turnSpeed = this.addSetting("turn-speed", new NumberSetting("Turn Speed", 4.5F, 1.0F, 10.0F).step(0.5F));
    private final BooleanSetting protectLoot = this.addSetting("protect-loot", new BooleanSetting("Protect Loot", true));
    private final SlotReturn slots = new SlotReturn(this);
    private final ShotView view = new ShotView();
    private int tick;
    private int holdUntil;
    private BlockPos spent;
    private int spentUntil;
    private float aimTurn;
    private float placedTurn;
    private BlockPos aimingAt;
    private int aimingTicks;
    private BlockPos skipped;
    private int skippedUntil;
    private List<CrystalDamage.Victim> lastTargets;
    private List<CrystalDamage.Victim> lastSpared;
    private int lastRtt;
    private float steerMin;
    private float steerSelf;
    private int steerUntil = -1;
    private float tickMin;
    private float tickSelf;

    public AnchorAuraModule() {
        super("anchor-aura", "Anchor Aura", "Places, charges and detonates respawn anchors on nearby enemies.", ModuleCategory.COMBAT);
        this.renderColor.visibleWhen(this.render::get);
    }

    @Override
    protected void onDisable() {
        this.slots.drop(Minecraft.getInstance(), true);
        this.spent = null;
        this.view.clear();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        this.tick++;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && mc.level != null) {
            LootGuard.update(mc.level, player);
        }

        CrystalAuraModule crystal = ModuleRegistry.get(CrystalAuraModule.class);
        if ((crystal == null || !crystal.isEnabled())
            && MeleeAttacks.canAct(mc)
            && !player.isSpectator()
            && !AutoInvTotemModule.wantsHotbar()
            && !AutoCityModule.mining()
            && !CombatMacroLock.heldByOther(this)
            && mc.level.dimension() != Level.NETHER) {
            HurtClock.tick(player);
            if (this.tick > this.holdUntil) {
                this.slots.tick(mc, true);
            }

            if (!MeleeAttacks.actedThisTick(player)
                && MeleeAttacks.handsFree(mc, player)
                && !player.isSecondaryUseActive()
                && !MeleeAttacks.rightClickComing(mc)) {
                Combo.Shot shot = Combo.pick(this.shots(mc, player, Combo.enemies(mc, player), Combo.friends(mc, player), Crystals.rttTicks(mc, player), 0.0F));
                if (shot == null) {
                    if (this.spent != null && this.tick < this.spentUntil && mc.level.getBlockState(this.spent).is(Blocks.RESPAWN_ANCHOR)) {
                        ServerRotation.request(ServerRotation.sent());
                    }

                    if (this.tick > this.holdUntil) {
                        this.release();
                    }
                } else {
                    this.show(shot);
                    if (CombatMacroLock.tryAcquire(this)) {
                        if (!this.ready(mc, player, shot)) {
                            BlockPos cell = cellOf(shot);
                            this.aimingTicks = cell.equals(this.aimingAt) ? this.aimingTicks + 1 : 0;
                            this.aimingAt = cell;
                            if (this.aimingTicks >= 12) {
                                this.skipped = cell;
                                this.skippedUntil = this.tick + 40;
                                this.aimingAt = null;
                            } else {
                                ServerRotation.request(RotationUtils.fromTo(player.getEyePosition(), shot.aim()), this.turnSpeed.get());
                            }
                        } else {
                            this.aimingAt = null;
                            if (shot.route() == -2 || this.slots.take(player, shot.route())) {
                                this.fire(mc, player, shot);
                                this.holdUntil = this.tick + 10;
                                ServerRotation.request(ServerRotation.sent());
                            }
                        }
                    }
                }
            }
        } else {
            this.slots.tick(mc, true);
            this.release();
        }
    }

    @EventHandler
    public void onPacketReceive(PacketReceiveEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            HurtClock.note(player, event.packet);
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (this.render.get()) {
            this.view.draw();
        }
    }

    @Override
    public boolean isHudOverlayEnabled() {
        return this.isEnabled() && this.render.get() && this.view.showing();
    }

    @Override
    public void renderHudOverlay(OverlayCanvas dl, float screenW, float screenH) {
        this.view.label(dl, screenW, screenH);
    }

    private static BlockPos cellOf(Combo.Shot shot) {
        return shot.plan() instanceof AnchorGuard.Spot spot ? spot.pos() : (BlockPos)shot.plan();
    }

    private boolean ruledOut(BlockPos cell) {
        return cell.equals(this.skipped) && this.tick < this.skippedUntil || this.protectLoot.get() && LootGuard.near(Vec3.atCenterOf(cell));
    }

    public void show(Combo.Shot shot) {
        if (this.render.get()) {
            BlockPos cell = cellOf(shot);
            Vec3 center = Vec3.atCenterOf(cell);
            float most = 0.0F;

            for (CrystalDamage.Victim victim : this.lastTargets) {
                most = Math.max(most, victim.damage(center, 5.0F, cell));
            }

            float own = this.lastSpared.get(0).damage(center, 5.0F, cell);
            this.view.show(new AABB(cell), this.renderColor.argb(), most, own);
        }
    }

    public void steer(LocalPlayer player, float minDamage, float maxSelf) {
        this.steerMin = minDamage;
        this.steerSelf = maxSelf;
        this.steerUntil = player.tickCount + 2;
    }

    public List<Combo.Shot> shots(Minecraft mc, LocalPlayer player, List<LivingEntity> enemies, List<LivingEntity> friends, int rtt, float rival) {
        ClientLevel level = mc.level;
        boolean steered = player.tickCount <= this.steerUntil;
        this.tickMin = steered ? this.steerMin : this.minDamage.get();
        this.tickSelf = steered ? this.steerSelf : this.maxSelfDamage.get();
        Inventory inventory = player.getInventory();
        ItemStack main = player.getMainHandItem();
        int detonator = AnchorGuard.detonatorRoute(inventory, main);
        if (level.dimension() != Level.NETHER
            && ServerRotation.canRotate(player)
            && !player.getOffhandItem().is(Items.GLOWSTONE)
            && !player.isSecondaryUseActive()
            && !enemies.isEmpty()
            && detonator != -1) {
            List<LivingEntity> spared = new ArrayList<>();
            spared.add(player);
            spared.addAll(friends);
            this.lastRtt = rtt;
            this.lastTargets = victims(enemies, rtt);
            this.lastSpared = victims(spared, rtt);
            List<Combo.Shot> shots = new ArrayList<>();
            int glowstone = routeOf(inventory, main, Items.GLOWSTONE);
            boolean existing = false;

            for (BlockPos cell : this.anchors(level, player, enemies)) {
                if (!this.ruledOut(cell)) {
                    Vec3 aim = AnchorGuard.anchorAim(level, player, cell);
                    if (aim != null && this.safe(this.lastSpared, cell)) {
                        boolean charged = level.getBlockState(cell).getValue(RespawnAnchorBlock.CHARGE) > 0;
                        int ahead = charged ? 0 : 1;
                        AnchorAuraModule.Blast due = this.blast(cell, ahead, false);
                        if (due != null || this.blast(cell, ahead, true) != null) {
                            existing = true;
                            if (due != null) {
                                if (charged) {
                                    shots.add(new Combo.Shot(Combo.Kind.DETONATE, aim, detonator, due.value(), due.kills(), 0, cell));
                                } else if (glowstone != -1 && HurtClock.opensIn(due.source(), rtt) <= 1 && (rival <= 0.0F || due.value() > rival + 1.0F)) {
                                    shots.add(new Combo.Shot(Combo.Kind.CHARGE, aim, glowstone, due.value(), due.kills(), 1, cell));
                                }
                            }
                        }
                    }
                }
            }

            int anchor = routeOf(inventory, main, Items.RESPAWN_ANCHOR);
            if (!existing && anchor != -1 && glowstone != -1) {
                List<AABB> reserved = Crystals.reservedBoxes(player, Crystals.confirmWindow(mc, player));
                Combo.Shot placement = this.place(level, player, anchor, reserved);
                if (placement != null) {
                    shots.add(placement);
                }
            }

            return shots;
        } else {
            return List.of();
        }
    }

    public boolean ready(Minecraft mc, LocalPlayer player, Combo.Shot shot) {
        ClientLevel level = mc.level;
        if (shot.kind() == Combo.Kind.ANCHOR) {
            AnchorGuard.Spot spot = (AnchorGuard.Spot)shot.plan();
            Rotation sent = ServerRotation.sent();
            if (sent != null && AnchorGuard.hitAlong(level, player, sent, spot) != null) {
                return true;
            }

            float turn = turnTo(player, spot);
            if (turn > 0.0F) {
                this.aimTurn = turn;
            }

            return false;
        } else {
            BlockPos cell = (BlockPos)shot.plan();
            BlockState state = level.getBlockState(cell);
            if (state.is(Blocks.RESPAWN_ANCHOR) && AnchorGuard.anchorHit(level, player, cell) != null) {
                boolean charged = state.getValue(RespawnAnchorBlock.CHARGE) > 0;
                return shot.kind() == Combo.Kind.CHARGE ? !charged : charged && this.safe(this.lastSpared, cell) && this.blast(cell, 0, false) != null;
            } else {
                return false;
            }
        }
    }

    public void fire(Minecraft mc, LocalPlayer player, Combo.Shot shot) {
        ClientLevel level = mc.level;
        switch (shot.kind()) {
            case ANCHOR:
                AnchorGuard.Spot spot = (AnchorGuard.Spot)shot.plan();
                AnchorGuard.click(mc, player, AnchorGuard.hitAlong(level, player, ServerRotation.sent(), spot));
                if (this.aimTurn > 0.0F) {
                    this.placedTurn = this.aimTurn;
                }
                break;
            case CHARGE:
                AnchorGuard.click(mc, player, AnchorGuard.anchorHit(level, player, (BlockPos)shot.plan()));
                break;
            case DETONATE:
                BlockPos cell = (BlockPos)shot.plan();
                AnchorGuard.click(mc, player, AnchorGuard.anchorHit(level, player, cell));
                this.spent = cell;
                this.spentUntil = this.tick + Crystals.confirmWindow(mc, player);
                Vec3 center = Vec3.atCenterOf(cell);

                for (CrystalDamage.Victim victim : this.lastTargets) {
                    float raw = victim.raw(center, 5.0F, cell);
                    if (raw > 0.0F) {
                        HurtClock.shot(player, victim.entity(), raw, center, this.lastRtt);
                    }
                }
        }
    }

    private Combo.Shot place(ClientLevel level, LocalPlayer player, int route, List<AABB> reserved) {
        Map<BlockPos, Float> bounds = new LinkedHashMap<>();

        for (CrystalDamage.Victim victim : this.lastTargets) {
            BlockPos feet = victim.entity().blockPosition();

            for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 2, 2))) {
                if (!bounds.containsKey(pos)
                    && level.getBlockState(pos).isAir()
                    && player.isWithinBlockInteractionRange(pos, -0.01)
                    && !reservedAt(reserved, pos)
                    && !this.ruledOut(pos)
                    && !this.walkedInto(level, pos)) {
                    float bound = this.upperValue(Vec3.atCenterOf(pos));
                    if (bound >= 0.0F) {
                        bounds.put(pos.immutable(), bound);
                    }
                }
            }
        }

        List<BlockPos> cells = new ArrayList<>(bounds.keySet());
        cells.sort((a, b) -> Float.compare(bounds.get(b), bounds.get(a)));
        Vec3 eye = player.getEyePosition();
        Combo.Shot best = null;

        for (BlockPos pos : cells) {
            if (best != null && bounds.get(pos) <= best.value()) {
                break;
            }

            AnchorAuraModule.Blast blast = this.blast(pos, 2, true);
            if (blast != null && (best == null || !(blast.value() <= best.value())) && this.safe(this.lastSpared, pos)) {
                AnchorGuard.Spot candidate = AnchorGuard.supportFor(level, player, eye, pos);
                if (candidate != null) {
                    Rotation look = ServerRotation.next(player, RotationUtils.fromTo(eye, candidate.aim()));
                    Vec3 reach = Vec3.directionFromRotation(look.pitch(), look.yaw()).scale(player.blockInteractionRange());
                    if (!new AABB(pos).clip(eye, eye.add(reach)).isEmpty()) {
                        float turn = turnTo(player, candidate);
                        if (!(turn > 2.0F) || !(Math.abs(turn - this.placedTurn) < 1.0E-4F)) {
                            best = new Combo.Shot(Combo.Kind.ANCHOR, candidate.aim(), route, blast.value(), blast.kills(), 2, candidate);
                        }
                    }
                }
            }
        }

        return best;
    }

    private Set<BlockPos> anchors(ClientLevel level, LocalPlayer player, List<LivingEntity> enemies) {
        Set<BlockPos> anchors = new LinkedHashSet<>();

        for (LivingEntity enemy : enemies) {
            BlockPos feet = enemy.blockPosition();

            for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-4, -3, -4), feet.offset(4, 4, 4))) {
                if (level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)
                    && player.isWithinBlockInteractionRange(pos, -0.01)
                    && (!pos.equals(this.spent) || this.tick >= this.spentUntil)) {
                    anchors.add(pos.immutable());
                }
            }
        }

        return anchors;
    }

    private AnchorAuraModule.Blast blast(BlockPos cell, int ahead, boolean waiting) {
        Vec3 center = Vec3.atCenterOf(cell);
        LivingEntity source = null;
        float best = 0.0F;
        boolean kills = false;

        for (CrystalDamage.Victim victim : this.lastTargets) {
            LivingEntity enemy = victim.entity();
            int when = waiting ? Math.max(ahead, HurtClock.opensIn(enemy, this.lastRtt)) : ahead;
            float effective = HurtClock.effective(victim, victim.raw(center, 5.0F, cell), when, this.lastRtt);
            if (!(effective < this.needed(enemy))) {
                kills = kills || Combo.kills(effective, enemy);
                float value = Combo.value(effective, enemy);
                if (source == null || value > best) {
                    source = enemy;
                    best = value;
                }
            }
        }

        if (source == null) {
            return null;
        }

        float own = this.lastSpared.get(0).damage(center, 5.0F, cell);
        return new AnchorAuraModule.Blast(best - 0.5F * own, kills, source);
    }

    private float upperValue(Vec3 center) {
        float bound = -1.0F;

        for (CrystalDamage.Victim victim : this.lastTargets) {
            float upper = victim.upperBound(center, 5.0F);
            if (upper >= this.needed(victim.entity())) {
                bound = Math.max(bound, Combo.value(upper, victim.entity()));
            }
        }

        return bound;
    }

    private boolean safe(List<CrystalDamage.Victim> spared, BlockPos cell) {
        Vec3 center = Vec3.atCenterOf(cell);

        for (CrystalDamage.Victim other : spared) {
            float damage = other.damage(center, 5.0F, cell);
            if (damage > this.tickSelf || CrystalDamage.endangers((Player)other.entity(), damage)) {
                return false;
            }
        }

        return true;
    }

    private float needed(LivingEntity enemy) {
        return Math.min(this.tickMin, CrystalDamage.health(enemy));
    }

    private static List<CrystalDamage.Victim> victims(List<LivingEntity> entities, int rtt) {
        List<CrystalDamage.Victim> victims = new ArrayList<>(entities.size());

        for (LivingEntity entity : entities) {
            int lead = entity instanceof LocalPlayer ? 0 : Math.min(rtt, 5);
            victims.add(new CrystalDamage.Victim(entity, Combo.lead(entity, lead)));
        }

        return victims;
    }

    private boolean walkedInto(ClientLevel level, BlockPos pos) {
        AABB cell = new AABB(pos);
        int lead = Math.min(this.lastRtt, 5);

        for (Player other : level.players()) {
            if (!other.isSpectator() && !(other instanceof LocalPlayer)) {
                AABB box = other.getBoundingBox();
                if (box.intersects(cell) || box.move(Combo.lead(other, lead)).intersects(cell)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean reservedAt(List<AABB> reserved, BlockPos pos) {
        for (AABB box : reserved) {
            if (box.intersects(pos)) {
                return true;
            }
        }

        return false;
    }

    private static int routeOf(Inventory inventory, ItemStack main, Item item) {
        if (main.is(item)) {
            return -2;
        }

        int slot = Crystals.hotbarSlotOf(inventory, item);
        return slot == -1 ? -1 : slot;
    }

    private static float turnTo(LocalPlayer player, AnchorGuard.Spot spot) {
        Rotation sent = ServerRotation.sent();
        Rotation from = sent != null ? sent : RotationUtils.entityRotation(player);
        Rotation aimed = ServerRotation.next(player, RotationUtils.fromTo(player.getEyePosition(), spot.aim()));
        return Math.abs(aimed.yaw() - from.yaw());
    }

    private void release() {
        if (!this.slots.holding()) {
            CombatMacroLock.release(this);
        }
    }

    @Environment(EnvType.CLIENT)
    private record Blast(float value, boolean kills, LivingEntity source) {
    }
}
