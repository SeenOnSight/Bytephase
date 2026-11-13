package dev.bytephase.pvp.module.impl.combat.bot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import dev.bytephase.pvp.chat.ModuleChat;
import dev.bytephase.pvp.easyplace.builder.Aim;
import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.Stage;
import dev.bytephase.pvp.event.impl.PacketAppliedEvent;
import dev.bytephase.pvp.event.impl.PacketReceiveEvent;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.CombatMacroLock;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.ModuleRegistry;
import dev.bytephase.pvp.module.impl.combat.AnchorAuraModule;
import dev.bytephase.pvp.module.impl.combat.AutoCityModule;
import dev.bytephase.pvp.module.impl.combat.AutoInvTotemModule;
import dev.bytephase.pvp.module.impl.combat.AutoTotemModule;
import dev.bytephase.pvp.module.impl.combat.AutoTrapModule;
import dev.bytephase.pvp.module.impl.combat.CrystalAuraModule;
import dev.bytephase.pvp.module.impl.combat.anchor.AnchorGuard;
import dev.bytephase.pvp.module.impl.combat.crystal.Combo;
import dev.bytephase.pvp.module.impl.combat.crystal.CrystalDamage;
import dev.bytephase.pvp.module.impl.combat.crystal.Crystals;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.setting.BooleanSetting;
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
import net.minecraft.core.Registry;
import net.minecraft.core.Direction.Plane;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;

@Environment(EnvType.CLIENT)
public final class CrystalBotModule extends ClientModule {
    private static final double MIN = 2.4;
    private static final double IDEAL = 3.2;
    private static final double MAX = 4.0;
    private static final Predicate<ItemStack> GAPPLE = stack -> stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
    private static final Predicate<ItemStack> XP = stack -> stack.is(Items.EXPERIENCE_BOTTLE);
    private static final Predicate<ItemStack> PEARL = stack -> stack.is(Items.ENDER_PEARL);
    private static final Predicate<ItemStack> OBSIDIAN = stack -> stack.is(Items.OBSIDIAN);
    private static final Predicate<ItemStack> ANCHOR = stack -> stack.is(Items.RESPAWN_ANCHOR);
    private static final Predicate<ItemStack> GLOWSTONE = stack -> stack.is(Items.GLOWSTONE);
    private static final float ARMOR_LOW = 0.3F;
    private static final int RUN_HOLD = 40;
    private static final int POP_COVER = 40;
    private static final int LAUNCH_GAP = 80;
    private static final int LAUNCH_STALL = 20;
    private static final int LAUNCH_TIMEOUT = 16;
    private static final double LAUNCH_START = 3.9;
    private static final double LAUNCH_GIVE_UP = 4.1;
    private static final double LAUNCH_STEP_IN = 2.9;
    private static final int LAUNCH_WATCH = 8;
    private static final double LAUNCH_LIFT = 0.3;
    private static final double HIT_RANGE = 2.95;
    private static final int THROW_TIMEOUT = 14;
    private static final int PEARL_GAP = 30;
    private static final int SHIELD_GAP = 20;
    private static final int BREACH_WAIT = 40;
    private static final int BREACH_WAIT_CAMPER = 15;
    private static final int BREACH_GAP = 100;
    private static final double ESCAPE_FAR = 20.0;
    private static final double ESCAPE_NEAR = 12.0;
    private static final int SEARCH_GAP = 10;
    private static final double EAT_CLEAR = 8.0;
    private static final int FOLLOW_TICKS = 12;
    private static final int OPENER_WINDOW = 40;
    private static final int CAMP_MAX = 2;
    private static final int SPOT_SCAN_GAP = 5;
    private static final float SPOT_DANGER = 8.0F;
    private static final int WORKING = 0;
    private static final int PLACED = 1;
    private static final int REGROUP_TICKS = 300;
    private static final double REGROUP_RANGE = 64.0;
    private static final int BOX_AIM_TIMEOUT = 10;
    private static final float RETREAT_HEALTH = 6.0F;
    private static final int POP_STREAK = 3;
    private static final int POP_STREAK_GAP = 600;
    private static final int POP_RUN = 200;
    private static final double BLAST_WATCH = 7.0;
    private static final double BELOW_DROP = 3.0;
    private static final double BELOW_REACH = 8.0;
    private static final double DIG_DEEPEST = 6.0;
    private static final int BELOW_SETTLE = 20;
    private static final int DIG_TIMEOUT = 80;
    private static final int DIG_WALK = 40;
    private static final int DIG_MAX = 2;
    private static final int DIG_GAP = 100;
    private static final int DIG_DOWN_TICKS = 50;
    private static final int DIG_BASE_TICKS = 25;
    private static final float LEVEL_MIN_DAMAGE = 2.5F;
    private final NumberSetting range = this.addSetting("range", new NumberSetting("Fight Range", 16.0F, 8.0F, 32.0F).step(1.0F));
    private final BooleanSetting anchors = this.addSetting("anchors", new BooleanSetting("Use Anchors", true));
    private final BooleanSetting launches = this.addSetting("knock-up", new BooleanSetting("Knock-Up Hits", true));
    private final BooleanSetting breach = this.addSetting("break-holes", new BooleanSetting("Break Holes", true));
    private final BooleanSetting guardAnchors = this.addSetting("guard-anchors", new BooleanSetting("Guard Anchors", true));
    private final BooleanSetting surround = this.addSetting("surround", new BooleanSetting("Surround Self", true));
    private final NumberSetting eatBelow = this.addSetting("eat-below", new NumberSetting("Gapple Below HP", 14.0F, 0.0F, 20.0F).step(1.0F));
    private final BooleanSetting mendArmor = this.addSetting("mend-armor", new BooleanSetting("Mend Armor", true));
    private final BooleanSetting escape = this.addSetting("escape-pearls", new BooleanSetting("Escape Pearl", true));
    private final BooleanSetting chase = this.addSetting("chase-pearls", new BooleanSetting("Chase Pearls", true));
    private final BooleanSetting refill = this.addSetting("refill", new BooleanSetting("Refill Hotbar", true));
    private final BooleanSetting learn = this.addSetting("learn", new BooleanSetting("Learn Rivals", true));
    private final BooleanSetting dance = this.addSetting("strafe", new BooleanSetting("Strafe", true));
    private final Minecraft mc = Minecraft.getInstance();
    private final BotBody body = new BotBody();
    private final BotKit kit = new BotKit();
    private final RivalBook book = new RivalBook();
    private final BotPause pause = new BotPause();
    private final AirTrack air = new AirTrack();
    private Player challenger;
    private int challengerSince;
    private int attackedBy = -1;
    private int attackedAt = -1000;
    private double closest;
    private int closerAt;
    private StandPicker.Spot standing;
    private int standScanAt;
    private int defuseAt = -1000;
    private int retreatAt = -1000;
    private Boolean crystalWasOn;
    private Boolean anchorWasOn;
    private Boolean totemWasOn;
    private int popStreak;
    private boolean poppedOut;
    private BlockPos digAnchor;
    private boolean digPlaced;
    private boolean digAnnounced;
    private int digGuards;
    private int digUntil;
    private int digWalkUntil;
    private int digBlasts;
    private int digAfter;
    private int belowSince = -1;
    private int stepDigAfter;
    private int baseDigAfter;
    private int diggingAt = -100;
    private boolean eating;
    private boolean needsGapple;
    private boolean mending;
    private BlockPos boxingAt;
    private int boxingUntil;
    private int tick;
    private Player fought;
    private CrystalBotModule.Stance stance = CrystalBotModule.Stance.HOLD;
    private int runUntil;
    private int ownPopAt = -1000;
    private CrystalBotModule.Throw throwing;
    private Rotation throwLook;
    private Vec3 throwSpot;
    private double throwAway;
    private int throwUntil;
    private int pearlAt = -1000;
    private int searchAfter;
    private int theirPearl = -1;
    private int followedPearl = -1;
    private boolean launching;
    private int launchUntil;
    private int nextLaunch;
    private int launchedAt = -1;
    private double launchY;
    private double launchPeak;
    private int shieldAt = -1000;
    private BlockPos boxAiming;
    private int boxAimTicks;
    private BlockPos boxSkipped;
    private int boxSkippedUntil;
    private int followUntil;
    private float selfCapNow;
    private boolean followLaid = true;
    private BlockPos blocking;
    private int spotScanAt;
    private int cityFails;
    private int openerArm = -1;
    private double openerReach;
    private int openerAt;
    private float openerHealth;
    private int openerPops;
    private boolean cityStarted;
    private int breachAfter;
    private float lastTheirHealth;
    private int hurtAt;
    private int progressAt;
    private int theirPopAt = -1;
    private int holeSince = -1;
    private boolean lowSpell;
    private boolean pearledInSpell;

    public CrystalBotModule() {
        super(
            "crystal-bot",
            "Crystal PvP Bot",
            "Crystal-fights the nearest enemy player: steers the Crystal and Anchor Aura, knocks them up, breaks their hole, guards, eats, mends, refills and pearls.",
            ModuleCategory.COMBAT
        );
    }

    @Override
    protected void onEnable() {
        this.body.forget();
        this.eating = this.mending = this.launching = this.cityStarted = this.needsGapple = false;
        this.boxingAt = null;
        this.fought = null;
        this.throwing = null;
        this.tick = 0;
        this.runUntil = 0;
        this.nextLaunch = this.followUntil = this.boxSkippedUntil = 0;
        this.launchedAt = -1;
        this.ownPopAt = this.pearlAt = this.shieldAt = -1000;
        this.searchAfter = this.breachAfter = this.spotScanAt = this.standScanAt = this.stepDigAfter = this.baseDigAfter = 0;
        this.defuseAt = this.attackedAt = this.retreatAt = -1000;
        this.attackedBy = -1;
        this.challenger = null;
        this.standing = null;
        this.air.forget();
        this.pause.reset();
        this.crystalWasOn = this.anchorWasOn = this.totemWasOn = null;
        this.popStreak = 0;
        this.forgetDig();
        this.on(TickEvent.TYPE, Stage.PRE, event -> this.tick());
    }

    @Override
    protected void onDisable() {
        this.body.release(this.mc);
        this.body.forget();
        CombatMacroLock.release(this);
        this.restoreAuras();
        AutoCityModule city = ModuleRegistry.get(AutoCityModule.class);
        if (this.cityStarted && city != null && city.isEnabled()) {
            city.setEnabled(false);
        }

        this.cityStarted = false;
        this.book.save(true);
    }

    @EventHandler
    public void onPacketApplied(PacketAppliedEvent event) {
        LocalPlayer player = this.mc.player;
        if (player != null && this.mc.level != null) {
            if (event.packet instanceof ClientboundEntityEventPacket status && status.getEventId() == 35) {
                if (status.getEntity(this.mc.level) == player) {
                    this.popStreak = this.tick - this.ownPopAt <= 600 ? this.popStreak + 1 : 1;
                    this.ownPopAt = this.tick;
                } else if (this.fought != null && status.getEntity(this.mc.level) == this.fought) {
                    this.theirPopAt = this.tick;
                    this.openerPops++;
                }
            }
        }
    }

