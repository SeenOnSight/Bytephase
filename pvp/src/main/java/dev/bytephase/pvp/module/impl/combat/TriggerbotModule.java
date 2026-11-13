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
import net.minecraft.client.multiplayer.ClientLevel;
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
    private final BooleanSetting instant = this.addSetting("instant", new BooleanSetting("Instant", true));
    private final NumberSetting cooldown = this.addSetting("cooldown", new NumberSetting("Cooldown (%)", 90.0F, 0.0F, 100.0F).step(1.0F));
    private final NumberSetting reaction = this.addSetting("reaction", new NumberSetting("Reaction", 0.0F, 0.0F, 10.0F).step(1.0F));
    private final ModeSetting crits = this.addSetting("crits", new ModeSetting("Crits", "Smart", "Off", "Smart", "Only"));
    private final NumberSetting critDelay = this.addSetting("crit-delay", new NumberSetting("Crit Wait (ticks)", 0.0F, 0.0F, 12.0F).step(1.0F));
    private final BooleanSetting holdAttack = this.addSetting("hold-attack", new BooleanSetting("Hold Attack", false));
    private final BooleanSetting players = this.addSetting("players", new BooleanSetting("Players", true));
    private final BooleanSetting hostiles = this.addSetting("hostiles", new BooleanSetting("Hostiles", false));
    private final BooleanSetting invisible = this.addSetting("invisible", new BooleanSetting("Invisible", false));
    private final BooleanSetting weaponsOnly = this.addSetting("weapons-only", new BooleanSetting("Weapons Only", true));
    private int ticks;
    private int trackedId = -1;
    private int seenTick;
    private int reactionDoneTick;
    private int critWaitStartedTick = -1;
    private LocalPlayer sessionPlayer;
    private ClientLevel sessionLevel;

    public TriggerbotModule() {
        super("triggerbot", "Triggerbot", "Hits the crosshair target at a configurable cooldown threshold, with immediate frame checks.", ModuleCategory.COMBAT);
        this.reaction.visibleWhen(() -> !this.instant.get());
        this.critDelay.visibleWhen(() -> !this.instant.get() && this.crits.is(CRITS_SMART));
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
        this.critWaitStartedTick = -1;
        this.sessionPlayer = null;
        this.sessionLevel = null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTick(TickEvent event) {
        this.ticks++;
        this.tryAttack(0.5F);
    }

    /** Called after local mouse look and aim assistance, including stationary frames. */
    public void onFrame() {
        if (this.isEnabled()) {
            Minecraft mc = Minecraft.getInstance();
            this.tryAttack(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        }
    }

    private void tryAttack(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!MeleeAttacks.canAct(mc) || !mc.isWindowActive() || mc.isPaused()
            || player.isDeadOrDying() || player.isSpectator()) {
            this.trackedId = -1;
            this.critWaitStartedTick = -1;
            return;
        }
        if (player != this.sessionPlayer || mc.level != this.sessionLevel) {
            this.sessionPlayer = player;
            this.sessionLevel = mc.level;
            this.trackedId = -1;
            this.critWaitStartedTick = -1;
        }
        // Reuse the existing ownership guard for tick and frame callbacks.
        if (MeleeAttacks.actedThisTick(player)) return;
        EntityHitResult hit = MeleeAttacks.crosshairHit(mc);
        if (this.holdAttack.get() && this.trackedId != -1 && hit == null
            && mc.hitResult != null && mc.hitResult.getType() == Type.MISS) {
            MeleeAttacks.takeAttackClicks(mc);
        }
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target) || !this.isTarget(player, target)) {
            if (this.ticks - this.seenTick > TRACK_GRACE_TICKS) this.trackedId = -1;
            this.critWaitStartedTick = -1;
            return;
        }
        if (target.getId() != this.trackedId) {
            this.trackedId = target.getId();
            this.reactionDoneTick = this.ticks + (this.instant.get() ? 0 : this.reaction.getInt());
            this.critWaitStartedTick = -1;
        }
        this.seenTick = this.ticks;
        if ((!this.instant.get() && this.ticks < this.reactionDoneTick)
            || this.holdAttack.get() && !mc.options.keyAttack.isDown()
            || this.weaponsOnly.get() && !MeleeAttacks.isMeleeWeapon(player.getMainHandItem())
            || CombatMacroLock.heldByOther(this) || MeleeAttacks.wastedOnShield(player, target)
            || mc.missTime > 0 || MeleeAttacks.clickPending(mc)
            || MeleeAttacks.rightClickComing(mc) || ServerRotation.pending()) return;

        MaceModule mace = ModuleRegistry.get(MaceModule.class);
        boolean smash = mace != null && mace.smashReady(player, target);
        float progress = player.getAttackStrengthScale(Math.max(0.0F, Math.min(1.0F, partialTick)));
        if (!smash && progress < this.cooldown.get() / 100.0F) return;
        boolean stabbing = player.getMainHandItem().has(DataComponents.PIERCING_WEAPON);
        if (!stabbing && !smash && !this.critsAllow(mc, player, target)) return;
        if (stabbing) {
            hit = this.stabHit(player, target);
            if (hit == null) return;
        }
        if (stabbing ? MeleeAttacks.stab(mc, hit) : MeleeAttacks.attack(mc, hit)) {
            this.critWaitStartedTick = -1;
            if (smash) mace.noteSmash();
            MeleeAttacks.claimTick(mc);
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
                if (this.instant.get() || this.critDelay.getInt() == 0 || MeleeAttacks.wouldCrit(player, target)) {
                    this.critWaitStartedTick = -1;
                    yield true;
                } else {
                    boolean jumping = player.onGround() && mc.options.keyJump.isDown() && !player.isSprinting();
                    if (!MeleeAttacks.critStance(player, target) && !jumping) {
                        this.critWaitStartedTick = -1;
                        yield true;
                    } else {
                        if (this.critWaitStartedTick < 0) this.critWaitStartedTick = this.ticks;
                        yield this.ticks - this.critWaitStartedTick >= this.critDelay.getInt();
                    }
                }
            }
            default -> true;
        };
    }
}
