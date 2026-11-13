package dev.bytephase.pvp.module.impl.combat;

import dev.bytephase.pvp.gui.OverlayCanvas;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.impl.PacketReceiveEvent;
import dev.bytephase.pvp.event.impl.PlayerAttackEntityEvent;
import dev.bytephase.pvp.event.impl.Render3DEvent;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.CombatMacroLock;
import dev.bytephase.pvp.module.HudOverlayProvider;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.ModuleRegistry;
import dev.bytephase.pvp.module.impl.combat.crystal.Combo;
import dev.bytephase.pvp.module.impl.combat.crystal.CrystalDamage;
import dev.bytephase.pvp.module.impl.combat.crystal.Crystals;
import dev.bytephase.pvp.module.impl.combat.crystal.HurtClock;
import dev.bytephase.pvp.module.impl.combat.crystal.LootGuard;
import dev.bytephase.pvp.module.impl.combat.crystal.ShotView;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.impl.misc.AutoEatModule;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import dev.bytephase.pvp.module.setting.ColorSetting;
import dev.bytephase.pvp.module.setting.KeySetting;
import dev.bytephase.pvp.module.setting.NumberSetting;
import dev.bytephase.pvp.util.pvp.Rotation;
import dev.bytephase.pvp.util.pvp.RotationUtils;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import dev.bytephase.pvp.util.pvp.TargetingUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class CrystalAuraModule extends ClientModule implements HudOverlayProvider {
    private static final int COUNTER_CAP = 1000;
    private static final int IDLE_TICKS = 10;
    private static final float FACE_PLACE_HEALTH = 10.0F;
    private static final float FACE_PLACE_DAMAGE = 2.0F;
    private static final float ARMOR_LOW = 0.1F;
    private static final float BREAK_FACTOR = 0.6F;
    private static final double AIM_INSET = 0.1;
    private static final int MAX_BREAK_ATTEMPTS = 3;
    private static final double CHAIN_RANGE = 12.0;
    private static final int WEAPON_SETTLE_TICKS = 2;
    private static final float LOOK_BONUS = 2.0F;
    private static final int MAX_LEAD_TICKS = 5;
    private static final float LOOK_MATCH_DEGREES = 2.0F;
    private static final double PLACE_BREAK_MARGIN = 0.1;
    private static final float ONE_LOOK_BONUS = 2.0F;
    private static final float OBSIDIAN_UPGRADE_BAR = 8.0F;
    private static final float OBSIDIAN_MIN_GAIN = 5.0F;
    private static final float OBSIDIAN_MIN_RATIO = 2.0F;
    private static final float EAGER_UPGRADE_BAR = 11.0F;
    private static final float EAGER_MIN_GAIN = 3.0F;
    private static final float EAGER_MIN_RATIO = 1.4F;
    private static final double PLACE_REACH_MARGIN = 0.1;
    private static final float HELD_VIEW_BONUS = 20.0F;
    private final BooleanSetting requireHold = this.addSetting("require-hold", new BooleanSetting("Require Hold", false));
    private final KeySetting holdKey = this.addSetting("hold-key", new KeySetting("Hold Key", 1003));
    private final NumberSetting placeDelay = this.addSetting("place-delay", new NumberSetting("Place Delay", 0.0F, 0.0F, 10.0F).step(1.0F));
    private final NumberSetting breakDelay = this.addSetting("break-delay", new NumberSetting("Break Delay", 0.0F, 0.0F, 10.0F).step(1.0F));
    private final NumberSetting minDamage = this.addSetting("min-damage", new NumberSetting("Min Damage", 4.0F, 0.0F, 20.0F).step(0.5F));
    private final NumberSetting maxSelfDamage = this.addSetting("max-self-damage", new NumberSetting("Max Self Damage", 8.0F, 0.0F, 20.0F).step(0.5F));
    private final BooleanSetting facePlace = this.addSetting("face-place", new BooleanSetting("Face Place", true));
    private final KeySetting forceFacePlace = this.addSetting("force-face-place", new KeySetting("Force Face Place", -1));
    private final BooleanSetting autoObsidian = this.addSetting("auto-obsidian", new BooleanSetting("Auto Obsidian", true));
    private final BooleanSetting switchBack = this.addSetting("switch-back", new BooleanSetting("Switch Back", true));
    private final NumberSetting turnSpeed = this.addSetting("turn-speed", new NumberSetting("Turn Speed", 4.5F, 1.0F, 10.0F).step(0.5F));
    private final NumberSetting viewAngle = this.addSetting("view-angle", new NumberSetting("View Angle", 90.0F, 30.0F, 180.0F).step(5.0F));
    private final BooleanSetting humanize = this.addSetting("humanize", new BooleanSetting("Human Pace", true));
    private final BooleanSetting protectLoot = this.addSetting("protect-loot", new BooleanSetting("Protect Loot", true));
    private final BooleanSetting render = this.addSetting("render", new BooleanSetting("Render", true));
    private final ColorSetting renderColor = this.addSetting("render-color", new ColorSetting("Render Color", -2728705));
    private final ShotView view = new ShotView();
    private boolean engaged;
    private int idleTicks;
    private int savedSlot = -1;
    private int ownSlot = -1;
    private int ticksSinceBreak = 1000;
    private int ticksSincePlace = 1000;
    private int breakGap;
    private int placeGap;
    private ItemStack lastMain;
    private int mainAge = 1000;
    private int pendingBreakId = -1;
    private int pendingBreakAge = 1000;
    private BlockPos pendingObsidian;
    private int pendingObsidianAge = 1000;
    private final Int2IntOpenHashMap breakAttempts = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap doomed = new Int2IntOpenHashMap();
    private float steerMin;
    private float steerSelf;
    private int steerUntil = -1;
    private float tickMin;
    private float tickSelf;
    private int eagerUntil = -1;
    private int floorY;
    private int floorUntil = -1;
    private int tickFloor = Integer.MIN_VALUE;
    private int fastUntil = -1;
    private boolean fullSpeed;

    public CrystalAuraModule() {
        super("crystal-aura", "Crystal Aura", "Places and breaks end crystals for the most damage.", ModuleCategory.COMBAT);
        this.holdKey.visibleWhen(this.requireHold::get);
        this.renderColor.visibleWhen(this.render::get);
    }

    @Override
    protected void onEnable() {
        this.resetState();
    }

    @Override
    protected void onDisable() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && !AutoEatModule.isEating() && !AutoInvTotemModule.wantsHotbar()) {
            this.restoreSlot(player);
        }

        this.resetState();
        this.view.clear();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        this.ageCounters();
        LocalPlayer player = mc.player;
        if (player != null && mc.level != null) {
            LootGuard.update(mc.level, player);
        }

        if (player != null && mc.level != null && mc.gameMode != null && !player.isDeadOrDying()) {
            HurtClock.tick(player);
            this.forgetGone(mc.level);
            boolean steered = player.tickCount <= this.steerUntil;
            this.tickMin = steered ? this.steerMin : this.minDamage.get();
            this.tickSelf = steered ? this.steerSelf : this.maxSelfDamage.get();
            this.tickFloor = player.tickCount <= this.floorUntil ? this.floorY : Integer.MIN_VALUE;
            this.fullSpeed = player.tickCount <= this.fastUntil;
            ItemStack main = player.getMainHandItem();
            if (main != this.lastMain) {
                this.lastMain = main;
                this.mainAge = 0;
            }

            CrystalAuraModule.Look look = ServerRotation.canRotate(player) ? CrystalAuraModule.Look.SILENT : CrystalAuraModule.Look.CROSSHAIR;
            boolean crystals = !this.requireHold.get() || Crystals.isKeyDown(mc, this.holdKey.value());
            AnchorAuraModule aura = ModuleRegistry.get(AnchorAuraModule.class);
            boolean anchors = aura != null && aura.isEnabled() && look == CrystalAuraModule.Look.SILENT;
            if (MeleeAttacks.canAct(mc) && !player.isSpectator() && (crystals || anchors)) {
                if (!AutoInvTotemModule.wantsHotbar() && !AutoCityModule.mining() && !CombatMacroLock.heldByOther(this)) {
                    if (this.ownSlot != -1 && !this.ownsSelection(player)) {
                        this.forgetSlots();
                    }

                    ItemStack offhand = player.getOffhandItem();
                    boolean offhandUse = !Crystals.isCombatItem(offhand) && !offhand.is(Items.END_CRYSTAL) && !offhand.is(Items.OBSIDIAN);
                    if (!MeleeAttacks.rightClickComing(mc) || !offhandUse && !main.is(Items.RESPAWN_ANCHOR) && !main.is(Items.GLOWSTONE)) {
                        if (!MeleeAttacks.actedThisTick(player)
                            && MeleeAttacks.handsFree(mc, player)
                            && (this.ownsSelection(player) || mayTakeHand(player, anchors))) {
                            List<LivingEntity> enemies = Combo.enemies(mc, player);
                            if (enemies.isEmpty()) {
                                this.disengage(mc, player);
                            } else {
                                Rotation fixedLook = null;
                                if (look == CrystalAuraModule.Look.CROSSHAIR) {
                                    fixedLook = RotationUtils.entityRotation(player);
                                    if (ServerRotation.pending() || !sameLook(ServerRotation.sent(), fixedLook)) {
                                        this.idle(mc, player);
                                        return;
                                    }
                                }

                                int window = Crystals.confirmWindow(mc, player);
                                EndCrystal lastHit = this.lastHit(mc.level, window);
                                CrystalAuraModule.Fight fight = new CrystalAuraModule.Fight(
                                    player,
                                    mc.level,
                                    enemies,
                                    Combo.friends(mc, player),
                                    window,
                                    Crystals.rttTicks(mc, player),
                                    fixedLook,
                                    look == CrystalAuraModule.Look.SILENT ? 2.0F : 0.0F,
                                    Crystals.isKeyDown(mc, this.forceFacePlace.value())
                                );
                                Combo.Shot shot = this.choose(mc, fight, lastHit, crystals, anchors ? aura : null);
                                if (shot == null) {
                                    this.idle(mc, player);
                                } else {
                                    this.engage();
                                    if (shot.plan() instanceof CrystalAuraModule.Plan plan) {
                                        if (this.render.get()) {
                                            this.show(fight, plan);
                                        }

                                        if (!this.perform(mc, player, fight, plan, look) || look != CrystalAuraModule.Look.SILENT) {
                                            return;
                                        }

                                        CrystalAuraModule.Plan following = this.planCrystal(fight, this.lastHit(mc.level, window), 1, this.stagedBreak(fight));
                                        Rotation sent = ServerRotation.sent();
                                        this.turn(following != null && !fight.holds(following) ? fight.lookAt(following.aim()) : sent);
                                    } else {
                                        aura.show(shot);
                                        if (this.fire(mc, player, fight, aura, shot)) {
                                            ServerRotation.request(ServerRotation.sent());
                                        }
                                    }
                                }
                            }
                        } else {
                            this.idle(mc, player);
                        }
                    } else {
                        this.disengage(mc, player);
                    }
                } else {
                    CombatMacroLock.release(this);
                }
            } else {
                this.disengage(mc, player);
            }
        } else {
            this.disengage(mc, null);
            Crystals.forgetRtt();
        }
    }

    public void steer(LocalPlayer player, float minDamage, float maxSelf) {
        this.steerMin = minDamage;
        this.steerSelf = maxSelf;
        this.steerUntil = player.tickCount + 2;
    }

    public void buildEagerly(LocalPlayer player) {
        this.eagerUntil = player.tickCount + 2;
    }

    public void crystalsFrom(LocalPlayer player, int lowest) {
        this.floorY = lowest;
        this.floorUntil = player.tickCount + 2;
    }

    public void fullSpeed(LocalPlayer player) {
        this.fastUntil = player.tickCount + 2;
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

    @EventHandler
    public void onAttack(PlayerAttackEntityEvent event) {
        if (event.target instanceof EndCrystal crystal && crystal.level().isClientSide()) {
            this.markBreak(crystal);
        }
    }

    @EventHandler
    public void onPacketReceive(PacketReceiveEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            if (event.packet instanceof ClientboundBundlePacket bundle) {
                for (Packet<?> packet : bundle.subPackets()) {
                    this.note(player, packet);
                }
            } else {
                this.note(player, event.packet);
            }

            HurtClock.note(player, event.packet);
        }
    }

    private void note(LocalPlayer player, Packet<?> packet) {
        if (packet instanceof ClientboundAddEntityPacket spawn && spawn.getType() == EntityType.END_CRYSTAL) {
            Crystals.noteSpawn(player, BlockPos.containing(spawn.getX(), spawn.getY() - 1.0, spawn.getZ()));
        }
    }

    private static boolean mayTakeHand(LocalPlayer player, boolean anchors) {
        ItemStack main = player.getMainHandItem();
        return main.is(Items.END_CRYSTAL)
            || main.is(Items.OBSIDIAN)
            || Crystals.isCombatItem(main)
            || anchors && (main.is(Items.RESPAWN_ANCHOR) || main.is(Items.GLOWSTONE));
    }

    private boolean ownsSelection(LocalPlayer player) {
        return this.ownSlot != -1 && player.getInventory().getSelectedSlot() == this.ownSlot;
    }

    private void engage() {
        this.engaged = true;
        this.idleTicks = 0;
        CombatMacroLock.tryAcquire(this);
    }

    private void idle(Minecraft mc, LocalPlayer player) {
        if (!this.engaged || ++this.idleTicks >= 10) {
            this.disengage(mc, player);
        }
    }

    private void disengage(Minecraft mc, LocalPlayer player) {
        this.engaged = false;
        this.idleTicks = 0;
        CombatMacroLock.release(this);
        if (player == null) {
            this.forgetSlots();
        } else if (mc.screen == null && !AutoEatModule.isEating() && !AutoInvTotemModule.wantsHotbar() && !MeleeAttacks.actedThisTick(player)) {
            this.restoreSlot(player);
        }
    }

    private void restoreSlot(LocalPlayer player) {
        if (this.ownSlot != -1) {
            Inventory inventory = player.getInventory();
            if (this.switchBack.get()
                && Inventory.isHotbarSlot(this.savedSlot)
                && this.savedSlot != this.ownSlot
                && inventory.getSelectedSlot() == this.ownSlot) {
                inventory.setSelectedSlot(this.savedSlot);
            }

            this.forgetSlots();
        }
    }

    private void forgetSlots() {
        this.savedSlot = -1;
        this.ownSlot = -1;
    }

    private void resetState() {
        this.engaged = false;
        this.idleTicks = 0;
        this.forgetSlots();
        this.ticksSinceBreak = 1000;
        this.ticksSincePlace = 1000;
        this.breakGap = 0;
        this.placeGap = 0;
        this.lastMain = null;
        this.mainAge = 1000;
        this.pendingBreakId = -1;
        this.pendingBreakAge = 1000;
        this.pendingObsidian = null;
        this.pendingObsidianAge = 1000;
        this.breakAttempts.clear();
        this.doomed.clear();
        Crystals.forgetRtt();
        CombatMacroLock.release(this);
    }

    private void forgetGone(ClientLevel level) {
        forgetGone(level, this.breakAttempts.keySet());
        forgetGone(level, this.doomed.keySet());
    }

    private static void forgetGone(ClientLevel level, IntSet ids) {
        IntIterator iterator = ids.iterator();

        while (iterator.hasNext()) {
            if (level.getEntity(iterator.nextInt()) == null) {
                iterator.remove();
            }
        }
    }

    private static boolean oneLook(Vec3 eye, AABB crystal, Vec3 placeAim, double reach) {
        Vec3 direction = placeAim.subtract(eye).normalize();
        return crystal.clip(eye, eye.add(direction.scale(reach))).isPresent();
    }

    private boolean canHostSoon(CrystalAuraModule.Fight fight, BlockPos base, Entity breaking) {
        BlockPos above = base.above();
        if (!fight.level.isEmptyBlock(above)) {
            return false;
        }

        AABB space = new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1.0, above.getY() + 2.0, above.getZ() + 1.0);

        for (Entity entity : fight.level.getEntities(breaking, space)) {
            if (entity.isAlive()) {
                if (!(entity instanceof LivingEntity living) || !fight.enemies.contains(living)) {
                    return false;
                }

                AABB then = living.getBoundingBox().move(Combo.lead(living, fight.arriveLead));
                boolean over = then.minY >= space.maxY;
                boolean aside = then.maxX <= space.minX || then.minX >= space.maxX || then.maxZ <= space.minZ || then.minZ >= space.maxZ;
                if (!over && !aside) {
                    return false;
                }
            }
        }

        return true;
    }

    private EndCrystal lastHit(ClientLevel level, int window) {
        if (this.pendingBreakId >= 0 && this.pendingBreakAge < window) {
            return level.getEntity(this.pendingBreakId) instanceof EndCrystal crystal && crystal.isAlive() ? crystal : null;
        } else {
            return null;
        }
    }

    private Combo.Shot choose(Minecraft mc, CrystalAuraModule.Fight fight, EndCrystal lastHit, boolean crystals, AnchorAuraModule aura) {
        List<Combo.Shot> shots = new ArrayList<>();
        Combo.Shot aimed = null;
        float rival = 0.0F;
        if (crystals) {
            CrystalAuraModule.Plan opening = this.stagedBreak(fight);
            CrystalAuraModule.Plan plan = this.planCrystal(fight, lastHit, 0, opening);
            if (plan != null) {
                aimed = this.shot(fight, plan, false);
                if (this.due(plan, 0)) {
                    shots.add(aimed);
                }
            }

            if (aura != null && opening != null) {
                rival = this.shot(fight, opening, true).value();
            }
        }

        if (aura != null) {
            shots.addAll(aura.shots(mc, fight.player, fight.enemies, fight.friends, fight.rtt, rival));
        }

        Combo.Shot picked = Combo.pick(shots);
        return picked != null ? picked : aimed;
    }

    private void show(CrystalAuraModule.Fight fight, CrystalAuraModule.Plan plan) {
        CrystalAuraModule.Side side = fight.side(plan.step());
        boolean baseToCome = plan.step() == CrystalAuraModule.Step.OBSIDIAN;
        Vec3 center = plan.step() == CrystalAuraModule.Step.BREAK ? plan.crystal().position() : Crystals.explosionAt(plan.target());
        float most = 0.0F;

        for (CrystalDamage.Victim enemy : side.enemies) {
            most = Math.max(most, enemy.damage(center, baseToCome));
        }

        BlockPos base = plan.step() == CrystalAuraModule.Step.BREAK ? plan.crystal().blockPosition().below() : plan.target();
        this.view.show(new AABB(base), this.renderColor.argb(), most, side.self.damage(center, baseToCome));
    }

    private CrystalAuraModule.Plan planCrystal(CrystalAuraModule.Fight fight, EndCrystal lastHit, int ahead, CrystalAuraModule.Plan opening) {
        CrystalAuraModule.Plan breakPlan = this.planBreak(fight, false);
        if (breakPlan != null && this.due(breakPlan, ahead)) {
            return breakPlan;
        }

        boolean staged = breakPlan != null || opening != null || Crystals.anyPlacing(fight.player, fight.window);
        CrystalAuraModule.Plan placePlan = staged ? null : this.planPlacement(fight, lastHit, lastHit == null);
        return placePlan != null ? placePlan : breakPlan;
    }

    private CrystalAuraModule.Plan stagedBreak(CrystalAuraModule.Fight fight) {
        return this.planBreak(fight, true);
    }

    private Combo.Shot shot(CrystalAuraModule.Fight fight, CrystalAuraModule.Plan plan, boolean opening) {
        CrystalAuraModule.Step step = plan.step();
        Vec3 center = step == CrystalAuraModule.Step.BREAK ? plan.crystal().position() : Crystals.explosionAt(plan.target());
        int readyIn = step == CrystalAuraModule.Step.BREAK ? 0 : (step == CrystalAuraModule.Step.CRYSTAL ? fight.rtt : fight.rtt + 1);
        return new Combo.Shot(
            Combo.Kind.valueOf(step.name()),
            plan.aim(),
            plan.route(),
            this.score(fight, step, center, Float.NEGATIVE_INFINITY, opening),
            kills(fight, step, center, opening),
            readyIn,
            plan
        );
    }

    private CrystalAuraModule.Plan planBreak(CrystalAuraModule.Fight fight, boolean opening) {
        double reach = fight.player.entityInteractionRange();
        CrystalAuraModule.Plan best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        AABB around = fight.player.getBoundingBox().inflate(reach + 2.0);

        for (EndCrystal crystal : fight.level.getEntitiesOfClass(EndCrystal.class, around)) {
            if (crystal.isAlive()
                && this.breakAttempts.get(crystal.getId()) < 3
                && (crystal.getId() != this.pendingBreakId || this.pendingBreakAge >= fight.window)
                && (!this.doomed.containsKey(crystal.getId()) || fight.player.tickCount - this.doomed.get(crystal.getId()) >= fight.window)) {
                Vec3 eye = fight.eye;
                if (!(eye.distanceToSqr(TargetingUtils.closestPoint(eye, crystal.getBoundingBox())) > reach * reach)
                    && !(crystal.getY() < this.tickFloor - 0.01)) {
                    Vec3 center = crystal.position();
                    if ((!this.protectLoot.get() || !LootGuard.near(center))
                        && !(this.bound(fight, CrystalAuraModule.Step.BREAK, center) + fight.lookBonus <= bestScore)) {
                        CrystalAuraModule.Plan plan = this.breakLook(fight, crystal);
                        if (plan != null && fight.inView(plan)) {
                            float score = this.score(fight, CrystalAuraModule.Step.BREAK, center, bestScore - fight.lookBonus, opening);
                            if (score != Float.NEGATIVE_INFINITY) {
                                score += fight.lookCredit(plan);
                                if (!(score <= bestScore)) {
                                    best = plan;
                                    bestScore = score;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (best == null) {
            return null;
        }

        int weapon = Crystals.weaponRoute(fight.player);
        return weapon == -1 ? null : best.withRoute(weapon);
    }

    private CrystalAuraModule.Plan breakLook(CrystalAuraModule.Fight fight, EndCrystal crystal) {
        if (dev.bytephase.pvp.marlow.MarlowCrystal.hidden(crystal)) return null;
        if (fight.fixedLook != null) {
            CrystalAuraModule.Plan plan = new CrystalAuraModule.Plan(
                CrystalAuraModule.Step.BREAK, crystal, null, null, crystal.getBoundingBox().getCenter(), -2
            );
            return hitAlong(fight.player, plan, fight.eye, fight.fixedLook) != null ? plan : null;
        }

        BlockPos base = crystal.blockPosition().below();
        if (Crystals.isBase(fight.level.getBlockState(base))) {
            CrystalAuraModule.Plan throughBase = new CrystalAuraModule.Plan(CrystalAuraModule.Step.BREAK, crystal, base, base, aimOnTop(fight.level, base), -2);
            if (hitAlong(fight.player, throughBase, fight.aimEye, fight.lookAt(throughBase.aim())) != null) {
                return throughBase;
            }
        }

        Vec3 toward = fight.towardLook(crystal.getBoundingBox().deflate(0.1));
        CrystalAuraModule.Plan turned = new CrystalAuraModule.Plan(CrystalAuraModule.Step.BREAK, crystal, null, null, toward, -2);
        if (hitAlong(fight.player, turned, fight.aimEye, fight.lookAt(toward)) != null) {
            return turned;
        }

        Vec3 nearest = TargetingUtils.closestPoint(fight.aimEye, crystal.getBoundingBox().deflate(0.1));
        CrystalAuraModule.Plan plan = new CrystalAuraModule.Plan(CrystalAuraModule.Step.BREAK, crystal, null, null, nearest, -2);
        return hitAlong(fight.player, plan, fight.aimEye, fight.lookAt(nearest)) != null ? plan : null;
    }

    private CrystalAuraModule.Plan planPlacement(CrystalAuraModule.Fight fight, EndCrystal breaking, boolean mayLayBase) {
        CrystalAuraModule.Plan crystal = this.planBase(fight, CrystalAuraModule.Step.CRYSTAL, breaking);
        if (this.autoObsidian.get() && mayLayBase) {
            boolean eager = fight.player.tickCount <= this.eagerUntil;
            float crystalDamage = crystal == null ? 0.0F : this.enemyDamage(fight, crystal);
            if (crystal != null && crystalDamage >= (eager ? 11.0F : 8.0F)) {
                return crystal;
            } else {
                CrystalAuraModule.Plan obsidian = this.planBase(fight, CrystalAuraModule.Step.OBSIDIAN, null);
                if (obsidian != null && crystal != null) {
                    float obsidianDamage = this.enemyDamage(fight, obsidian);
                    boolean upgrade = obsidianDamage >= crystalDamage + (eager ? 3.0F : 5.0F) && obsidianDamage >= crystalDamage * (eager ? 1.4F : 2.0F);
                    return upgrade ? obsidian : crystal;
                } else {
                    return obsidian == null ? crystal : obsidian;
                }
            }
        } else {
            return crystal;
        }
    }

    private float enemyDamage(CrystalAuraModule.Fight fight, CrystalAuraModule.Plan plan) {
        Vec3 center = Crystals.explosionAt(plan.target());
        float most = 0.0F;

        for (CrystalDamage.Victim enemy : fight.side(plan.step()).enemies) {
            most = Math.max(most, enemy.damage(center, plan.step() == CrystalAuraModule.Step.OBSIDIAN));
        }

        return most;
    }

    private CrystalAuraModule.Plan planBase(CrystalAuraModule.Fight fight, CrystalAuraModule.Step step, EndCrystal breaking) {
        LocalPlayer player = fight.player;
        ClientLevel level = fight.level;
        Item item = step == CrystalAuraModule.Step.CRYSTAL ? Items.END_CRYSTAL : Items.OBSIDIAN;
        BlockState anyBase = Blocks.OBSIDIAN.defaultBlockState();
        if (Crystals.route(player, item, anyBase, this.savedSlot) == -1) {
            return null;
        }

        if (step == CrystalAuraModule.Step.OBSIDIAN && Crystals.route(player, Items.END_CRYSTAL, anyBase, this.savedSlot) == -1) {
            return null;
        }

        double placeReach = fight.placeReach;
        double breakable = player.entityInteractionRange() - 0.1;
        Vec3 eye = fight.eye;
        BlockPos origin = BlockPos.containing(eye);
        int radius = Mth.ceil(placeReach);
        float cycle = step == CrystalAuraModule.Step.CRYSTAL && fight.lookBonus > 0.0F ? 2.0F : 0.0F;
        List<CrystalAuraModule.Candidate> candidates = new ArrayList<>();
        MutableBlockPos cell = new MutableBlockPos();
        MutableBlockPos below = new MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cell.setWithOffset(origin, dx, dy, dz);
                    if (cell.getY() + 1 >= this.tickFloor) {
                        BlockState state = level.getBlockState(cell);
                        if (step == CrystalAuraModule.Step.CRYSTAL ? Crystals.isBase(state) : state.canBeReplaced()) {
                            below.setWithOffset(cell, Direction.DOWN);
                            if ((step != CrystalAuraModule.Step.OBSIDIAN || level.getBlockState(below).isFaceSturdy(level, below, Direction.UP))
                                && (
                                    step == CrystalAuraModule.Step.CRYSTAL
                                        ? !Crystals.placing(player, cell, fight.window)
                                        : !cell.equals(this.pendingObsidian) || this.pendingObsidianAge >= fight.window
                                )) {
                                BlockPos base = cell.immutable();
                                BlockPos clicked = step == CrystalAuraModule.Step.CRYSTAL ? base : below.immutable();
                                Vec3 aim = aimOnTop(level, clicked);
                                if (!(eye.distanceToSqr(aim) > placeReach * placeReach)) {
                                    AABB crystal = Crystals.boxOn(base);
                                    if (!(eye.distanceToSqr(TargetingUtils.closestPoint(eye, crystal)) > breakable * breakable)
                                        && (!this.protectLoot.get() || !LootGuard.near(Crystals.explosionAt(base)))) {
                                        float bound = this.bound(fight, step, Crystals.explosionAt(base));
                                        if (bound != Float.NEGATIVE_INFINITY) {
                                            candidates.add(
                                                new CrystalAuraModule.Candidate(new CrystalAuraModule.Plan(step, null, clicked, base, aim, -2), bound)
                                            );
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(CrystalAuraModule.Candidate::bound).reversed());
        CrystalAuraModule.Plan best = null;
        float bestScore = Float.NEGATIVE_INFINITY;

        for (CrystalAuraModule.Candidate candidate : candidates) {
            if (candidate.bound() + fight.lookBonus + cycle <= bestScore) {
                break;
            }

            CrystalAuraModule.Plan found = candidate.plan();
            float score = this.score(fight, step, Crystals.explosionAt(found.target()), bestScore - fight.lookBonus - cycle, false);
            if (score != Float.NEGATIVE_INFINITY
                && level.getWorldBorder().isWithinBounds(found.clicked())
                && this.canHostSoon(fight, found.target(), step == CrystalAuraModule.Step.CRYSTAL ? breaking : null)) {
                CrystalAuraModule.Plan plan = this.reachable(fight, found);
                if (plan != null && fight.inView(plan)) {
                    score += fight.lookCredit(plan);
                    if (cycle > 0.0F && oneLook(fight.aimEye, Crystals.boxOn(plan.target()), plan.aim(), breakable)) {
                        score += cycle;
                    }

                    if (!(score <= bestScore)) {
                        best = plan;
                        bestScore = score;
                    }
                }
            }
        }

        if (best == null) {
            return null;
        }

        int route = Crystals.route(player, item, level.getBlockState(best.clicked()), this.savedSlot);
        return route == -1 ? null : best.withRoute(route);
    }

    private CrystalAuraModule.Plan reachable(CrystalAuraModule.Fight fight, CrystalAuraModule.Plan plan) {
        if (fight.fixedLook == null) {
            BlockPos clicked = plan.clicked();
            double top = plan.aim().y;
            Vec3 near = fight.towardLook(new AABB(clicked.getX() + 0.1, top, clicked.getZ() + 0.1, clicked.getX() + 1.0 - 0.1, top, clicked.getZ() + 1.0 - 0.1));
            CrystalAuraModule.Plan turned = new CrystalAuraModule.Plan(plan.step(), null, clicked, plan.target(), near, plan.route());
            if (fight.eye.distanceToSqr(near) <= fight.placeReach * fight.placeReach
                && hitAlong(fight.player, turned, fight.aimEye, fight.lookAt(near)) != null) {
                return turned;
            }
        }

        if (hitAlong(fight.player, plan, fight.aimEye, fight.lookAt(plan.aim())) != null) {
            return plan;
        }

        if (plan.step() == CrystalAuraModule.Step.CRYSTAL && fight.fixedLook == null) {
            Vec3 middle = Vec3.atCenterOf(plan.clicked());

            for (Direction side : Plane.HORIZONTAL) {
                Vec3 face = middle.add(side.getStepX() * 0.4, 0.0, side.getStepZ() * 0.4);
                if (!((fight.aimEye.x - face.x) * side.getStepX() + (fight.aimEye.z - face.z) * side.getStepZ() <= 0.0)) {
                    CrystalAuraModule.Plan aimed = new CrystalAuraModule.Plan(plan.step(), null, plan.clicked(), plan.target(), face, plan.route());
                    if (hitAlong(fight.player, aimed, fight.aimEye, fight.lookAt(face)) != null) {
                        return aimed;
                    }
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private static Vec3 aimOnTop(ClientLevel level, BlockPos pos) {
        double top = level.getBlockState(pos).getShape(level, pos).max(Axis.Y);
        return new Vec3(pos.getX() + 0.5, pos.getY() + top - 0.1, pos.getZ() + 0.5);
    }

    private float need(LivingEntity target, boolean breaking, boolean forced) {
        float damage = !forced && (!this.facePlace.get() || !facePlaced(target)) ? this.tickMin : Math.min(2.0F, this.tickMin);
        return breaking ? damage * 0.6F : damage;
    }

    private static boolean facePlaced(LivingEntity target) {
        return CrystalDamage.health(target) <= 10.0F || Combo.inHole(target) || armorLow(target);
    }

    private static boolean armorLow(LivingEntity target) {
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (slot.isArmor()) {
                ItemStack stack = target.getItemBySlot(slot);
                if (stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= stack.getMaxDamage() * 0.1F) {
                    return true;
                }
            }
        }

        return false;
    }

    private float bound(CrystalAuraModule.Fight fight, CrystalAuraModule.Step step, Vec3 center) {
        float best = Float.NEGATIVE_INFINITY;

        for (CrystalDamage.Victim enemy : fight.side(step).enemies) {
            LivingEntity entity = enemy.entity();
            float upper = enemy.upperBound(center);
            if (Combo.kills(upper, entity) || !(upper < this.need(entity, step == CrystalAuraModule.Step.BREAK, fight.forced))) {
                best = Math.max(best, Combo.value(upper, entity));
            }
        }

        return best;
    }

    private float score(CrystalAuraModule.Fight fight, CrystalAuraModule.Step step, Vec3 center, float toBeat, boolean opening) {
        CrystalAuraModule.Side side = fight.side(step);
        boolean baseToCome = step == CrystalAuraModule.Step.OBSIDIAN;
        float selfDamage = side.self.damage(center, baseToCome);
        if (!(selfDamage > this.tickSelf) && !CrystalDamage.endangers(fight.player, selfDamage)) {
            for (CrystalDamage.Victim friend : side.friends) {
                float damage = friend.damage(center, baseToCome);
                if (damage > this.tickSelf || CrystalDamage.endangers((Player)friend.entity(), damage)) {
                    return Float.NEGATIVE_INFINITY;
                }
            }

            float best = Float.NEGATIVE_INFINITY;

            for (CrystalDamage.Victim enemy : side.enemies) {
                LivingEntity entity = enemy.entity();
                float effective = effective(fight, enemy, step, center, opening);
                if (Combo.kills(effective, entity) || !(effective < this.need(entity, step == CrystalAuraModule.Step.BREAK, fight.forced))) {
                    best = Math.max(best, Combo.value(effective, entity) - 0.5F * selfDamage);
                }
            }

            return best > toBeat ? best : Float.NEGATIVE_INFINITY;
        } else {
            return Float.NEGATIVE_INFINITY;
        }
    }

    private static boolean kills(CrystalAuraModule.Fight fight, CrystalAuraModule.Step step, Vec3 center, boolean opening) {
        for (CrystalDamage.Victim enemy : fight.side(step).enemies) {
            if (Combo.kills(effective(fight, enemy, step, center, opening), enemy.entity())) {
                return true;
            }
        }

        return false;
    }

    private static float effective(CrystalAuraModule.Fight fight, CrystalDamage.Victim enemy, CrystalAuraModule.Step step, Vec3 center, boolean opening) {
        int ahead = step == CrystalAuraModule.Step.BREAK ? (opening ? HurtClock.opensIn(enemy.entity(), fight.rtt) : 0) : fight.rtt;
        return HurtClock.effective(enemy, enemy.raw(center, step == CrystalAuraModule.Step.OBSIDIAN), ahead, fight.rtt);
    }

    private static HitResult hitAlong(LocalPlayer player, CrystalAuraModule.Plan plan, Vec3 eye, Rotation look) {
        if (look == null) {
            return null;
        }

        Vec3 direction = Vec3.directionFromRotation(look.pitch(), look.yaw());
        if (plan.step() == CrystalAuraModule.Step.BREAK) {
            AABB box = plan.crystal().getBoundingBox();
            if (box.contains(eye)) {
                return new EntityHitResult(plan.crystal(), eye);
            }

            Vec3 at = box.clip(eye, eye.add(direction.scale(player.entityInteractionRange()))).orElse(null);
            return at != null && MeleeAttacks.lineIsClear(player, eye, at) ? new EntityHitResult(plan.crystal(), at) : null;
        } else {
            ClientLevel level = (ClientLevel)player.level();
            BlockState clicked = level.getBlockState(plan.clicked());
            BlockHitResult hit = clicked.getShape(level, plan.clicked()).clip(eye, eye.add(direction.scale(player.blockInteractionRange())), plan.clicked());
            if (hit != null && MeleeAttacks.lineIsClear(player, eye, hit.getLocation())) {
                return plan.step() == CrystalAuraModule.Step.OBSIDIAN && !plan.target().equals(Crystals.placedObsidian(level, player, hit, clicked))
                    ? null
                    : hit;
            } else {
                return null;
            }
        }
    }

    private boolean perform(Minecraft mc, LocalPlayer player, CrystalAuraModule.Fight fight, CrystalAuraModule.Plan plan, CrystalAuraModule.Look look) {
        Vec3 eye = player.getEyePosition();
        Rotation sent = ServerRotation.sent();
        Rotation next = look == CrystalAuraModule.Look.SILENT ? sent : RotationUtils.entityRotation(player);
        HitResult hit = sent == null ? null : hitAlong(player, plan, eye, sent);
        if (hit != null && next != sent && hitAlong(player, plan, eye, next) == null) {
            hit = null;
        }

        if (plan.route() != -2) {
            this.select(player, plan.route());
        }

        boolean acted = hit != null
            && this.due(plan, 0)
            && (
                plan.step() == CrystalAuraModule.Step.BREAK
                    ? this.strike(mc, player, fight, plan.crystal())
                    : this.place(mc, player, plan, (BlockHitResult)hit)
            );
        if (acted) {
            MeleeAttacks.claimTick(mc);
        } else if (look == CrystalAuraModule.Look.SILENT) {
            this.turn(hit != null ? sent : fight.lookAt(plan.aim()));
        }

        return acted;
    }

    private boolean fire(Minecraft mc, LocalPlayer player, CrystalAuraModule.Fight fight, AnchorAuraModule aura, Combo.Shot shot) {
        if (shot.route() != -2) {
            this.select(player, shot.route());
        }

        if (!aura.ready(mc, player, shot)) {
            this.turn(fight.lookAt(shot.aim()));
            return false;
        } else {
            aura.fire(mc, player, shot);
            return true;
        }
    }

    private boolean strike(Minecraft mc, LocalPlayer player, CrystalAuraModule.Fight fight, EndCrystal crystal) {
        if (this.mainAge < 2 && !Crystals.enchantsAddDamage(player.getMainHandItem()) && !Crystals.dealsDamage(player, ItemStack.EMPTY)) {
            return false;
        }

        if (!Crystals.strike(mc, player, crystal)) {
            return false;
        }

        this.markBreak(crystal);
        this.breakAttempts.addTo(crystal.getId(), 1);
        this.ticksSinceBreak = 0;
        this.breakGap = this.humanize.get() && !this.fullSpeed ? Crystals.humanGap() : 0;

        for (CrystalDamage.Victim enemy : fight.side(CrystalAuraModule.Step.BREAK).enemies) {
            float raw = enemy.raw(crystal.position(), false);
            if (raw > 0.0F) {
                HurtClock.shot(player, enemy.entity(), raw, crystal.getId(), fight.rtt);
            }
        }

        AABB blast = crystal.getBoundingBox().inflate(12.0);

        for (EndCrystal other : fight.level.getEntitiesOfClass(EndCrystal.class, blast)) {
            if (other != crystal && other.distanceToSqr(crystal) <= 144.0) {
                this.doomed.put(other.getId(), player.tickCount);
            }
        }

        return true;
    }

    private boolean place(Minecraft mc, LocalPlayer player, CrystalAuraModule.Plan plan, BlockHitResult hit) {
        if (!Crystals.rightClick(mc, player, hit)) {
            return false;
        }

        if (plan.step() == CrystalAuraModule.Step.CRYSTAL) {
            Crystals.notePlace(player, plan.target());
        } else {
            this.pendingObsidian = plan.target();
            this.pendingObsidianAge = 0;
        }

        this.ticksSincePlace = 0;
        this.placeGap = this.humanize.get() && !this.fullSpeed ? Crystals.humanGap() : 0;
        return true;
    }

    private void turn(Rotation look) {
        ServerRotation.request(look, this.fullSpeed ? this.turnSpeed.max() : this.turnSpeed.get());
    }

    private static boolean sameLook(Rotation a, Rotation b) {
        return a != null && Math.abs(RotationUtils.yawDelta(a.yaw(), b.yaw())) <= 2.0F && Math.abs(a.pitch() - b.pitch()) <= 2.0F;
    }

    private boolean due(CrystalAuraModule.Plan plan, int ahead) {
        return plan.step() == CrystalAuraModule.Step.BREAK
            ? this.ticksSinceBreak + ahead > Math.max(this.breakDelay.getInt(), this.breakGap - 1)
            : this.ticksSincePlace + ahead > Math.max(this.placeDelay.getInt(), this.placeGap - 1);
    }

    private void select(LocalPlayer player, int slot) {
        Inventory inventory = player.getInventory();
        boolean changed = inventory.getSelectedSlot() != slot;
        if (this.savedSlot == -1) {
            this.savedSlot = inventory.getSelectedSlot();
        }

        inventory.setSelectedSlot(slot);
        this.ownSlot = slot;
        if (changed) {
            this.lastMain = player.getMainHandItem();
            this.mainAge = 0;
        }
    }

    private void markBreak(EndCrystal crystal) {
        this.pendingBreakId = crystal.getId();
        this.pendingBreakAge = 0;
    }

    private void ageCounters() {
        this.ticksSinceBreak = Math.min(this.ticksSinceBreak + 1, 1000);
        this.ticksSincePlace = Math.min(this.ticksSincePlace + 1, 1000);
        this.mainAge = Math.min(this.mainAge + 1, 1000);
        this.pendingBreakAge = Math.min(this.pendingBreakAge + 1, 1000);
        this.pendingObsidianAge = Math.min(this.pendingObsidianAge + 1, 1000);
    }

    @Environment(EnvType.CLIENT)
    private record Candidate(CrystalAuraModule.Plan plan, float bound) {
    }

    @Environment(EnvType.CLIENT)
    private final class Fight {
        final LocalPlayer player;
        final ClientLevel level;
        final Vec3 eye;
        final Vec3 aimEye;
        final List<LivingEntity> enemies;
        final List<LivingEntity> friends;
        final int window;
        final int rtt;
        final Rotation fixedLook;
        final Rotation serverLook;
        final float lookBonus;
        final int arriveLead;
        private final int placeLead;
        private final int selfPlaceLead;
        final boolean forced;
        final double placeReach;
        private CrystalAuraModule.Side breakSide;
        private CrystalAuraModule.Side placeSide;

        Fight(
            LocalPlayer player,
            ClientLevel level,
            List<LivingEntity> enemies,
            List<LivingEntity> friends,
            int window,
            int rtt,
            Rotation fixedLook,
            float lookBonus,
            boolean forced
        ) {
            this.player = player;
            this.level = level;
            this.eye = player.getEyePosition();
            this.aimEye = fixedLook != null ? this.eye : this.eye.add(player.getDeltaMovement());
            this.enemies = enemies;
            this.friends = friends;
            this.window = window;
            this.rtt = rtt;
            this.fixedLook = fixedLook;
            this.serverLook = ServerRotation.sent();
            this.lookBonus = lookBonus;
            this.arriveLead = Math.min(rtt, 5);
            this.placeLead = Math.min(2 * rtt, 5);
            this.selfPlaceLead = Math.min(rtt, 5);
            this.forced = forced;
            this.placeReach = player.blockInteractionRange() - 0.1;
        }

        CrystalAuraModule.Side side(CrystalAuraModule.Step step) {
            if (step == CrystalAuraModule.Step.BREAK) {
                if (this.breakSide == null) {
                    this.breakSide = new CrystalAuraModule.Side(this.player, this.enemies, this.friends, this.arriveLead, 0);
                }

                return this.breakSide;
            } else {
                if (this.placeSide == null) {
                    this.placeSide = new CrystalAuraModule.Side(this.player, this.enemies, this.friends, this.placeLead, this.selfPlaceLead);
                }

                return this.placeSide;
            }
        }

        Rotation lookAt(Vec3 aim) {
            return this.fixedLook != null ? this.fixedLook : RotationUtils.fromTo(this.aimEye, aim);
        }

        boolean holds(CrystalAuraModule.Plan plan) {
            return CrystalAuraModule.hitAlong(this.player, plan, this.eye, this.serverLook) != null;
        }

        float lookCredit(CrystalAuraModule.Plan plan) {
            return this.lookBonus > 0.0F && this.holds(plan) ? this.lookBonus : 0.0F;
        }

        boolean inView(CrystalAuraModule.Plan plan) {
            if (this.fixedLook == null && !(CrystalAuraModule.this.viewAngle.get() >= 180.0F)) {
                float off = Math.abs(RotationUtils.yawDelta(this.player.getYRot(), RotationUtils.fromTo(this.aimEye, plan.aim()).yaw()));
                return off <= CrystalAuraModule.this.viewAngle.get() || off <= CrystalAuraModule.this.viewAngle.get() + 20.0F && this.holds(plan);
            } else {
                return true;
            }
        }

        Vec3 towardLook(AABB box) {
            Rotation from = this.serverLook != null ? this.serverLook : RotationUtils.entityRotation(this.player);
            Vec3 direction = Vec3.directionFromRotation(from.pitch(), from.yaw());
            Vec3 center = box.getCenter();
            boolean flat = box.getYsize() < 1.0E-6 && direction.y < -1.0E-6 && this.aimEye.y > box.minY;
            double along = flat ? (box.minY - this.aimEye.y) / direction.y : center.subtract(this.aimEye).dot(direction);
            if (along <= 0.0) {
                return center;
            }

            Vec3 on = this.aimEye.add(direction.scale(along));
            return new Vec3(Mth.clamp(on.x, box.minX, box.maxX), Mth.clamp(on.y, box.minY, box.maxY), Mth.clamp(on.z, box.minZ, box.maxZ));
        }
    }

    @Environment(EnvType.CLIENT)
    private enum Look {
        SILENT,
        CROSSHAIR;
    }

    @Environment(EnvType.CLIENT)
    private record Plan(CrystalAuraModule.Step step, EndCrystal crystal, BlockPos clicked, BlockPos target, Vec3 aim, int route) {
        CrystalAuraModule.Plan withRoute(int newRoute) {
            return new CrystalAuraModule.Plan(this.step, this.crystal, this.clicked, this.target, this.aim, newRoute);
        }
    }

    @Environment(EnvType.CLIENT)
    private static final class Side {
        final List<CrystalDamage.Victim> enemies = new ArrayList<>();
        final List<CrystalDamage.Victim> friends = new ArrayList<>();
        final CrystalDamage.Victim self;

        Side(LocalPlayer player, List<LivingEntity> targets, List<LivingEntity> friends, int lead, int selfLead) {
            for (LivingEntity target : targets) {
                this.enemies.add(new CrystalDamage.Victim(target, Combo.lead(target, lead)));
            }

            for (LivingEntity friend : friends) {
                this.friends.add(new CrystalDamage.Victim(friend, Combo.lead(friend, lead)));
            }

            this.self = new CrystalDamage.Victim(player, Combo.lead(player, selfLead));
        }
    }

    @Environment(EnvType.CLIENT)
    private enum Step {
        BREAK,
        CRYSTAL,
        OBSIDIAN;
    }
}