    @EventHandler
    public void onPacketReceive(PacketReceiveEvent event) {
        LocalPlayer player = this.mc.player;
        if (player != null) {
            if (event.packet instanceof ClientboundPlayerPositionPacket moved) {
                Vec3 to = moved.change().position();
                Vec3 from = player.position();
                Set<Relative> relative = moved.relatives();
                this.pause
                    .moved(
                        new Vec3(
                            relative.contains(Relative.X) ? from.x + to.x : to.x,
                            relative.contains(Relative.Y) ? from.y + to.y : to.y,
                            relative.contains(Relative.Z) ? from.z + to.z : to.z
                        ),
                        this.tick - this.pearlAt
                    );
            } else if (event.packet instanceof ClientboundSetTimePacket time) {
                this.pause.time(time.gameTime());
            } else if (event.packet instanceof ClientboundExplodePacket blast) {
                this.air.blast(blast.center());
            } else if (event.packet instanceof ClientboundDamageEventPacket hurt && hurt.entityId() == player.getId() && hurt.sourceCauseId() >= 0) {
                this.attackedBy = hurt.sourceCauseId();
                this.attackedAt = this.tick;
            }
        }
    }

    private void tick() {
        this.tick++;
        LocalPlayer player = this.mc.player;
        ClientLevel level = this.mc.level;
        this.body.release(this.mc);
        if (player != null && level != null && MeleeAttacks.canAct(this.mc) && !player.isDeadOrDying()) {
            CombatMacroLock.release(this);
            this.keepTotem();
            this.body.stayGrounded(blastNear(level, player));
            if (this.learn.get()) {
                this.book.save(false);
            }

            BotPause.State state = this.pause.tick(this.mc, player);
            if (state == BotPause.State.STOP) {
                ModuleChat.send(this, ModuleChat.body().danger("The server keeps pulling us back").text(", stopping."));
                this.requestDisable();
            } else if (state != BotPause.State.RUN) {
                this.throwing = null;
                this.launching = this.eating = false;
                if (state == BotPause.State.HOLD) {
                    CombatMacroLock.take(this);
                }
            } else {
                boolean regrouping = this.needsGapple || this.eating || this.tick - this.retreatAt < 300;
                Player target = this.choose(level, player, this.body.pick(this.mc, player, this.range.get(), regrouping ? 64.0 : this.range.get() + 8.0));
                if (target != this.fought) {
                    this.newFight(target);
                }

                boolean low = player.getHealth() <= this.eatAt() && player.getAbsorptionAmount() < 4.0F;
                if (player.getHealth() > this.eatAt() + 4.0F || player.getAbsorptionAmount() >= 4.0F) {
                    this.needsGapple = false;
                }

                if (target == null) {
                    this.launching = false;
                    this.throwing = null;
                    CombatMacroLock.release(this);
                    if ((low || this.needsGapple || this.eating && player.isUsingItem()) && this.hands() && this.body.use(this.mc, player, GAPPLE)) {
                        this.eating = true;
                    } else {
                        this.eating = false;
                        if (!this.refill.get() || !this.kit.refill(this.mc, player, this.anchors.get())) {
                            if (!this.mendIdle(player)) {
                                this.mending = false;
                            }
                        }
                    }
                } else {
                    RivalBook.Rival rival = this.learn.get() ? this.book.of(target.getUUID()) : null;
                    this.watch(level, target, rival);
                    if (feetY(player) < Mth.floor(target.getY() + 0.01)) {
                        this.body.stayGrounded(true);
                    }

                    this.air.note(target, this.tick, rival);
                    Combo.prefer(player, target);
                    Combo.flight(player, target, ahead -> this.air.lead(ahead, this.tick));
                    this.runAuras(level);
                    CrystalBotModule.Stance was = this.stance;
                    this.stance = this.stance(level, player, target, rival);
                    if (this.stance == CrystalBotModule.Stance.RUN && was != CrystalBotModule.Stance.RUN) {
                        ModuleChat.send(
                            this,
                            this.poppedOut
                                ? ModuleChat.body().warn("3 totems popped").text(", getting away.")
                                : ModuleChat.body().warn("Losing").text(", getting away.")
                        );
                    }

                    this.poppedOut = false;
                    this.steer(player, target, rival);
                    double distance = player.distanceTo(target);
                    if (distance < this.closest - 0.5) {
                        this.closest = distance;
                        this.closerAt = this.tick;
                    }

                    if (this.throwing == null || !this.keepThrowing(player, target)) {
                        if (this.stance != CrystalBotModule.Stance.RUN || !this.getAway(level, player, target)) {
                            if (!this.getDown(level, player, target)) {
                                if (!this.guardAnchors.get() || !this.defuse(level, player) && !this.guard(level, player, target)) {
                                    if (!this.launching || !this.launch(player, target)) {
                                        if (this.followLaid || this.tick > this.followUntil || !this.followUp(level, player, target)) {
                                            boolean punish = target.isUsingItem()
                                                && target.getUseItem().has(DataComponents.FOOD)
                                                && totems(player) >= 1
                                                && player.getHealth() > 8.0F;
                                            if (low && !punish) {
                                                this.needsGapple = true;
                                            }

                                            if (this.needsGapple || this.eating && player.isUsingItem()) {
                                                if (distance >= 8.0) {
                                                    if (BotBody.hotbarSlot(player, GAPPLE) >= 0) {
                                                        if (this.hands() && this.body.use(this.mc, player, GAPPLE)) {
                                                            this.eating = true;
                                                        }

                                                        this.body.face(this.mc, player, target, 1.0F);
                                                        this.body.backOff(this.mc, player);
                                                        return;
                                                    }
                                                } else if (this.critical(player) && BotBody.hotbarSlot(player, GAPPLE) >= 0) {
                                                    this.eating = false;
                                                    this.retreatAt = this.tick;
                                                    if (this.pearlAway(level, player, target)) {
                                                        return;
                                                    }

                                                    this.body.flee(this.mc, player, target);
                                                    return;
                                                }
                                            }

                                            this.eating = false;
                                            if (this.cover(level, player, target, distance)) {
                                                this.body.face(this.mc, player, target, 1.0F);
                                            } else if (this.stance == CrystalBotModule.Stance.GUARD && this.closeSpot(level, player, target)) {
                                                this.body.face(this.mc, player, target, 1.0F);
                                            } else if (!this.breach.get() || !this.breakHole(level, player, target, rival)) {
                                                if (!this.mendArmor.get() || !(distance >= 12.0) || !armorLow(player) || !this.hands() || !this.throwXp(player)
                                                    )
                                                 {
                                                    this.mending = false;
                                                    if (!this.chase.get() || !this.chasePearl(level, player, target, rival)) {
                                                        boolean calm = distance >= 8.0 || BotBody.hotbarSlot(player, stack -> stack.is(Items.END_CRYSTAL)) < 0;
                                                        if (this.refill.get()
                                                            && calm
                                                            && this.kit.needed(player, this.anchors.get())
                                                            && this.hands()
                                                            && this.kit.refill(this.mc, player, this.anchors.get())) {
                                                            this.body.face(this.mc, player, target, 1.0F);
                                                        } else if (!this.finish(player, target)) {
                                                            if (!this.launches.get() || !this.startLaunch(player, target, rival)) {
                                                                if (!this.digDown(level, player, target, distance)
                                                                    && !this.digBase(level, player, target, distance)) {
                                                                    CombatMacroLock.release(this);
                                                                    handBack(player);
                                                                    this.body.face(this.mc, player, target, 1.0F);
                                                                    if (!surrounded(level, player)) {
                                                                        if (!this.stayLow(level, player, target, distance)) {
                                                                            if (this.tick >= this.standScanAt) {
                                                                                this.standScanAt = this.tick + 10;
                                                                                this.standing = StandPicker.pick(
                                                                                    level, player, target, 2.4, 3.2, 4.0, this.stance.cover
                                                                                );
                                                                            }

                                                                            if (this.standing == null
                                                                                || this.body.toward(this.mc, player, this.standing.center())) {
                                                                                this.standing = null;
                                                                                this.body
                                                                                    .keepDistance(this.mc, player, target, 2.4, 3.2, 4.0, this.dance.get());
                                                                            }
                                                                        }
                                                                    }
                                                                } else {
                                                                    this.body.face(this.mc, player, target, 1.0F);
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            this.body.forget();
            CombatMacroLock.release(this);
            this.throwing = null;
            this.launching = false;
        }
    }

    private Player choose(ClientLevel level, LocalPlayer player, Player kept) {
        Player best = null;
        double bestWorth = Double.NEGATIVE_INFINITY;

        for (Player other : enemies(level, player, this.range.get())) {
            double worth = this.worth(player, other);
            if (worth > bestWorth) {
                best = other;
                bestWorth = worth;
            }
        }

        if (kept == null || !kept.isAlive() || this.fought == null) {
            this.challenger = null;
            if (best != null) {
                this.body.hold(best);
            }

            return best;
        } else if (best != null && best != kept) {
            boolean close = player.distanceTo(kept) <= 6.0;
            if (bestWorth < this.worth(player, kept) + (close ? 8.0 : 2.0)) {
                this.challenger = null;
                return kept;
            }

            if (best != this.challenger) {
                this.challenger = best;
                this.challengerSince = this.tick;
                return kept;
            }

            if (this.tick - this.challengerSince < (close ? 20 : 6)) {
                return kept;
            }

            this.challenger = null;
            this.body.hold(best);
            return best;
        } else {
            this.challenger = null;
            return kept;
        }
    }

    private double worth(LocalPlayer player, Player other) {
        double distance = player.distanceTo(other);
        double near = distance <= 6.0 ? 1.0 : (distance >= 10.0 ? 0.0 : (10.0 - distance) / 4.0);
        double worth = 4.0 * near - 0.2 * distance + (20.0F - Math.min(20.0F, CrystalDamage.health(other))) / 5.0;
        if (!other.getOffhandItem().is(Items.TOTEM_OF_UNDYING) && !other.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
            worth += 1.5;
        }

        if (armorLow(other)) {
            worth++;
        }

        if (other.getId() == this.attackedBy && this.tick - this.attackedAt <= 60 && distance <= 4.5) {
            worth += 4.0;
        }

        return worth;
    }

    private boolean finish(LocalPlayer player, Player target) {
        if (this.pause.slowServer() || target.hurtTime > 0 || reach(player, target) > 2.95 || CrystalDamage.health(target) > 3.0F) {
            return false;
        }

        if (!MeleeAttacks.isMeleeWeapon(player.getMainHandItem()) || MeleeAttacks.charge(player) < 0.9F) {
            return false;
        }

        if (!this.hands()) {
            return true;
        }

        this.body.face(this.mc, player, target, 1.0F);
        this.strike(player, target);
        return true;
    }

    private void newFight(Player target) {
        if (this.fought != null && this.lowSpell && this.pearledInSpell && this.learn.get()) {
            this.book.of(this.fought.getUUID()).lowSpell(true);
        }

        this.fought = target;
        this.launching = false;
        this.launchedAt = this.theirPopAt = this.holeSince = -1;
        this.lowSpell = this.pearledInSpell = false;
        this.theirPearl = this.followedPearl = -1;
        this.openerArm = -1;
        this.closest = Double.MAX_VALUE;
        this.closerAt = this.tick;
        this.standing = null;
        this.followLaid = true;
        this.blocking = null;
        this.cityFails = 0;
        this.air.forget();
        this.forgetDig();
        this.progressAt = this.hurtAt = this.tick;
        this.lastTheirHealth = target == null ? 0.0F : CrystalDamage.health(target);
        if (target != null) {
            ModuleChat.send(this, ModuleChat.body().text("Fighting ").name(target.getName().getString()).text("."));
            if (this.learn.get()) {
                this.book.of(target.getUUID()).fought();
            }
        } else {
            ModuleChat.send(this, ModuleChat.body().text("No enemy in range, waiting."));
        }
    }

    private CrystalBotModule.Stance stance(ClientLevel level, LocalPlayer player, Player target, RivalBook.Rival rival) {
        int totems = totems(player);
        float health = player.getHealth() + player.getAbsorptionAmount();
        float risk = worstThreat(level, player, this.digAnchor) / Math.max(1.0F, health);
        int near = enemiesWithin(level, player, 8.0);
        boolean run = totems == 0 && near >= 2;
        if (run) {
            this.runUntil = Math.max(this.runUntil, this.tick + 40);
        }

        if (this.popStreak >= 3) {
            this.popStreak = 0;
            this.poppedOut = true;
            this.runUntil = this.tick + 200;
        }

        if (this.tick < this.runUntil) {
            return CrystalBotModule.Stance.RUN;
        }

        if (this.tick - this.ownPopAt <= 40) {
            return CrystalBotModule.Stance.GUARD;
        }

        if (this.finishing(target, rival)) {
            return CrystalBotModule.Stance.PRESS;
        }

        boolean guard = health <= this.retreatAt() + 2.0F || totems <= 1 || risk >= 0.35F;
        return guard && health <= 8.0F ? CrystalBotModule.Stance.GUARD : CrystalBotModule.Stance.PRESS;
    }

    private boolean finishing(Player target, RivalBook.Rival rival) {
        if (this.theirPopAt >= 0 && !target.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            int window = rival == null ? 8 : Mth.clamp(rival.retotemWindow(8), 3, 30);
            return this.tick - this.theirPopAt <= window;
        } else {
            return false;
        }
    }

    private void steer(LocalPlayer player, Player target, RivalBook.Rival rival) {
        float shift = 2.0F;
        float health = player.getHealth() + player.getAbsorptionAmount();
        float room = health - (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING) ? 1.0F : 4.0F);
        float selfCap = Math.max(0.5F, Math.min(this.stance.selfCap + shift, room));
        this.selfCapNow = selfCap;
        float min = this.finishing(target, rival) ? 2.0F : this.stance.minDamage;
        if (feetY(player) >= Mth.floor(target.getY() + 0.01) && !Combo.inHole(target)) {
            min = Math.min(min, 2.5F);
        }

        CrystalAuraModule crystal = ModuleRegistry.get(CrystalAuraModule.class);
        if (crystal != null) {
            crystal.steer(player, min, selfCap);
            crystal.crystalsFrom(player, feetY(player) + 1);
            crystal.fullSpeed(player);
            crystal.buildEagerly(player);
        }

        AnchorAuraModule anchor = ModuleRegistry.get(AnchorAuraModule.class);
        if (anchor != null) {
            anchor.steer(player, this.finishing(target, rival) ? 2.0F : this.stance.anchorMin, Math.min(selfCap, 11.0F));
        }
    }

    private float eatAt() {
        return this.eatBelow.get() - 2.0F;
    }

    private float retreatAt() {
        return 4.0F;
    }

    private boolean critical(LocalPlayer player) {
        return player.getHealth() + player.getAbsorptionAmount() <= this.retreatAt() && !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    private static int totems(LocalPlayer player) {
        int count = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING) ? 1 : 0;
        Inventory inventory = player.getInventory();

        for (int i = 0; i < 36; i++) {
            if (inventory.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                count += inventory.getItem(i).getCount();
            }
        }

        return count;
    }

    private static float worstThreat(ClientLevel level, LocalPlayer player, BlockPos ours) {
        List<Player> enemies = enemies(level, player, 16.0);
        float worst = 0.0F;

        for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, player.getBoundingBox().inflate(12.0))) {
            Vec3 center = crystal.position();
            float us = CrystalDamage.estimate(center, player);
            if (us > worst && worthIt(enemies, crystal.getBoundingBox(), center, us, 4.0, 6.0F, null)) {
                worst = us;
            }
        }

        BlockPos feet = player.blockPosition();

        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-5, -2, -5), feet.offset(5, 3, 5))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) > 0 && !pos.equals(ours)) {
                BlockPos anchor = pos.immutable();
                Vec3 center = Vec3.atCenterOf(anchor);
                float us = CrystalDamage.estimate(center, player, 5.0F, anchor);
                if (us > worst && worthIt(enemies, new AABB(anchor), center, us, 5.5, 5.0F, anchor)) {
                    worst = us;
                }
            }
        }

        return worst;
    }

    private static boolean worthIt(List<Player> enemies, AABB source, Vec3 center, float us, double reach, float power, BlockPos ignored) {
        for (Player enemy : enemies) {
            Vec3 eye = enemy.getEyePosition();
            if (!(eye.distanceToSqr(TargetingUtils.closestPoint(eye, source)) > reach * reach) && CrystalDamage.estimate(center, enemy, power, ignored) < us) {
                return true;
            }
        }

        return false;
    }

    private static int enemiesWithin(ClientLevel level, LocalPlayer player, double reach) {
        int count = 0;

        for (Player other : level.players()) {
            if (other != player && other.isAlive() && MeleeAttacks.isEnemy(player, other) && other.distanceTo(player) <= reach) {
                count++;
            }
        }

        return count;
    }

    private static List<Player> enemies(ClientLevel level, LocalPlayer player, double reach) {
        List<Player> enemies = new ArrayList<>();

        for (Player other : level.players()) {
            if (other != player && other.isAlive() && MeleeAttacks.isEnemy(player, other) && other.distanceTo(player) <= reach) {
                enemies.add(other);
            }
        }

        return enemies;
    }

    private void runAuras(ClientLevel level) {
        CrystalAuraModule crystal = ModuleRegistry.get(CrystalAuraModule.class);
        if (crystal != null && !crystal.isEnabled()) {
            if (this.crystalWasOn == null) {
                this.crystalWasOn = false;
            }

            crystal.setEnabled(true);
        }

        AnchorAuraModule anchor = ModuleRegistry.get(AnchorAuraModule.class);
        boolean wantAnchors = this.anchors.get() && level.dimension() != Level.NETHER;
        if (anchor != null && wantAnchors && !anchor.isEnabled()) {
            if (this.anchorWasOn == null) {
                this.anchorWasOn = false;
            }

            anchor.setEnabled(true);
        }
    }

    private void keepTotem() {
        AutoInvTotemModule inventory = ModuleRegistry.get(AutoInvTotemModule.class);
        AutoTotemModule plain = ModuleRegistry.get(AutoTotemModule.class);
        if (inventory != null && !inventory.isEnabled() && (plain == null || !plain.isEnabled())) {
            if (this.totemWasOn == null) {
                this.totemWasOn = false;
            }

            inventory.setEnabled(true);
        }
    }

    private void restoreAuras() {
        CrystalAuraModule crystal = ModuleRegistry.get(CrystalAuraModule.class);
        if (crystal != null && Boolean.FALSE.equals(this.crystalWasOn)) {
            crystal.setEnabled(false);
        }

        AnchorAuraModule anchor = ModuleRegistry.get(AnchorAuraModule.class);
        if (anchor != null && Boolean.FALSE.equals(this.anchorWasOn)) {
            anchor.setEnabled(false);
        }

        AutoInvTotemModule totem = ModuleRegistry.get(AutoInvTotemModule.class);
        if (totem != null && Boolean.FALSE.equals(this.totemWasOn)) {
            totem.setEnabled(false);
        }

        this.crystalWasOn = this.anchorWasOn = this.totemWasOn = null;
    }

    private static boolean blastNear(ClientLevel level, LocalPlayer player) {
        for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, player.getBoundingBox().inflate(7.0))) {
            if (CrystalDamage.estimate(crystal.position(), player) >= 1.0F) {
                return true;
            }
        }

        BlockPos feet = player.blockPosition();
        int reach = (int)Math.ceil(7.0);

        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-reach, -2, -reach), feet.offset(reach, 3, reach))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) > 0 && Vec3.atCenterOf(pos).distanceTo(player.position()) <= 7.0) {
                return true;
            }
        }

