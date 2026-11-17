package dev.bytephase.pvp.module.impl.combat;

import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.EventPriority;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.CombatMacroLock;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.ModuleRegistry;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import dev.bytephase.pvp.module.setting.ModeSetting;
import dev.bytephase.pvp.module.setting.NumberSetting;
import dev.bytephase.pvp.util.pvp.InputControl;
import dev.bytephase.pvp.util.pvp.Rotation;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

@Environment(EnvType.CLIENT)
public final class TriggerbotModule extends ClientModule {
    private static final String CRITS_OFF = "Off";
    private static final String CRITS_SMART = "Smart";
    private static final String CRITS_ONLY = "Only";
    private static final int TRACK_GRACE_TICKS = 4;
    private static final int SMART_MAX_WAIT_TICKS = 12;
    private final NumberSetting reaction = this.addSetting("reaction", new NumberSetting("Reaction", 3.0F, 0.0F, 10.0F).step(1.0F));
    private final ModeSetting crits = this.addSetting("crits", new ModeSetting("Crits", "Smart", "Off", "Smart", "Only"));
    private final BooleanSetting holdAttack = this.addSetting("hold-attack", new BooleanSetting("Hold Attack", false));
    private final BooleanSetting players = this.addSetting("players", new BooleanSetting("Players", true));
    private final BooleanSetting hostiles = this.addSetting("hostiles", new BooleanSetting("Hostiles", false));
    private final BooleanSetting invisible = this.addSetting("invisible", new BooleanSetting("Invisible", false));
    private final BooleanSetting weaponsOnly = this.addSetting("weapons-only", new BooleanSetting("Weapons Only", true));
    private int ticks;
    private int trackedId = -1;
    private int seenTick;
    private int reactionDoneTick;
    private int chargedTick = -1;
    private int chargePause;
    private int critWait;
    private int critWaitTick = -1;

    public TriggerbotModule() {
        super("triggerbot", "Triggerbot", "Hits the entity under the crosshair once the swing is charged, with a reaction delay.", ModuleCategory.COMBAT);
    }

    @Override
    protected void onEnable() {
        this.reset();
    }

    @Override
    protected void onDisable() {
        this.reset();
    }

