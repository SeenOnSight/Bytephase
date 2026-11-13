package dev.bytephase.pvp.module.impl.combat;

import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.EventPriority;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.CombatMacroLock;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.ModuleRegistry;
import dev.bytephase.pvp.module.impl.combat.crystal.CrystalDamage;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import dev.bytephase.pvp.module.setting.ModeSetting;
import dev.bytephase.pvp.util.pvp.InputControl;
import dev.bytephase.pvp.util.pvp.Rotation;
import dev.bytephase.pvp.util.pvp.RotationUtils;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import dev.bytephase.pvp.util.pvp.TargetingUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class KillAuraModule extends ClientModule {
    private static final String PRIORITY_DISTANCE = "Distance";
    private static final String PRIORITY_HEALTH = "Health";
    private static final String PRIORITY_ANGLE = "Angle";
    private static final String CRITS_OFF = "Off";
    private static final String CRITS_WAIT = "Wait";
    private static final String CRITS_JUMP = "Jump";
    private static final int CRIT_HOLD_TICKS = 8;
    private static final int JUMP_LEAD = 7;
    private static final double REACH_MARGIN = 0.01;
    private static final double RIDING_BONUS = 2.5;
    private static final double MAX_STEP_SQ = 1.0;
    private static final double[] AIM_HEIGHTS = new double[]{0.85, 0.5, 0.15};
    private final BooleanSetting players = this.addSetting("players", new BooleanSetting("Players", true));
    private final BooleanSetting hostiles = this.addSetting("hostiles", new BooleanSetting("Hostiles", false));
    private final ModeSetting priority = this.addSetting("priority", new ModeSetting("Priority", "Distance", "Distance", "Health", "Angle"));
    private final ModeSetting crits = this.addSetting("crits", new ModeSetting("Crits", "Wait", "Off", "Wait", "Jump"));
    private LivingEntity target;
    private int critWait;

    public KillAuraModule() {
        super("kill-aura", "Kill Aura", "Attacks nearby targets without moving your camera.", ModuleCategory.COMBAT);
    }

    @Override
    protected void onDisable() {
        this.target = null;
        this.critWait = 0;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (MeleeAttacks.canAct(mc)
            && !player.isDeadOrDying()
            && !player.isSpectator()
            && mc.missTime <= 0
            && MeleeAttacks.handsFree(mc, player)
            && !CombatMacroLock.heldByOther(this)
            && !MeleeAttacks.actedThisTick(player)
            && !MeleeAttacks.rightClickComing(mc)) {
            boolean riding = player.isPassenger();
            ItemStack held = player.getMainHandItem();
            boolean spear = held.has(DataComponents.PIERCING_WEAPON);
            double reach = spear ? player.entityAttackRange().effectiveMaxRange(player) - 0.01 : player.entityInteractionRange() - 0.01 + (riding ? 2.5 : 0.0);
            Vec3 eye = player.getEyePosition();
            this.target = this.pick(mc, player, eye, reach);
            if (this.target == null) {
                this.critWait = 0;
            } else {
                MaceModule mace = ModuleRegistry.get(MaceModule.class);
                boolean smash = !spear && mace != null && mace.smashReady(player, this.target);
                boolean ready = spear ? !player.cannotAttackWithItem(held, 0) : smash || MeleeAttacks.charge(player) >= 1.0F;
                boolean aiming = !ready && MeleeAttacks.ticksToFull(player) <= 1;
                boolean jump = !spear && this.crits.is("Jump") && !riding && player.onGround() && !player.isInWater() && MeleeAttacks.ticksToFull(player) <= 7;
                if (jump) {
                    InputControl.jump();
                }

                AABB raw = this.target.getBoundingBox();
                if (aiming) {
                    raw = raw.move(stride(this.target));
                }

                Vec3 point = aimPoint(player, eye, raw);
                if (point != null) {
                    double side = Math.min(0.03, raw.getXsize() * 0.15);
                    AABB hitBox = raw.deflate(side, 0.01, side);
                    Rotation aim = RotationUtils.fromTo(eye, point);
                    Rotation next = !riding && !hitBox.contains(eye) && ServerRotation.canRotate(player) && !ServerRotation.pending()
                        ? ServerRotation.nextStep(player, aim)
                        : null;
                    Vec3 nextAt = hits(player, eye, hitBox, reach, next);
                    if (!ready) {
                        if (aiming && nextAt != null) {
                            ServerRotation.request(aim);
                        }
                    } else {
                        Rotation look = riding ? RotationUtils.entityRotation(player) : ServerRotation.sent();
                        Vec3 at = hits(player, eye, hitBox, reach, look);
                        if (at != null || nextAt != null) {
                            if (spear) {
                                if (at == null) {
                                    ServerRotation.request(aim);
                                } else if (!hitsOthers(player, this.target, eye, look) && MeleeAttacks.stab(mc, new EntityHitResult(this.target, at))) {
                                    MeleeAttacks.claimTick(mc);
                                }
                            } else {
                                boolean hold = smash
                                    ? mace.holdsSmash(player, this.target)
                                    : !this.crits.is("Off") && !riding && this.waitForCrit(player, jump);
                                if (hold) {
                                    if (smash) {
                                        InputControl.dropSprint();
                                    }

                                    if (nextAt != null && !mc.options.keyAttack.isDown() && !MeleeAttacks.clickPending(mc)) {
                                        ServerRotation.request(aim);
                                    }
                                } else {
                                    if (at == null) {
                                        ServerRotation.request(aim);
                                        at = nextAt;
                                    }

                                    if (MeleeAttacks.attack(mc, new EntityHitResult(this.target, at), riding ? 2.5 : 0.0)) {
                                        this.critWait = 0;
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
            this.critWait = 0;
        }
    }

    private boolean waitForCrit(LocalPlayer player, boolean jump) {
        if (MeleeAttacks.airborne(player)) {
            if (player.isSprinting() || !(player.fallDistance > 0.0) && this.critWait++ < 8) {
                InputControl.dropSprint();
                return true;
            } else {
                return false;
            }
        } else {
            return player.onGround() && (jump || player.input.keyPresses.jump()) && this.critWait++ < 8;
        }
    }

    private LivingEntity pick(Minecraft mc, LocalPlayer player, Vec3 eye, double reach) {
        LivingEntity best = null;
        double bestRank = 0.0;
        double bestDistance = 0.0;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living) {
                AABB raw = living.getBoundingBox();
                double distance = eye.distanceTo(TargetingUtils.closestPoint(eye, raw));
                if (!(distance > reach) && this.wanted(player, living)) {
                    Vec3 point = aimPoint(player, eye, raw);
                    if (point != null) {
                        if (living == this.target) {
                            return living;
                        }

                        double rank = this.rank(player, eye, living, point, distance);
                        if (best != null) {
                            int order = Double.compare(rank, bestRank);
                            if (order == 0) {
                                order = Boolean.compare(living.hurtTime > 0, best.hurtTime > 0);
                            }

                            if (order == 0) {
                                order = Double.compare(distance, bestDistance);
                            }

                            if (order >= 0) {
                                continue;
                            }
                        }

                        best = living;
                        bestRank = rank;
                        bestDistance = distance;
                    }
                }
            }
        }

        return best;
    }

    private double rank(LocalPlayer player, Vec3 eye, LivingEntity living, Vec3 point, double distance) {
        if (this.priority.is("Health")) {
            return CrystalDamage.health(living);
        }

        if (!this.priority.is("Angle")) {
            return distance;
        }

        Rotation sent = ServerRotation.sent();
        Rotation look = sent != null ? sent : RotationUtils.entityRotation(player);
        return RotationUtils.angleTo(look, RotationUtils.fromTo(eye, point));
    }

    private boolean wanted(LocalPlayer player, LivingEntity living) {
        if (!MeleeAttacks.isEnemy(player, living) || MeleeAttacks.wastedOnShield(player, living)) {
            return false;
        } else {
            return living instanceof Player ? this.players.get() : this.hostiles.get() && MeleeAttacks.isHostile(living);
        }
    }

    private static Vec3 aimPoint(LocalPlayer player, Vec3 eye, AABB raw) {
        double side = Math.min(0.15, raw.getXsize() * 0.3);
        Vec3 near = TargetingUtils.closestPoint(eye, raw.deflate(side, Math.min(0.2, raw.getYsize() * 0.25), side));
        if (MeleeAttacks.lineIsClear(player, eye, near)) {
            return near;
        }

        Vec3 center = raw.getCenter();

        for (double height : AIM_HEIGHTS) {
            Vec3 point = new Vec3(center.x, raw.minY + raw.getYsize() * height, center.z);
            if (MeleeAttacks.lineIsClear(player, eye, point)) {
                return point;
            }
        }

        return null;
    }

    private static Vec3 stride(LivingEntity entity) {
        double dx = entity.getX() - entity.xo;
        double dz = entity.getZ() - entity.zo;
        return dx * dx + dz * dz > 1.0 ? Vec3.ZERO : new Vec3(dx, 0.0, dz);
    }

    private static Vec3 hits(LocalPlayer player, Vec3 eye, AABB box, double reach, Rotation look) {
        if (look == null) {
            return null;
        }

        Vec3 direction = Vec3.directionFromRotation(look.pitch(), look.yaw());
        Vec3 at = box.clip(eye, eye.add(direction.scale(reach))).orElse(box.contains(eye) ? eye : null);
        return at != null && eye.distanceTo(at) <= reach && MeleeAttacks.lineIsClear(player, eye, at) ? at : null;
    }

    private static boolean hitsOthers(LocalPlayer player, Entity target, Vec3 eye, Rotation look) {
        AttackRange range = player.entityAttackRange();
        Vec3 direction = Vec3.directionFromRotation(look.pitch(), look.yaw());
        Vec3 start = eye.add(direction.scale(range.effectiveMinRange(player)));
        double carried = Math.max(0.0, player.getKnownMovement().dot(direction));
        Vec3 end = eye.add(direction.scale(range.effectiveMaxRange(player) + carried));

        for (Entity entity : player.level().getEntities(player, new AABB(start, end).inflate(1.0))) {
            if (entity != target && entity instanceof LivingEntity && PiercingWeapon.canHitEntity(player, entity) && !MeleeAttacks.isEnemy(player, entity)) {
                AABB box = entity.getBoundingBox().inflate(range.hitboxMargin());
                if (box.contains(start) || box.clip(start, end).isPresent()) {
                    return true;
                }
            }
        }

        return false;
    }
}