        return false;
    }

    private static void handBack(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (!main.is(Items.END_CRYSTAL)
            && !main.is(Items.OBSIDIAN)
            && !main.is(Items.RESPAWN_ANCHOR)
            && !main.is(Items.GLOWSTONE)
            && !Crystals.isCombatItem(main)) {
            int slot = BotBody.hotbarSlot(player, stack -> stack.is(Items.END_CRYSTAL));
            if (slot < 0) {
                slot = BotBody.hotbarSlot(player, MeleeAttacks::isMeleeWeapon);
            }

            if (slot >= 0) {
                player.getInventory().setSelectedSlot(slot);
            }
        }
    }

    private boolean hands() {
        CombatMacroLock.take(this);
        return !MeleeAttacks.actedThisTick(this.mc.player);
    }

    private void watch(ClientLevel level, Player target, RivalBook.Rival rival) {
        float theirs = CrystalDamage.health(target);
        if (theirs < this.lastTheirHealth - 0.5F) {
            this.hurtAt = this.tick;
        }

        if (theirs < this.lastTheirHealth - 0.5F || !Combo.inHole(target)) {
            this.progressAt = this.tick;
        }

        this.lastTheirHealth = theirs;
        if (this.openerArm >= 0 && this.tick - this.openerAt >= 40) {
            float taken = this.openerHealth - theirs + 10.0F * this.openerPops;
            if (rival != null) {
                rival.opened(this.openerArm, this.openerReach, Mth.clamp(taken / 12.0F, 0.0F, 1.5F));
            }

            this.openerArm = -1;
        }

        if (this.launchedAt >= 0) {
            this.launchPeak = Math.max(this.launchPeak, target.getY() - this.launchY);
            if (this.tick - this.launchedAt >= 8) {
                if (rival != null) {
                    rival.launched(this.launchPeak >= 0.3);
                }

                this.launchedAt = -1;
            }
        }

        if (this.theirPopAt >= 0 && rival != null) {
            if (target.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                rival.retotemed(this.tick - this.theirPopAt);
                this.theirPopAt = -1;
            } else if (this.tick - this.theirPopAt > 100) {
                rival.retotemed(100);
                this.theirPopAt = -1;
            }
        } else if (this.theirPopAt >= 0 && this.tick - this.theirPopAt > 100) {
            this.theirPopAt = -1;
        }

        if (Combo.inHole(target)) {
            if (this.holeSince < 0) {
                this.holeSince = this.tick;
            }
        } else if (this.holeSince >= 0) {
            if (rival != null) {
                rival.leftHole(Math.min(600, this.tick - this.holeSince));
            }

            this.holeSince = -1;
        }

        if (!this.lowSpell && theirs <= 8.0F) {
            this.lowSpell = true;
            this.pearledInSpell = false;
        } else if (this.lowSpell && theirs > 12.0F) {
            if (rival != null) {
                rival.lowSpell(this.pearledInSpell);
            }

            this.lowSpell = false;
        }

        for (ThrownEnderpearl pearl : level.getEntitiesOfClass(ThrownEnderpearl.class, target.getBoundingBox().inflate(4.0))) {
            if (pearl.getOwner() == target && pearl.tickCount <= 2 && pearl.getId() != this.followedPearl) {
                if (this.lowSpell) {
                    this.pearledInSpell = true;
                }

                this.theirPearl = pearl.getId();
            }
        }
    }

    private boolean getAway(ClientLevel level, LocalPlayer player, Player target) {
        if (player.distanceTo(target) < 12.0 && this.pearlAway(level, player, target)) {
            return true;
        } else if (player.distanceTo(target) >= 16.0 && player.getHealth() <= this.eatAt() + 4.0F && this.hands() && this.body.use(this.mc, player, GAPPLE)) {
            this.eating = true;
            return true;
        } else {
            this.body.flee(this.mc, player, target);
            return true;
        }
    }

    private boolean pearlAway(ClientLevel level, LocalPlayer player, Player target) {
        if (this.escape.get() && this.pearlReady(player) && this.tick >= this.searchAfter) {
            List<Player> enemies = enemies(level, player, 48.0);
            Rotation look = PearlFlight.escape(player, enemies, 20.0);
            this.throwAway = 16.0;
            if (look == null) {
                look = PearlFlight.escape(player, enemies, 12.0);
                this.throwAway = 12.0;
            }

            if (look == null) {
                this.searchAfter = this.tick + 10;
                return false;
            } else {
                this.startThrow(CrystalBotModule.Throw.ESCAPE, look, null);
                return this.keepThrowing(player, target);
            }
        } else {
            return false;
        }
    }

    private boolean pearlReady(LocalPlayer player) {
        return this.tick - this.pearlAt >= 30
            && BotBody.hotbarSlot(player, PEARL) >= 0
            && !player.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))
            && (player.getHealth() + player.getAbsorptionAmount() > 6.0F || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING));
    }

    private void startThrow(CrystalBotModule.Throw kind, Rotation look, Vec3 spot) {
        this.throwing = kind;
        this.throwLook = look;
        this.throwSpot = spot;
        this.throwUntil = this.tick + 14;
    }

    private boolean keepThrowing(LocalPlayer player, Player target) {
        if (this.tick > this.throwUntil || !this.pearlReady(player)) {
            this.throwing = null;
            this.pearlAt = this.tick - 15;
            return false;
        }

        if (!this.hands()) {
            return true;
        }

        Aim.turn(this.mc, this.throwLook.yaw(), this.throwLook.pitch(), 27.0F);
        int slot = BotBody.hotbarSlot(player, PEARL);
        if (player.getInventory().getSelectedSlot() != slot) {
            player.getInventory().setSelectedSlot(slot);
            return true;
        }

        if (!ServerRotation.desynced() && !player.isUsingItem() && MeleeAttacks.handsFree(this.mc, player)) {
            if (player.onGround() && !(player.getKnownMovement().horizontalDistanceSqr() > 1.0E-4)) {
                float yaw = player.getYRot();
                float pitch = player.getXRot();
                Rotation sent = ServerRotation.commit(player);
                if (!(Math.abs(Mth.wrapDegrees(sent.yaw() - yaw)) > 0.01F) && !(Math.abs(sent.pitch() - pitch) > 0.01F)) {
                    boolean good = this.throwing == CrystalBotModule.Throw.ESCAPE
                        ? PearlFlight.escapes(player, yaw, pitch, enemies(this.mc.level, player, 48.0), this.throwAway)
                        : PearlFlight.reaches(player, yaw, pitch, this.throwSpot, 0.0, 3.0);
                    if (!good) {
                        return true;
                    }

                    if (this.mc.gameMode.useItem(player, InteractionHand.MAIN_HAND).consumesAction()) {
                        player.swing(InteractionHand.MAIN_HAND);
                        MeleeAttacks.claimTick(this.mc);
                        this.pearlAt = this.tick;
                        if (this.throwing == CrystalBotModule.Throw.ESCAPE) {
                            ModuleChat.send(this, ModuleChat.body().warn("Pearled away").text("."));
                        } else {
                            ModuleChat.send(this, ModuleChat.body().text("Pearled after ").name(target.getName().getString()).text("."));
                        }
                    }

                    this.throwing = null;
                    return true;
                } else {
                    return true;
                }
            } else {
                return true;
            }
        } else {
            return true;
        }
    }

    private boolean chasePearl(ClientLevel level, LocalPlayer player, Player target, RivalBook.Rival rival) {
        if ((this.stance == CrystalBotModule.Stance.PRESS || this.stance == CrystalBotModule.Stance.HOLD) && this.pearlReady(player)) {
            float health = player.getHealth() + player.getAbsorptionAmount();
            if (health < 6.0F) {
                return false;
            }

            int pearls = count(player, PEARL);
            boolean following = this.theirPearl >= 0;
            boolean keepOne = totems(player) == 0 && player.getHealth() <= 12.0F
                || !following && rival != null && rival.pearlsWhenLow() && CrystalDamage.health(target) <= 8.0F;
            if (pearls <= (keepOne ? 1 : 0)) {
                return false;
            }

            Vec3 spot = null;
            if (following) {
                this.followedPearl = this.theirPearl;
                this.theirPearl = -1;
                if (level.getEntity(this.followedPearl) instanceof ThrownEnderpearl pearl) {
                    PearlFlight.Landing landing = PearlFlight.fly(level, pearl, pearl.position(), pearl.getDeltaMovement());
                    if (landing != null && landing.at().distanceTo(player.position()) > 8.0) {
                        spot = landing.at();
                    }
                }
            }

            double distance = player.distanceTo(target);
            if (spot == null && distance <= this.range.get() + 8.0) {
                Vec3 away = target.position().subtract(player.position()).multiply(1.0, 0.0, 1.0).normalize();
                Vec3 motion = target.position().subtract(target.xo, target.yo, target.zo);
                boolean running = distance >= 12.0 && motion.x * away.x + motion.z * away.z > 0.12;
                BlockPos under = target.blockPosition().below();
                boolean standing = !level.getBlockState(under).getCollisionShape(level, under).isEmpty();
                boolean above = distance >= 3.0 && target.getY() - player.getY() >= 3.0 && standing;
                boolean below = distance >= 4.0 && player.getY() - target.getY() >= 4.0 && standing;
                boolean stuck = distance > 6.0 && this.tick - this.closerAt > 60;
                if (running || above || below || stuck) {
                    spot = target.position().add(motion.multiply(1.0, 0.0, 1.0).scale(PearlFlight.flightTicks(distance)));
                }

                if (spot == null && !target.onGround() && distance >= 6.0) {
                    AirTrack.Landing down = this.air.landing(this.tick);
                    if (down != null && target.getY() - down.at().y >= 3.0) {
                        spot = down.at();
                    }
                }
            }

            if (spot != null && this.tick >= this.searchAfter) {
                Rotation look = PearlFlight.toward(player, spot, 0.8, 3.0);
                if (look == null) {
                    this.searchAfter = this.tick + 10;
                    return false;
                } else {
                    this.startThrow(CrystalBotModule.Throw.CHASE, look, spot);
                    return this.keepThrowing(player, target);
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean getDown(ClientLevel level, LocalPlayer player, Player target) {
        double drop = player.getY() - target.getY();
        double sideways = Math.hypot(target.getX() - player.getX(), target.getZ() - player.getZ());
        if (!(drop < 3.0) && !(sideways > 8.0) && this.stance != CrystalBotModule.Stance.RUN && !this.critical(player)) {
            if (this.belowSince < 0) {
                this.belowSince = this.tick;
            }

            if (this.tick - this.belowSince >= 20 && player.onGround() && target.onGround()) {
                if (this.digAnchor != null && !level.getBlockState(this.digAnchor).is(Blocks.RESPAWN_ANCHOR)) {
                    if (this.digPlaced) {
                        this.digBlasts++;
                        this.digWalkUntil = this.tick + 40;
                    }

                    this.digAnchor = null;
                }

                if (this.tick >= this.digWalkUntil && !openDrop(level, player, target)) {
                    boolean blast = drop <= 6.0
                        && this.digBlasts < 2
                        && this.tick >= this.digAfter
                        && this.anchors.get()
                        && level.dimension() != Level.NETHER
                        && BotBody.hotbarSlot(player, ANCHOR) >= 0
                        && BotBody.hotbarSlot(player, GLOWSTONE) >= 0;
                    return blast && this.blastDown(level, player, target) ? true : this.pearlDown(player, target);
                } else {
                    this.body.face(this.mc, player, target, 1.0F);
                    this.body.dropToward(this.mc, player, target.position());
                    return true;
                }
            } else {
                return false;
            }
        } else {
            this.forgetDig();
            return false;
        }
    }

    private boolean blastDown(ClientLevel level, LocalPlayer player, Player target) {
        if (this.digAnchor == null) {
            this.digAnchor = digCell(level, player, target);
            if (this.digAnchor == null) {
                this.digAfter = this.tick + 100;
                return false;
            }

            this.digPlaced = false;
            this.digGuards = 0;
            this.digUntil = this.tick + 80;
            if (!this.digAnnounced) {
                ModuleChat.send(this, ModuleChat.body().text("Blasting down to ").name(target.getName().getString()).text("."));
            }

            this.digAnnounced = true;
        }

        if (this.tick > this.digUntil) {
            this.digBlasts = 2;
            this.digAnchor = null;
            return false;
        }

        this.body.face(this.mc, player, target, 1.0F);
        BlockState state = level.getBlockState(this.digAnchor);
        if (!state.is(Blocks.RESPAWN_ANCHOR)) {
            AnchorGuard.Spot spot = state.canBeReplaced() ? AnchorGuard.supportFor(level, player, player.getEyePosition(), this.digAnchor) : null;
            if (spot == null) {
                this.digAnchor = null;
                this.digAfter = this.tick + 100;
                return false;
            }

            if (this.placeAt(level, player, spot, BotBody.hotbarSlot(player, ANCHOR)) == 1) {
                this.digPlaced = true;
            }

            return true;
        } else {
            if (state.getValue(RespawnAnchorBlock.CHARGE) <= 0) {
                return this.clickDigAnchor(level, player, BotBody.hotbarSlot(player, GLOWSTONE));
            }

            Vec3 aim = AnchorGuard.anchorAim(level, player, this.digAnchor);
            if (aim == null) {
                this.digAnchor = null;
                return false;
            }

            int shieldSlot = guardBlockSlot(player);
            if (this.digGuards < 2 && shieldSlot >= 0) {
                AnchorGuard.Spot shield = AnchorGuard.find(level, player, this.digAnchor, aim);
                if (shield != null && !shield.standing()) {
                    if (this.placeAt(level, player, shield, shieldSlot) == 1) {
                        this.digGuards++;
                    }

                    return true;
                }
            }

            float own = CrystalDamage.estimate(Vec3.atCenterOf(this.digAnchor), player, 5.0F, this.digAnchor);
            int route = AnchorGuard.detonatorRoute(player.getInventory(), player.getMainHandItem());
            if (!(own > Math.min(this.selfCapNow, 10.0F)) && !CrystalDamage.endangers(player, own) && route != -1) {
                return this.clickDigAnchor(level, player, route == -2 ? player.getInventory().getSelectedSlot() : route);
            }

            this.digBlasts = 2;
            this.digAnchor = null;
            return false;
        }
    }

    private static BlockPos digCell(ClientLevel level, LocalPlayer player, Player target) {
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange() - 0.5;
        BlockPos feet = player.blockPosition();
        List<BlockPos> cells = new ArrayList<>();

        for (BlockPos cell : BlockPos.betweenClosed(feet.offset(-4, -2, -4), feet.offset(4, 0, 4))) {
            if (!(eye.distanceTo(Vec3.atCenterOf(cell)) > reach)) {
                BlockPos under = cell.below();
                if (level.getBlockState(cell).canBeReplaced()
                    && level.getFluidState(cell).isEmpty()
                    && level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)) {
                    AABB box = new AABB(cell);
                    if (!box.intersects(player.getBoundingBox()) && !box.intersects(target.getBoundingBox())) {
                        cells.add(cell.immutable());
                    }
                }
            }
        }

        cells.sort(Comparator.comparingDouble(cellx -> Vec3.atCenterOf(cellx).distanceToSqr(target.position())));
        BlockPos fallback = null;

        for (BlockPos cell : cells.subList(0, Math.min(cells.size(), 8))) {
            if (AnchorGuard.supportFor(level, player, eye, cell) != null) {
                if (fallback == null) {
                    fallback = cell;
                }

                Vec3 sight = TargetingUtils.closestPoint(eye, new AABB(cell).deflate(0.1));
                if (AnchorGuard.find(level, player, cell, sight) != null) {
                    return cell;
                }
            }
        }

        return fallback;
    }

    private static boolean openDrop(ClientLevel level, LocalPlayer player, Player target) {
        Vec3 way = target.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
        if (way.lengthSqr() < 0.25) {
            return false;
        }

        Vec3 step = player.position().add(way.normalize().scale(0.8));
        BlockPos column = BlockPos.containing(step.x, player.getY() + 0.1, step.z);
        int floor = Mth.floor(target.getY() + 0.01);

        for (BlockPos pos = column; pos.getY() >= floor - 1; pos = pos.below()) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.LAVA) || state.is(BlockTags.FIRE)) {
                return false;
            }

            if (!state.getCollisionShape(level, pos).isEmpty()) {
                return pos.getY() < player.getY() - 1.5 && pos.getY() <= floor + 1;
            }
        }

        return false;
    }

    private boolean clickDigAnchor(ClientLevel level, LocalPlayer player, int slot) {
        if (slot < 0) {
            this.digAnchor = null;
            return false;
        }

        if (!this.hands()) {
            return true;
        }

        BlockHitResult hit = AnchorGuard.anchorHit(level, player, this.digAnchor);
        if (hit == null) {
            Vec3 aim = AnchorGuard.anchorAim(level, player, this.digAnchor);
            if (aim == null) {
                this.digAnchor = null;
                return false;
            } else {
                ServerRotation.request(RotationUtils.fromTo(player.getEyePosition(), aim), 6.0F);
                return true;
            }
        } else {
            if (player.getInventory().getSelectedSlot() != slot) {
                player.getInventory().setSelectedSlot(slot);
                return true;
            }

            if (MeleeAttacks.handsFree(this.mc, player)) {
                AnchorGuard.click(this.mc, player, hit);
            }

            return true;
        }
    }

    private boolean pearlDown(LocalPlayer player, Player target) {
        if (this.chase.get() && this.pearlReady(player) && this.tick >= this.searchAfter) {
            Vec3 spot = target.position();
            Rotation look = PearlFlight.toward(player, spot, 0.8, 3.0);
            if (look == null) {
                this.searchAfter = this.tick + 10;
                return false;
            } else {
                this.startThrow(CrystalBotModule.Throw.CHASE, look, spot);
                return this.keepThrowing(player, target);
            }
        } else {
            return false;
        }
    }

    private static int feetY(LocalPlayer player) {
        return Mth.floor(player.getY() + 0.01);
    }

    private boolean wantsLower(LocalPlayer player, Player target, double distance) {
        return this.stance != CrystalBotModule.Stance.RUN
            && player.onGround()
            && target.onGround()
            && !Combo.inHole(target)
            && distance <= 5.5
            && feetY(player) == Mth.floor(target.getY() + 0.01)
            && !this.justLaunched();
    }

    private boolean justLaunched() {
        return this.launching || this.launchedAt >= 0 || this.tick <= this.followUntil + 20;
    }

    private boolean stayLow(ClientLevel level, LocalPlayer player, Player target, double distance) {
        if (this.stance != CrystalBotModule.Stance.RUN && player.onGround() && target.onGround() && !Combo.inHole(target) && !(distance > 5.5)) {
            if (feetY(player) < Mth.floor(target.getY() + 0.01)) {
                return true;
            }

            if (!this.wantsLower(player, target, distance)) {
                return false;
            }

            BlockPos step = lowerStep(level, player, target, feetY(player) - 1);
            if (step == null) {
                return false;
            }

            this.body.toward(this.mc, player, Vec3.atBottomCenterOf(step));
            return true;
        } else {
            return false;
        }
    }

    private boolean digDown(ClientLevel level, LocalPlayer player, Player target, double distance) {
        if (this.wantsLower(player, target, distance) && this.tick >= this.stepDigAfter) {
            if (lowerStep(level, player, target, feetY(player) - 1) != null) {
                return false;
            }

            BlockPos under = player.blockPosition().below();
            BlockState state = level.getBlockState(under);
            if (BotBody.hotbarSlot(player, OBSIDIAN) < 0 && !Crystals.isBase(state)) {
                return false;
            }

            BlockPos landing = under.below();
            boolean diggable = !state.hasBlockEntity()
                && digTicks(level, player, under) <= 50
                && !level.getBlockState(landing).getCollisionShape(level, landing).isEmpty()
                && !nearLava(level, under);
            if (diggable && this.mine(level, player, under)) {
                return true;
            }

            this.stepDigAfter = this.tick + 100;
            return false;
        } else {
            return false;
        }
    }

    private boolean digBase(ClientLevel level, LocalPlayer player, Player target, double distance) {
        int theirs = Mth.floor(target.getY() + 0.01);
        if (this.stance != CrystalBotModule.Stance.RUN
            && player.onGround()
            && target.onGround()
            && !Combo.inHole(target)
            && !(distance > 5.5)
            && feetY(player) == theirs - 1
            && this.tick >= this.baseDigAfter
            && BotBody.hotbarSlot(player, OBSIDIAN) >= 0
            && !this.justLaunched()) {
            Vec3 eye = player.getEyePosition();
            BlockPos theirFeet = target.blockPosition();
            BlockPos best = null;
            double nearest = Double.MAX_VALUE;

            for (BlockPos pos : BlockPos.betweenClosed(theirFeet.offset(-2, -1, -2), theirFeet.offset(2, -1, 2))) {
                BlockState state = level.getBlockState(pos);
                if (Crystals.isBase(state) && Crystals.canHost(level, pos, null)) {
                    return false;
                }

                if ((pos.getX() != theirFeet.getX() || pos.getZ() != theirFeet.getZ())
                    && Math.max(Math.abs(pos.getX() - player.getBlockX()), Math.abs(pos.getZ() - player.getBlockZ())) >= 2
                    && !state.canBeReplaced()
                    && !state.hasBlockEntity()
                    && level.isEmptyBlock(pos.above())) {
                    Vec3 top = Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0);
                    double reach = top.distanceTo(target.position());
                    if (!(reach > 2.5)
                        && !(eye.distanceTo(top) > player.blockInteractionRange() - 0.5)
                        && digTicks(level, player, pos) <= 25
                        && !(reach >= nearest)) {
                        best = pos.immutable();
                        nearest = reach;
                    }
                }
            }

            if (best != null && this.mine(level, player, best)) {
                return true;
            }

            this.baseDigAfter = this.tick + 100;
            return false;
        } else {
            return false;
        }
    }

    private static BlockPos lowerStep(ClientLevel level, LocalPlayer player, Player target, int feet) {
        BlockPos here = player.blockPosition();
        BlockPos best = null;
        double nearest = Double.MAX_VALUE;

        for (BlockPos cell : BlockPos.betweenClosed(here.offset(-2, -1, -2), here.offset(2, -1, 2))) {
            BlockPos spot = StandPicker.standable(level, cell);
            if (spot != null && spot.getY() == feet && spot.equals(cell)) {
                Vec3 at = Vec3.atBottomCenterOf(spot);
                double reach = Math.hypot(at.x - target.getX(), at.z - target.getZ());
                if (!(reach < 2.4) && !(reach > 4.5) && walkable(level, player.position(), at)) {
                    double distance = spot.distSqr(here);
                    if (distance < nearest) {
                        best = spot.immutable();
                        nearest = distance;
                    }
                }
            }
        }

        return best;
    }

    private static int digTicks(ClientLevel level, LocalPlayer player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F) {
            return Integer.MAX_VALUE;
        }

        if (hardness == 0.0F) {
            return 1;
        }

        int slot = toolSlot(player.getInventory(), state);
        ItemStack tool = slot < 0 ? ItemStack.EMPTY : player.getInventory().getItem(slot);
        float speed = tool.getDestroySpeed(state);
        if (speed > 1.0F) {
            Registry<Enchantment> enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            int efficiency = enchantments.get(Enchantments.EFFICIENCY).map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, tool)).orElse(0);
            if (efficiency > 0) {
                speed += efficiency * efficiency + 1;
            }
        }

        boolean right = tool.isCorrectToolForDrops(state) || !state.requiresCorrectToolForDrops();
        return Mth.ceil(hardness * (right ? 30.0F : 100.0F) / speed);
    }

    private static boolean nearLava(ClientLevel level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            if (level.getFluidState(pos.relative(side)).is(FluidTags.LAVA)) {
                return true;
            }
        }

        return false;
    }

    private void forgetDig() {
        this.digAnchor = null;
        this.digPlaced = this.digAnnounced = false;
        this.digGuards = this.digBlasts = 0;
        this.digUntil = this.digWalkUntil = this.digAfter = 0;
        this.belowSince = -1;
    }

    private boolean startLaunch(LocalPlayer player, Player target, RivalBook.Rival rival) {
        if (player.tickCount - this.diggingAt <= 1) {
            return false;
        }

        if (this.tick >= this.nextLaunch
            && !this.pause.slowServer()
            && (this.stance == CrystalBotModule.Stance.PRESS || this.stance == CrystalBotModule.Stance.HOLD)
            && (rival == null || !rival.shrugsOffLaunches())) {
            if (target.onGround() && !Combo.inHole(target) && (!target.isBlocking() || axeInHotbar(player))) {
                if (!this.mc.level.getEntitiesOfClass(EndCrystal.class, target.getBoundingBox().inflate(3.0)).isEmpty()) {
                    return false;
                }

                double reach = reach(player, target);
                if (weaponSlot(player, target.isBlocking()) >= 0 && !(reach > 3.9)) {
                    boolean armed = MeleeAttacks.isMeleeWeapon(player.getMainHandItem()) && MeleeAttacks.charge(player) >= 0.5F;
                    if (!armed) {
                        int arm = rival != null ? rival.opener(reach) : (this.tick - this.hurtAt >= 20 ? 0 : 1);
                        if (this.openerArm < 0) {
                            this.judgeOpener(arm, reach, target);
                        }

                        if (arm == 1) {
                            this.nextLaunch = this.tick + 40;
                            return false;
                        }
                    }

                    this.launching = true;
                    this.launchUntil = this.tick + 16;
                    return this.launch(player, target);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private void judgeOpener(int arm, double reach, Player target) {
        this.openerArm = arm;
        this.openerReach = reach;
        this.openerAt = this.tick;
        this.openerHealth = CrystalDamage.health(target);
        this.openerPops = 0;
    }

    private boolean followUp(ClientLevel level, LocalPlayer player, Player target) {
        AirTrack.Landing down = this.air.landing(this.tick);
        Vec3 landing = down != null ? down.at() : target.position();
        if (this.anchorUnder(level, player, target, landing)) {
            return true;
        }

        int slot = BotBody.hotbarSlot(player, OBSIDIAN);
        if (slot < 0) {
            this.followLaid = true;
            return false;
        }

        BlockPos feet = BlockPos.containing(landing.x, landing.y + 0.2, landing.z);
        CrystalDamage.Victim them = new CrystalDamage.Victim(target, landing.subtract(target.position()));
        CrystalDamage.Victim us = new CrystalDamage.Victim(player, Vec3.ZERO);
        Vec3 eye = player.getEyePosition();
        AnchorGuard.Spot best = null;
        float most = 4.0F;

        for (BlockPos base : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 0, 1))) {
            if (base.getX() != feet.getX() || base.getZ() != feet.getZ()) {
                BlockState state = level.getBlockState(base);
                Vec3 center = Crystals.explosionAt(base);
                if (Crystals.isBase(state) && Crystals.canHost(level, base, null)) {
                    if (them.damage(center, false) >= most) {
                        this.followLaid = true;
                        return false;
                    }
                } else if (state.canBeReplaced() && level.isEmptyBlock(base.above()) && level.isEmptyBlock(base.above(2))) {
                    BlockPos below = base.below();
                    if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                        && level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), base, CollisionContext.empty())
                        && !(eye.distanceTo(Vec3.atCenterOf(base)) > player.blockInteractionRange())) {
                        float hurt = them.damage(center, true);
                        float own = us.damage(center, true);
                        if (!(hurt <= most) && !(own > this.selfCapNow) && !CrystalDamage.endangers(player, own)) {
                            AnchorGuard.Spot spot = AnchorGuard.supportFor(level, player, eye, base.immutable());
                            if (spot != null) {
                                best = spot;
                                most = hurt;
                            }
                        }
                    }
                }
            }
        }

        this.body.face(this.mc, player, target, 1.0F);
        if (best == null) {
            if (eye.distanceTo(landing) > 3.5) {
                this.body.drive(this.mc, player, 1, 0.0, true, false);
            }

            return true;
        } else {
            if (this.placeAt(level, player, best, slot) == 1) {
                this.followLaid = true;
            }

            return true;
        }
    }

    private boolean anchorUnder(ClientLevel level, LocalPlayer player, Player target, Vec3 landing) {
        if (this.anchors.get() && level.dimension() != Level.NETHER && !target.onGround()) {
            int slot = BotBody.hotbarSlot(player, stack -> stack.is(Items.RESPAWN_ANCHOR));
            if (slot >= 0 && BotBody.hotbarSlot(player, stack -> stack.is(Items.GLOWSTONE)) >= 0) {
                BlockPos cell = BlockPos.containing(landing.x, landing.y + 0.2, landing.z);
                BlockPos below = cell.below();
                if (level.getBlockState(cell).canBeReplaced() && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    if (!target.getBoundingBox().intersects(new AABB(cell)) && !player.getBoundingBox().intersects(new AABB(cell))) {
                        Vec3 eye = player.getEyePosition();
                        if (eye.distanceTo(Vec3.atCenterOf(cell)) > player.blockInteractionRange()) {
                            return false;
                        }

                        Vec3 blast = Vec3.atCenterOf(cell);
                        float own = CrystalDamage.estimate(blast, player, 5.0F, cell);
                        Vec3 onTop = new Vec3(landing.x, cell.getY() + 1.0, landing.z);
                        float theirs = new CrystalDamage.Victim(target, onTop.subtract(target.position())).damage(blast, 5.0F, cell);
                        if (!(theirs < 6.0F) && !(own > Math.min(this.selfCapNow, 11.0F)) && !CrystalDamage.endangers(player, own)) {
                            AnchorGuard.Spot spot = AnchorGuard.supportFor(level, player, eye, cell);
                            if (spot == null) {
                                return false;
                            }

                            this.body.face(this.mc, player, target, 1.0F);
                            if (this.placeAt(level, player, spot, slot) == 1) {
                                this.followLaid = true;
                            }

                            return true;
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean launch(LocalPlayer player, Player target) {
        if (this.tick > this.launchUntil
            || this.stance == CrystalBotModule.Stance.RUN
            || this.stance == CrystalBotModule.Stance.GUARD
            || reach(player, target) > 4.1
            || Combo.inHole(target)) {
            this.launching = false;
            this.nextLaunch = this.tick + 40;
            return false;
        }

        if (!this.hands()) {
            return true;
        }

        int slot = weaponSlot(player, target.isBlocking());
        if (slot < 0) {
            this.launching = false;
            return false;
        }

        if (player.getInventory().getSelectedSlot() != slot) {
            player.getInventory().setSelectedSlot(slot);
        }

        this.body.face(this.mc, player, target, 1.0F);
        float charge = MeleeAttacks.charge(player);
        double reach = reach(player, target);
        Rotation sent = ServerRotation.sent();
        boolean sprinting = player.isSprinting();
        if (charge >= 0.95F && reach <= 2.95 && this.strike(player, target)) {
            this.launching = false;
            this.nextLaunch = this.tick + 80;
            this.launchedAt = this.tick;
            this.launchY = target.getY();
            this.launchPeak = 0.0;
            this.followLaid = false;
            this.followUntil = this.tick + 12;
            this.air.kicked(player, target, sent != null ? sent.yaw() : player.getYRot(), sprinting, this.tick);
            this.body.drive(this.mc, player, 0, 1.0, false, false);
            return true;
        } else {
            this.body.drive(this.mc, player, reach > 2.9 ? 1 : 0, 0.0, charge >= 0.85F, false);
            return true;
        }
    }

    private boolean strike(LocalPlayer player, Player target) {
        if (!this.pause.slowServer() && !MeleeAttacks.actedThisTick(player) && this.mc.missTime <= 0 && MeleeAttacks.isMeleeWeapon(player.getMainHandItem())) {
            Rotation sent = ServerRotation.sent();
            if (sent == null) {
                return false;
            }

            Vec3 eye = player.getEyePosition();
            double reachLimit = Math.min(2.95, player.entityInteractionRange());
            AABB box = target.getBoundingBox();
            Vec3 at = box.contains(eye) ? eye : box.clip(eye, eye.add(Vec3.directionFromRotation(sent.pitch(), sent.yaw()).scale(reachLimit))).orElse(null);
            if (at != null && MeleeAttacks.lineIsClear(player, eye, at)) {
                if (!MeleeAttacks.attack(this.mc, new EntityHitResult(target, at))) {
                    return false;
                }

                MeleeAttacks.claimTick(this.mc);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private static double reach(LocalPlayer player, Player target) {
        Vec3 eye = player.getEyePosition();
        return eye.distanceTo(TargetingUtils.closestPoint(eye, target.getBoundingBox()));
    }

    private static boolean axeInHotbar(LocalPlayer player) {
        return BotBody.hotbarSlot(player, stack -> stack.is(ItemTags.AXES)) >= 0;
    }

    private static int weaponSlot(LocalPlayer player, boolean shield) {
        Inventory inventory = player.getInventory();
        int best = -1;
        double most = 0.0;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            if (MeleeAttacks.isMeleeWeapon(stack) && !stack.is(Items.MACE) && (!shield || stack.is(ItemTags.AXES))) {
                double damage = MeleeAttacks.attackDamage(stack);
                if (best < 0 || damage > most) {
                    best = i;
                    most = damage;
                }
            }
        }

        return best;
    }

    private boolean breakHole(ClientLevel level, LocalPlayer player, Player target, RivalBook.Rival rival) {
        AutoCityModule city = ModuleRegistry.get(AutoCityModule.class);
        if (city == null) {
            return false;
        }

        if (city.isEnabled()) {
            if (!this.cityStarted) {
                return false;
            }

            CombatMacroLock.release(this);
            this.body.face(this.mc, player, target, 1.0F);
            return true;
        } else {
            if (this.cityStarted) {
                this.cityStarted = false;
                this.breachAfter = this.tick + 100;
                this.progressAt = this.tick;
                if (Combo.inHole(target)) {
                    this.cityFails++;
                }
            }

            if (this.stance != CrystalBotModule.Stance.RUN
                && this.tick >= this.breachAfter
                && Combo.inHole(target)
                && !(player.getHealth() + player.getAbsorptionAmount() < 8.0F)) {
                int wait = rival != null && rival.campsInHoles() ? 15 : 40;
                if (this.tick - this.progressAt < wait) {
                    return false;
                }

                boolean pickaxe = BotBody.hotbarSlot(player, stack -> stack.is(ItemTags.PICKAXES)) >= 0;
                if (this.cityFails >= 2 && pickaxe && this.roofOff(level, player, target)) {
                    return true;
                }

                if (this.cityFails >= 2 || !pickaxe) {
                    return this.camp(level, player, target);
                }

                if (player.distanceTo(target) > 3.6) {
                    this.body.face(this.mc, player, target, 1.0F);
                    this.body.keepDistance(this.mc, player, target, 1.6, 2.6, 3.4, false);
                    return true;
                }

                city.setEnabled(true);
                if (!city.isEnabled()) {
                    return false;
                }

                this.cityStarted = true;
                ModuleChat.send(this, ModuleChat.body().text("Breaking ").name(target.getName().getString()).text("'s hole open."));
                return true;
            } else {
                return false;
            }
        }
    }

    private boolean roofOff(ClientLevel level, LocalPlayer player, Player target) {
        if (this.anchors.get()
            && level.dimension() != Level.NETHER
            && BotBody.hotbarSlot(player, stack -> stack.is(Items.RESPAWN_ANCHOR)) >= 0
            && BotBody.hotbarSlot(player, stack -> stack.is(Items.GLOWSTONE)) >= 0) {
            BlockPos roof = target.blockPosition().above(2);
            BlockState state = level.getBlockState(roof);
            if (state.canBeReplaced() || state.getDestroySpeed(level, roof) < 0.0F) {
                return false;
            }

            if (this.mine(level, player, roof)) {
                this.body.face(this.mc, player, target, 1.0F);
                return true;
            }

            BlockPos view = roofView(level, player, roof);
            if (view == null) {
                return false;
            }

            this.body.face(this.mc, player, target, 1.0F);
            this.body.toward(this.mc, player, Vec3.atBottomCenterOf(view));
            return true;
        } else {
            return false;
        }
    }

    private static boolean walkable(ClientLevel level, Vec3 from, Vec3 to) {
        Vec3 way = to.subtract(from);
        int steps = Math.max(1, (int)Math.ceil(way.horizontalDistance() / 0.5));
        int lastY = Mth.floor(from.y + 0.01);

        for (int i = 1; i <= steps; i++) {
            Vec3 at = from.add(way.scale((double)i / steps));
            BlockPos feet = StandPicker.standable(level, BlockPos.containing(at.x, lastY, at.z));
            if (feet == null || Math.abs(feet.getY() - lastY) > 1) {
                return false;
            }

            lastY = feet.getY();
        }

        return true;
    }

    private static BlockPos roofView(ClientLevel level, LocalPlayer player, BlockPos roof) {
        BlockPos here = player.blockPosition();
        BlockPos best = null;
        double nearest = Double.MAX_VALUE;
        double reach = player.blockInteractionRange() - 0.2;

        for (BlockPos cell : BlockPos.betweenClosed(here.offset(-4, -1, -4), here.offset(4, 1, 4))) {
            BlockPos feet = StandPicker.standable(level, cell);
            if (feet != null && feet.equals(cell) && walkable(level, player.position(), Vec3.atBottomCenterOf(feet))) {
                Vec3 eye = Vec3.atBottomCenterOf(feet).add(0.0, player.getEyeHeight(), 0.0);
                double distance = feet.distSqr(here);
                if (!(distance >= nearest)) {
                    for (Direction face : Direction.values()) {
                        Vec3 point = Vec3.atCenterOf(roof).relative(face, 0.5);
                        if (!(eye.distanceTo(point) > reach)) {
                            BlockHitResult hit = level.clip(new ClipContext(eye, point.relative(face, -0.05), Block.OUTLINE, Fluid.NONE, player));
                            if (hit.getType() == Type.BLOCK && hit.getBlockPos().equals(roof) && hit.getDirection() == face) {
                                best = feet.immutable();
                                nearest = distance;
                                break;
                            }
                        }
                    }
                }
            }
        }

        return best;
    }

    private boolean mine(ClientLevel level, LocalPlayer player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        int slot = toolSlot(player.getInventory(), state);
        if (slot < 0) {
            return false;
        }

        BlockHitResult face = visibleFace(level, player, pos);
        if (face == null) {
            return false;
        }

        if (!this.hands()) {
            return true;
        }

        Rotation sent = ServerRotation.sent();
        BlockHitResult held = sent == null ? null : AnchorGuard.trace(level, player, sent);
        if (held == null || !held.getBlockPos().equals(pos)) {
            ServerRotation.request(RotationUtils.fromTo(player.getEyePosition(), face.getLocation()), 6.0F);
            return true;
        }

        if (player.getInventory().getSelectedSlot() != slot) {
            player.getInventory().setSelectedSlot(slot);
            return true;
        }

        ServerRotation.request(sent);
        if (MeleeAttacks.actedThisTick(player)) {
            return true;
        }

        if (this.mc.gameMode.continueDestroyBlock(pos, held.getDirection())) {
            player.swing(InteractionHand.MAIN_HAND);
        }

        MeleeAttacks.noteAction(player);
        this.diggingAt = player.tickCount;
        return true;
    }

    public static boolean digging() {
        CrystalBotModule module = ModuleRegistry.get(CrystalBotModule.class);
        LocalPlayer player = Minecraft.getInstance().player;
        return module != null && module.isEnabled() && player != null && player.tickCount - module.diggingAt <= 1;
    }

    private static int toolSlot(Inventory inventory, BlockState state) {
        int best = -1;
        float fastest = 0.0F;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            boolean fits = stack.isCorrectToolForDrops(state) || !state.requiresCorrectToolForDrops();
            if (fits && !(stack.getDestroySpeed(state) <= fastest)) {
                best = i;
                fastest = stack.getDestroySpeed(state);
            }
        }

        return best;
    }

    private static BlockHitResult visibleFace(ClientLevel level, LocalPlayer player, BlockPos pos) {
        Vec3 eye = player.getEyePosition();
        BlockHitResult best = null;
        double nearest = player.blockInteractionRange() * player.blockInteractionRange();

        for (Direction face : Direction.values()) {
            Vec3 point = Vec3.atCenterOf(pos).relative(face, 0.5);
            double distance = eye.distanceToSqr(point);
            if (!(distance > nearest)) {
                BlockHitResult hit = AnchorGuard.trace(level, player, RotationUtils.fromTo(eye, point.relative(face, -0.05)));
                if (hit != null && hit.getBlockPos().equals(pos) && hit.getDirection() == face) {
                    best = hit;
                    nearest = distance;
                }
            }
        }

        return best;
    }

    private boolean camp(ClientLevel level, LocalPlayer player, Player target) {
        BlockPos feet = target.blockPosition();
        if (level.getEntitiesOfClass(EndCrystal.class, new AABB(feet).inflate(3.0, 1.0, 3.0)).size() >= 2) {
            return false;
        }

        Vec3 eye = player.getEyePosition();
        BlockPos best = null;
        float least = this.selfCapNow - 2.0F;
        double hit = player.entityInteractionRange() - 0.1;

        for (BlockPos cell : BlockPos.betweenClosed(feet.offset(-2, 0, -2), feet.offset(2, 0, 2))) {
            if (Math.max(Math.abs(cell.getX() - feet.getX()), Math.abs(cell.getZ() - feet.getZ())) == 2) {
                BlockPos base = cell.below().immutable();
                if (base.getY() >= feetY(player)
                    && Crystals.isBase(level.getBlockState(base))
                    && Crystals.canHost(level, base, null)
                    && !(eye.distanceToSqr(TargetingUtils.closestPoint(eye, Crystals.boxOn(base))) > hit * hit)) {
                    float own = CrystalDamage.estimate(Crystals.explosionAt(base), player);
                    if (!(own > least) && !CrystalDamage.endangers(player, own) && topAim(level, player, base) != null) {
                        best = base;
                        least = own;
                    }
                }
            }
        }

        if (best == null) {
            return false;
        }

        this.body.face(this.mc, player, target, 1.0F);
        this.placeCrystal(level, player, best);
        return true;
    }

    private static Vec3 topAim(ClientLevel level, LocalPlayer player, BlockPos base) {
        Vec3 eye = player.getEyePosition();
        double[][] spots = new double[][]{{0.5, 0.5}, {0.2, 0.2}, {0.8, 0.2}, {0.2, 0.8}, {0.8, 0.8}};

        for (double[] at : spots) {
            Vec3 point = new Vec3(base.getX() + at[0], base.getY() + 1.0, base.getZ() + at[1]);
            if (!(eye.distanceTo(point) > player.blockInteractionRange() - 0.1)) {
                BlockHitResult hit = AnchorGuard.trace(level, player, RotationUtils.fromTo(eye, point));
                if (hit != null && hit.getBlockPos().equals(base) && hit.getDirection() == Direction.UP) {
                    return point;
                }
            }
        }

        return null;
    }

    private void placeCrystal(ClientLevel level, LocalPlayer player, BlockPos base) {
        int slot = BotBody.hotbarSlot(player, stack -> stack.is(Items.END_CRYSTAL));
        if (slot >= 0 && this.hands()) {
            Rotation sent = ServerRotation.sent();
            BlockHitResult hit = sent == null ? null : AnchorGuard.trace(level, player, sent);
            if (hit == null || !hit.getBlockPos().equals(base) || hit.getDirection() != Direction.UP) {
                Vec3 aim = topAim(level, player, base);
                if (aim != null) {
                    ServerRotation.request(RotationUtils.fromTo(player.getEyePosition(), aim), 6.0F);
                }
            } else if (player.getInventory().getSelectedSlot() != slot) {
                player.getInventory().setSelectedSlot(slot);
            } else if (MeleeAttacks.handsFree(this.mc, player)) {
                if (Crystals.rightClick(this.mc, player, hit)) {
                    Crystals.notePlace(player, base);
                    MeleeAttacks.claimTick(this.mc);
                }

                ServerRotation.request(sent);
            }
        }
    }

    private boolean closeSpot(ClientLevel level, LocalPlayer player, Player target) {
        int slot = guardBlockSlot(player);
        if (slot < 0) {
            return false;
        }

        if (this.blocking == null && this.tick >= this.spotScanAt) {
            this.spotScanAt = this.tick + 5;
            this.blocking = dangerSpot(level, player, target);
        }

        if (this.blocking == null) {
            return false;
        }

        if (level.getBlockState(this.blocking).canBeReplaced()
            && level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), this.blocking, CollisionContext.empty())) {
            AnchorGuard.Spot spot = AnchorGuard.supportFor(level, player, player.getEyePosition(), this.blocking);
            if (spot == null) {
                this.blocking = null;
                return false;
            }

            if (this.placeAt(level, player, spot, slot) == 1) {
                this.blocking = null;
            }

            return true;
        } else {
            this.blocking = null;
            return false;
        }
    }

    private static BlockPos dangerSpot(ClientLevel level, LocalPlayer player, Player target) {
        BlockPos feet = player.blockPosition();
        Vec3 theirEye = target.getEyePosition();
        double reach = Math.max(4.6, player.blockInteractionRange() + 0.1);
        BlockPos worst = null;
        float first = 0.0F;
        float second = 0.0F;

        for (BlockPos base : BlockPos.betweenClosed(feet.offset(-4, -2, -4), feet.offset(4, 1, 4))) {
            if (Crystals.isBase(level.getBlockState(base))
                && Crystals.canHost(level, base, null)
                && !(theirEye.distanceTo(new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5)) > reach)) {
                Vec3 center = Crystals.explosionAt(base);
                float us = CrystalDamage.estimate(center, player);
                if (!(us <= second) && !(CrystalDamage.estimate(center, target) >= us + 2.0F)) {
                    if (us > first) {
                        second = first;
                        first = us;
                        worst = base.above().immutable();
                    } else {
                        second = us;
                    }
                }
            }
        }

        return first >= 8.0F && second <= first / 2.0F ? worst : null;
    }

    private int placeAt(ClientLevel level, LocalPlayer player, AnchorGuard.Spot spot, int slot) {
        if (!this.hands()) {
            return 0;
        }

        Rotation sent = ServerRotation.sent();
        BlockHitResult hit = sent == null ? null : AnchorGuard.hitAlong(level, player, sent, spot);
        if (hit == null) {
            ServerRotation.request(RotationUtils.fromTo(player.getEyePosition(), spot.aim()), 6.0F);
            return 0;
        }

        if (player.getInventory().getSelectedSlot() != slot) {
            player.getInventory().setSelectedSlot(slot);
            return 0;
        }

        if (!MeleeAttacks.handsFree(this.mc, player)) {
            return 0;
        }

        AnchorGuard.click(this.mc, player, hit);
        ServerRotation.request(sent);
        return 1;
    }

    private boolean defuse(ClientLevel level, LocalPlayer player) {
        if (this.tick - this.defuseAt >= 10 && level.dimension() != Level.NETHER) {
            BlockPos feet = player.blockPosition();

            for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-5, -2, -5), feet.offset(5, 3, 5))) {
                BlockState state = level.getBlockState(pos);
                if (state.is(Blocks.RESPAWN_ANCHOR)
                    && state.getValue(RespawnAnchorBlock.CHARGE) > 0
                    && player.isWithinBlockInteractionRange(pos, -0.1)
                    && !pos.equals(this.digAnchor)) {
                    BlockPos anchor = pos.immutable();
                    Vec3 center = Vec3.atCenterOf(anchor);
                    if (!(CrystalDamage.estimate(center, player, 5.0F, anchor) > Math.max(1.0F, 0.15F * player.getHealth()))) {
                        boolean theirs = false;

                        for (Player enemy : enemies(level, player, 16.0)) {
                            theirs |= enemy.getEyePosition().distanceTo(center) <= 5.5;
                        }

                        if (theirs) {
                            Inventory inventory = player.getInventory();
                            int route = AnchorGuard.detonatorRoute(inventory, player.getMainHandItem());
                            if (route == -1 || !this.hands()) {
                                return false;
                            }

                            BlockHitResult hit = AnchorGuard.anchorHit(level, player, anchor);
                            if (hit != null) {
                                if (route != -2) {
                                    inventory.setSelectedSlot(route);
                                    return true;
                                }

                                if (!MeleeAttacks.handsFree(this.mc, player)) {
                                    return true;
                                }

                                AnchorGuard.click(this.mc, player, hit);
                                this.defuseAt = this.tick;
                                return true;
                            }

                            Vec3 aim = AnchorGuard.anchorAim(level, player, anchor);
                            if (aim != null) {
                                ServerRotation.request(RotationUtils.fromTo(player.getEyePosition(), aim), 6.0F);
                                return true;
                            }
                        }
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private boolean guard(ClientLevel level, LocalPlayer player, Player target) {
        float health = player.getHealth() + player.getAbsorptionAmount();
        BlockPos danger = null;
        float worst = 0.0F;
        BlockPos feet = player.blockPosition();

        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-5, -2, -5), feet.offset(5, 3, 5))) {
            if (level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR) && !pos.equals(this.digAnchor)) {
                Vec3 center = Vec3.atCenterOf(pos);
                float hurt = CrystalDamage.estimate(center, player, 5.0F, pos.immutable());
                if ((!(hurt < Math.max(4.0F, health * 0.3F)) || !(hurt < health - 1.0F))
                    && !(target.getEyePosition().distanceTo(center) > 5.5)
                    && !(hurt <= worst)) {
                    worst = hurt;
                    danger = pos.immutable();
                }
            }
        }

        if (danger == null) {
            return false;
        }

        AnchorGuard.Spot spot = AnchorGuard.find(level, player, danger, player.getEyePosition());
        if (spot != null && spot.standing()) {
            return false;
        }

        int slot = guardBlockSlot(player);
        if (spot != null && slot >= 0 && this.tick - this.shieldAt >= 20) {
            this.body.face(this.mc, player, target, 1.0F);
            if (this.placeAt(level, player, spot, slot) == 1) {
                this.shieldAt = this.tick;
            }

            return true;
        } else {
            this.body.face(this.mc, player, target, 1.0F);
            this.body.away(this.mc, player, Vec3.atCenterOf(danger));
            return true;
        }
    }

    private static int guardBlockSlot(LocalPlayer player) {
        int obsidian = BotBody.hotbarSlot(player, OBSIDIAN);
        return obsidian >= 0 ? obsidian : BotBody.hotbarSlot(player, AnchorGuard::isGuardBlock);
    }

    private boolean cover(ClientLevel level, LocalPlayer player, Player target, double distance) {
        boolean boxing = this.boxingAt != null && this.boxingAt.equals(player.blockPosition()) && this.tick < this.boxingUntil;
        boolean wants = player.getHealth() + player.getAbsorptionAmount() <= this.retreatAt() + 4.0F || this.stance == CrystalBotModule.Stance.GUARD;
        if (this.surround.get() && !boxing && wants && player.onGround()) {
            this.boxingAt = player.blockPosition();
            this.boxingUntil = this.tick + 60;
            boxing = true;
        }

        if (!boxing) {
            this.boxingAt = null;
        }

        return this.surround.get() && boxing && player.onGround() && this.boxIn(level, player);
    }

    private boolean boxIn(ClientLevel level, LocalPlayer player) {
        List<BlockPos> open = openAroundFeet(level, player);
        if (!open.isEmpty() && BotBody.hotbarSlot(player, OBSIDIAN) >= 0) {
            if (!this.hands()) {
                return true;
            }

            for (BlockPos cell : open) {
                BlockHitResult support = AutoTrapModule.support(level, player, cell);
                if (support != null && (!cell.equals(this.boxSkipped) || this.tick >= this.boxSkippedUntil)) {
                    BlockHitResult seen = AutoTrapModule.seenByServer(level, player, support);
                    if (seen != null) {
                        int slot = BotBody.hotbarSlot(player, OBSIDIAN);
                        if (player.getInventory().getSelectedSlot() != slot) {
                            player.getInventory().setSelectedSlot(slot);
                            return true;
                        }

                        if (!MeleeAttacks.handsFree(this.mc, player)) {
                            return true;
                        }

                        InteractionResult result = this.mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, seen);
                        if (result.consumesAction()) {
                            player.swing(InteractionHand.MAIN_HAND);
                            MeleeAttacks.noteAction(player);
                            ServerRotation.request(ServerRotation.sent());
                        }

                        return true;
                    }

                    this.boxAimTicks = cell.equals(this.boxAiming) ? this.boxAimTicks + 1 : 0;
                    this.boxAiming = cell;
                    if (this.boxAimTicks < 10) {
                        ServerRotation.request(RotationUtils.fromTo(player.getEyePosition(), support.getLocation()), 6.0F);
                        return true;
                    }

                    this.boxSkipped = cell;
                    this.boxSkippedUntil = this.tick + 40;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private static List<BlockPos> openAroundFeet(ClientLevel level, LocalPlayer player) {
        List<BlockPos> open = new ArrayList<>();

        for (BlockPos cell : ring(player)) {
            BlockState state = level.getBlockState(cell);
            if (state.canBeReplaced()
                && !(cell.getCenter().distanceTo(player.getEyePosition()) > player.blockInteractionRange())
                && level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), cell, CollisionContext.empty())) {
                open.add(cell);
            }
        }

        return open;
    }

    private static boolean surrounded(ClientLevel level, LocalPlayer player) {
        for (BlockPos cell : ring(player)) {
            if (level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private static Set<BlockPos> ring(LocalPlayer player) {
        AABB box = player.getBoundingBox();
        int feet = Mth.floor(box.minY + 0.01);
        Set<Long> columns = new LinkedHashSet<>();

        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX - 1.0E-4); x++) {
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ - 1.0E-4); z++) {
                columns.add(BlockPos.asLong(x, 0, z));
            }
        }

        Set<BlockPos> ring = new LinkedHashSet<>();

        for (long key : columns) {
            BlockPos column = BlockPos.of(key).atY(feet);

            for (Direction side : Plane.HORIZONTAL) {
                BlockPos cell = column.relative(side);
                if (!columns.contains(BlockPos.asLong(cell.getX(), 0, cell.getZ()))) {
                    ring.add(cell);
                }
            }
        }

        return ring;
    }

    private static boolean armorLow(LivingEntity player) {
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (slot.isArmor()) {
                ItemStack stack = player.getItemBySlot(slot);
                if (stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() < stack.getMaxDamage() * 0.3F) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean throwXp(LocalPlayer player) {
        if (BotBody.hotbarSlot(player, XP) < 0) {
            return false;
        }

        this.mending = true;
        Aim.turn(this.mc, player.getYRot(), 86.0F, 22.0F);
        if (player.getXRot() > 70.0F) {
            this.body.use(this.mc, player, XP);
        }

        return true;
    }

    private boolean mendIdle(LocalPlayer player) {
        if (this.mendArmor.get() && (armorLow(player) || this.mending) && hasDamagedArmor(player)) {
            CombatMacroLock.take(this);
            return this.throwXp(player);
        } else {
            return false;
        }
    }

    private static boolean hasDamagedArmor(LocalPlayer player) {
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (slot.isArmor() && player.getItemBySlot(slot).isDamaged()) {
                return true;
            }
        }

        return false;
    }

    private static int count(LocalPlayer player, Predicate<ItemStack> want) {
        int count = 0;
        Inventory inventory = player.getInventory();

        for (int i = 0; i < 36; i++) {
            if (want.test(inventory.getItem(i))) {
                count += inventory.getItem(i).getCount();
            }
        }

        return count + (want.test(player.getOffhandItem()) ? player.getOffhandItem().getCount() : 0);
    }

    @Environment(EnvType.CLIENT)
    private enum Stance {
        PRESS(3.5F, 10.0F, 5.0F, 0.7),
        HOLD(4.5F, 8.0F, 6.0F, 1.0),
        GUARD(6.0F, 5.0F, 7.0F, 1.6),
        RUN(99.0F, 3.0F, 99.0F, 2.0);

        final float minDamage;
        final float selfCap;
        final float anchorMin;
        final double cover;

        Stance(float minDamage, float selfCap, float anchorMin, double cover) {
            this.minDamage = minDamage;
            this.selfCap = selfCap;
            this.anchorMin = anchorMin;
            this.cover = cover;
        }
    }

    @Environment(EnvType.CLIENT)
    private enum Throw {
        ESCAPE,
        CHASE;
    }
}
