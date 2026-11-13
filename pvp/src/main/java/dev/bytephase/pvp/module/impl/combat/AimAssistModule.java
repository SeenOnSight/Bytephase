package dev.bytephase.pvp.module.impl.combat;

import java.util.concurrent.ThreadLocalRandom;
import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.CombatMacroLock;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import dev.bytephase.pvp.module.setting.NumberSetting;
import dev.bytephase.pvp.util.combat.CombatTarget;
import dev.bytephase.pvp.util.pvp.Rotation;
import dev.bytephase.pvp.util.pvp.RotationUtils;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import dev.bytephase.pvp.util.pvp.TargetingUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class AimAssistModule extends ClientModule {
    private static final long LAST_HIT_MS = 1500L;
    private static final double STICK_RANGE = 0.5;
    private static final double STICK_FOV = 1.3;
    private static final double[] STILL = new double[]{0.0, 0.0};
    private static final long MINING_QUIET_MS = 250L;
    private final NumberSetting range = this.addSetting("range", new NumberSetting("Range", 4.0F, 3.0F, 6.0F).step(0.1F));
    private final NumberSetting fov = this.addSetting("fov", new NumberSetting("FOV", 60.0F, 10.0F, 180.0F).step(5.0F));
    private final NumberSetting speed = this.addSetting("speed", new NumberSetting("Speed", 60.0F, 10.0F, 180.0F).step(5.0F));
    private final BooleanSetting weaponsOnly = this.addSetting("weapons-only", new BooleanSetting("Weapons Only", true));
    private final BooleanSetting vertical = this.addSetting("vertical", new BooleanSetting("Vertical", true));
    private Player target;
    private double pace;
    private double pendingYaw;
    private double pendingPitch;
    private long minedAt;

    public AimAssistModule() {
        super("aim-assist", "Aim Assist", "Eases your crosshair onto the nearest enemy player, even while the mouse is still.", ModuleCategory.COMBAT);
    }

    @Override
    public int settingsVersion() {
        return 1;
    }

    @Override
    public void migrateSettings(int savedVersion) {
        if (savedVersion < 1) {
            this.speed.set(60.0F);
        }
    }

    @Override
    protected void onDisable() {
        this.follow(null);
    }

    @EventHandler
    public void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.gameMode != null && mc.gameMode.isDestroying()) {
            this.minedAt = Util.getMillis();
        }

        if (this.canFollow(mc, player)) {
            this.follow(this.choose(mc, player));
        } else {
            this.follow(null);
        }
    }

    public double[] nudge(LocalPlayer player, double userYaw, double userPitch, double seconds) {
        Minecraft mc = Minecraft.getInstance();
        Rotation want = this.target != null && this.canFollow(mc, player)
            && this.target.level() == mc.level && MeleeAttacks.isEnemy(player, this.target)
            ? this.aim(player, player.getEyePosition(), core(this.target)) : null;
        if (want == null) {
            this.pendingYaw = 0.0;
            this.pendingPitch = 0.0;
            return STILL;
        }

        if (!Double.isFinite(seconds) || seconds <= 0.0) return STILL;
        double dt = Math.min(seconds, 0.05);
        float yawError = Mth.wrapDegrees(want.yaw() - player.getYRot());
        float pitchError = this.vertical.get() ? want.pitch() - player.getXRot() : 0.0F;
        if (userYaw * yawError < 0.0) {
            yawError = 0.0F;
            this.pendingYaw = 0.0;
        }

        if (userPitch * pitchError < 0.0) {
            pitchError = 0.0F;
            this.pendingPitch = 0.0;
        }

        double cap = this.speed.get() * this.pace * dt;
        double ease = 1.0 - Math.exp(-12.0 * dt);
        double k = RotationUtils.mouseScale(Minecraft.getInstance(), player);
        double degreesPerCount = (float)k * 0.15F;
        this.pendingYaw = this.pendingYaw + Mth.clamp(yawError * ease, -cap, cap) / degreesPerCount;
        this.pendingPitch = this.pendingPitch + Mth.clamp(pitchError * ease * 0.6, -cap, cap) / degreesPerCount;
        long yawCounts = (long)this.pendingYaw;
        long pitchCounts = (long)this.pendingPitch;
        this.pendingYaw -= yawCounts;
        this.pendingPitch -= pitchCounts;
        return new double[]{yawCounts * k, pitchCounts * k};
    }

    private boolean canFollow(Minecraft mc, LocalPlayer player) {
        return MeleeAttacks.canAct(mc) && player == mc.player
            && mc.isWindowActive() && mc.mouseHandler.isMouseGrabbed() && !mc.isPaused()
            && !player.isDeadOrDying() && !player.isSpectator()
            && !player.isUsingItem() && !mc.options.keyUse.isDown()
            && !mc.gameMode.isDestroying()
            && (!this.weaponsOnly.get() || MeleeAttacks.isMeleeWeapon(player.getMainHandItem()))
            && !ServerRotation.pending() && !CombatMacroLock.heldByOther(this)
            && Util.getMillis() - this.minedAt >= MINING_QUIET_MS;
    }

    private void follow(Player next) {
        if (next != this.target) {
            this.target = next;
            this.pace = ThreadLocalRandom.current().nextDouble(0.85, 1.15);
            this.pendingYaw = 0.0;
            this.pendingPitch = 0.0;
        }
    }

    private Player choose(Minecraft mc, LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        double halfFov = this.fov.get() / 2.0;
        if (CombatTarget.lastHit(1500L) instanceof Player hit && this.offAngle(player, eye, hit, this.range.get()) <= halfFov) {
            return hit;
        } else {
            if (this.target != null
                && this.target.level() == player.level()
                && this.offAngle(player, eye, this.target, this.range.get() + 0.5) <= halfFov * 1.3) {
                return this.target;
            }

            Player best = null;
            double bestAngle = halfFov;

            for (Player candidate : mc.level.players()) {
                double angle = this.offAngle(player, eye, candidate, this.range.get());
                if (!(angle > bestAngle)) {
                    best = candidate;
                    bestAngle = angle;
                }
            }

            return best;
        }
    }

    private double offAngle(LocalPlayer player, Vec3 eye, Player candidate, double reach) {
        if (MeleeAttacks.isEnemy(player, candidate) && (!candidate.isInvisible() || armourShows(candidate))) {
            if (eye.distanceTo(TargetingUtils.closestPoint(eye, candidate.getBoundingBox())) > reach) {
                return Double.MAX_VALUE;
            }

            AABB core = core(candidate);
            if (!MeleeAttacks.lineIsClear(player, eye, core.getCenter())) {
                return Double.MAX_VALUE;
            }

            Rotation want = this.aim(player, eye, core);
            return want == null ? 0.0 : RotationUtils.angleTo(RotationUtils.entityRotation(player), want);
        } else {
            return Double.MAX_VALUE;
        }
    }

    private Rotation aim(LocalPlayer player, Vec3 eye, AABB core) {
        Vec3 view = player.getViewVector(1.0F);
        if (core.clip(eye, eye.add(view.scale(this.range.get() + 1.0))).isPresent()) {
            return null;
        }

        Vec3 near = TargetingUtils.closestPoint(eye.add(view.scale(eye.distanceTo(core.getCenter()))), core);
        return RotationUtils.fromTo(eye, near);
    }

    private static boolean armourShows(Player player) {
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (slot.isArmor() && !player.getItemBySlot(slot).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private static AABB core(Player player) {
        AABB box = player.getBoundingBox();
        return box.deflate(box.getXsize() * 0.25, box.getYsize() * 0.1, box.getXsize() * 0.25);
    }
}