    private void reset() {
        this.ticks = 0;
        this.trackedId = -1;
        this.seenTick = 0;
        this.reactionDoneTick = 0;
        this.chargedTick = -1;
        this.chargePause = 0;
        this.critWait = 0;
        this.critWaitTick = -1;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTick(TickEvent event) {
        this.ticks++;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!MeleeAttacks.canAct(mc)) {
            this.trackedId = -1;
        } else {
            this.trackCharge(player);
            EntityHitResult hit = MeleeAttacks.crosshairHit(mc);
            if (this.holdAttack.get() && this.trackedId != -1 && mc.hitResult != null && mc.hitResult.getType() == Type.MISS) {
                MeleeAttacks.takeAttackClicks(mc);
            }

            if (hit != null && hit.getEntity() instanceof LivingEntity target && this.isTarget(player, target)) {
                if (target.getId() != this.trackedId) {
                    this.trackedId = target.getId();
                    this.reactionDoneTick = this.ticks + this.reaction.getInt() + MeleeAttacks.spread(2);
                }

                this.seenTick = this.ticks;
                MaceModule mace = ModuleRegistry.get(MaceModule.class);
                boolean smash = mace != null && mace.smashReady(player, target);
                if (this.ticks >= this.reactionDoneTick && (smash || this.chargedTick >= 0 && this.ticks - this.chargedTick >= this.chargePause)) {
                    if (!this.holdAttack.get() || mc.options.keyAttack.isDown()) {
                        if (!this.weaponsOnly.get() || MeleeAttacks.isMeleeWeapon(player.getMainHandItem())) {
                            if (!CombatMacroLock.heldByOther(this) && !MeleeAttacks.wastedOnShield(player, target)) {
                                if (mc.missTime <= 0 && !MeleeAttacks.clickPending(mc) && !MeleeAttacks.rightClickComing(mc) && !ServerRotation.pending()) {
                                    boolean stabbing = player.getMainHandItem().has(DataComponents.PIERCING_WEAPON);
                                    if (stabbing || smash || this.critsAllow(mc, player, target)) {
                                        if (stabbing) {
                                            hit = this.stabHit(player, target);
                                            if (hit == null) {
                                                return;
                                            }
                                        }

                                        if (stabbing ? MeleeAttacks.stab(mc, hit) : MeleeAttacks.attack(mc, hit)) {
                                            this.chargedTick = -1;
                                            if (smash) {
                                                mace.noteSmash();
                                            }

                                            MeleeAttacks.claimTick(mc);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (this.ticks - this.seenTick > 4) {
                    this.trackedId = -1;
                }
            }
        }
    }

    private void trackCharge(LocalPlayer player) {
        if (MeleeAttacks.charge(player) < 1.0F) {
            this.chargedTick = -1;
        } else if (this.chargedTick < 0) {
            this.chargedTick = this.ticks;
            this.chargePause = MeleeAttacks.spread(1);
        }
    }

    private EntityHitResult stabHit(LocalPlayer player, LivingEntity target) {
        Rotation sent = ServerRotation.sent();
        if (sent == null) {
            return null;
        }

        AttackRange range = player.entityAttackRange();
        Vec3 look = Vec3.directionFromRotation(sent.pitch(), sent.yaw());
        Vec3 eye = player.getEyePosition();
        Vec3 start = eye.add(look.scale(range.effectiveMinRange(player)));
        Vec3 end = eye.add(look.scale(range.effectiveMaxRange(player) + Math.max(0.0, player.getKnownMovement().dot(look))));
        float margin = range.hitboxMargin();
        Vec3 at = target.getBoundingBox().inflate(margin).clip(eye, end).orElse(null);
        if (at != null && MeleeAttacks.lineIsClear(player, eye, at)) {
            for (Entity entity : player.level().getEntities(player, new AABB(start, end).inflate(1.0))) {
                if (entity != target && entity instanceof LivingEntity other && PiercingWeapon.canHitEntity(player, entity) && !this.isTarget(player, other)) {
                    AABB box = entity.getBoundingBox().inflate(margin);
                    if (box.contains(start) || box.clip(start, end).isPresent()) {
                        return null;
                    }
                }
            }

            return new EntityHitResult(target, at);
        } else {
            return null;
        }
    }

    private boolean isTarget(LocalPlayer self, LivingEntity entity) {
        if (!MeleeAttacks.isEnemy(self, entity)) {
            return false;
        } else if (entity.isInvisible() && !this.invisible.get()) {
            return false;
        } else {
            return entity instanceof Player ? this.players.get() : this.hostiles.get() && MeleeAttacks.isHostile(entity);
        }
    }

    private boolean critsAllow(Minecraft mc, LocalPlayer player, LivingEntity target) {
        return switch (this.crits.get()) {
            case "Only" -> {
                if (MeleeAttacks.airborne(player) && player.isSprinting() && player.fallDistance > 0.0) {
                    InputControl.dropSprint();
                }

                yield MeleeAttacks.wouldCrit(player, target);
            }
            case "Smart" -> {
                if (MeleeAttacks.wouldCrit(player, target)) {
                    yield true;
                } else {
                    boolean jumping = player.onGround() && mc.options.keyJump.isDown() && !player.isSprinting();
                    if (!MeleeAttacks.critStance(player, target) && !jumping) {
                        yield true;
                    } else {
                        if (this.critWaitTick != this.ticks - 1) {
                            this.critWait = 0;
                        }

                        this.critWaitTick = this.ticks;
                        yield this.critWait++ >= 12;
                    }
                }
            }
            default -> true;
        };
    }
}
