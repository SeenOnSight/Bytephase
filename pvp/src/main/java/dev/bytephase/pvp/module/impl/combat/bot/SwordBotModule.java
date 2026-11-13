package dev.bytephase.pvp.module.impl.combat.bot;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import dev.bytephase.pvp.chat.ModuleChat;
import dev.bytephase.pvp.event.Stage;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.CombatMacroLock;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import dev.bytephase.pvp.module.setting.ModeSetting;
import dev.bytephase.pvp.module.setting.NumberSetting;
import dev.bytephase.pvp.util.pvp.InputControl;
import dev.bytephase.pvp.util.pvp.Rotation;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import dev.bytephase.pvp.util.pvp.TargetingUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class SwordBotModule extends ClientModule {
    private static final String PATIENT = "Patient";
    private static final String AGGRESSIVE = "Aggressive";
    private static final Predicate<ItemStack> GAPPLE = stack -> stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
    private static final double HIT_RANGE = 3.0;
    private static final double CHASE_RANGE = 6.0;
    private static final int RESET_WINDOW = 3;
    private final ModeSetting style = this.addSetting("style", new ModeSetting("Style", "Patient", "Patient", "Aggressive"));
    private final NumberSetting range = this.addSetting("range", new NumberSetting("Fight Range", 16.0F, 6.0F, 32.0F).step(1.0F));
    private final NumberSetting turnSpeed = this.addSetting("turn-speed", new NumberSetting("Turn Speed", 1.0F, 0.5F, 1.5F).step(0.05F));
    private final BooleanSetting strafe = this.addSetting("strafe", new BooleanSetting("Strafe", true));
    private final BooleanSetting crits = this.addSetting("crits", new BooleanSetting("Jump Crits", true));
    private final BooleanSetting jumpReset = this.addSetting("jump-reset", new BooleanSetting("Jump Reset", true));
    private final NumberSetting eatBelow = this.addSetting("eat-below", new NumberSetting("Gapple Below HP", 10.0F, 0.0F, 20.0F).step(1.0F));
    private final Minecraft mc = Minecraft.getInstance();
    private final BotBody body = new BotBody();
    private Player fought;
    private double closing;
    private double lastTheirReach = Double.NaN;
    private double jitter;
    private int tapTicks;
    private boolean tapBack;
    private boolean critJump;
    private int landedTicks;
    private int resetUntil;
    private int lastHurt;
    private boolean eating;

    public SwordBotModule() {
        super(
            "sword-bot",
            "Sword PvP Bot",
            "Fights the nearest enemy player with a sword or axe: keeps out of their reach while charging, times crits, strafes and eats gapples.",
            ModuleCategory.COMBAT
        );
    }

    @Override
    protected void onEnable() {
        this.body.forget();
        this.newFight(null);
        this.eating = false;
        this.on(TickEvent.TYPE, Stage.PRE, event -> this.tick());
    }

    @Override
    protected void onDisable() {
        this.body.release(this.mc);
        this.body.forget();
        CombatMacroLock.release(this);
    }

    private void newFight(Player target) {
        this.fought = target;
        this.closing = 0.0;
        this.lastTheirReach = Double.NaN;
        this.jitter = randomJitter();
        this.tapTicks = 0;
        this.critJump = false;
        this.resetUntil = 0;
    }

    private void tick() {
        LocalPlayer player = this.mc.player;
        this.body.release(this.mc);
        if (player != null && this.mc.level != null && MeleeAttacks.canAct(this.mc) && !player.isDeadOrDying()) {
            Player target = this.body.pick(this.mc, player, this.range.get());
            int tick = this.body.tick();
            if (target != this.fought) {
                if (target != null) {
                    ModuleChat.send(this, ModuleChat.body().text("Fighting ").name(target.getName().getString()).text("."));
                } else if (this.fought != null) {
                    ModuleChat.send(this, ModuleChat.body().text("No enemy in range, waiting."));
                }

                this.newFight(target);
            }

            if (target != null) {
                this.body.face(this.mc, player, target, this.turnSpeed.get());
                boolean low = player.getHealth() <= this.eatBelow.get() && player.getAbsorptionAmount() < 4.0F;
                if ((low || this.eating && player.isUsingItem()) && this.body.use(this.mc, player, GAPPLE)) {
                    this.eating = true;
                    this.body.backOff(this.mc, player);
                } else {
                    this.eating = false;
                    if (!player.isUsingItem()) {
                        holdWeapon(player);
                        Vec3 eye = player.getEyePosition();
                        double myReach = eye.distanceTo(TargetingUtils.closestPoint(eye, target.getBoundingBox()));
                        Vec3 theirEye = target.getEyePosition();
                        double theirReach = theirEye.distanceTo(TargetingUtils.closestPoint(theirEye, player.getBoundingBox()));
                        if (!Double.isNaN(this.lastTheirReach)) {
                            this.closing = this.closing * 0.5 + (this.lastTheirReach - theirReach) * 0.5;
                        }

                        this.lastTheirReach = theirReach;
                        int ready = MeleeAttacks.ticksToFull(player);
                        float charge = MeleeAttacks.charge(player);
                        double facing = facingAngle(target, player);
                        Vec3 away = new Vec3(target.getX() - player.getX(), 0.0, target.getZ() - player.getZ()).normalize();
                        Vec3 theirMotion = target.position().subtract(target.xo, target.yo, target.zo);
                        double fleeing = theirMotion.x * away.x + theirMotion.z * away.z;
                        boolean iframes = target.hurtTime > 1;
                        boolean shielded = target.isBlocking();
                        boolean threatened = theirReach <= 3.15 && facing <= 40.0;
                        boolean close = myReach <= 3.0;
                        double enemyReach = 3.35
                            + 0.12 * Mth.clamp(Math.round(ping(player) / 50.0F), 0, 3)
                            + 2.5 * Mth.clamp(this.closing, 0.0, 0.35)
                            - (this.style.is("Aggressive") ? 0.2 : 0.0);
                        double gap = ready <= 0
                            ? 2.9
                            : (ready <= 2 ? 3.08 + 0.1 * (ready - 1) + this.jitter : Math.max(enemyReach + this.jitter, 2.9 + 0.26 * ready));
                        if (facing < 8.0 && theirReach < 4.2) {
                            this.body.flipSide();
                        }

                        double side = this.strafe.get() ? 1.0 : 0.0;
                        if (player.hurtTime > this.lastHurt && player.hurtTime >= 8) {
                            this.resetUntil = tick + 3;
                        }

                        this.lastHurt = player.hurtTime;
                        if (this.tapTicks > 0) {
                            this.tapTicks--;
                            this.body.drive(this.mc, player, this.tapBack ? -1 : 0, side * (this.tapBack ? 0.8 : 1.0), false, false);
                        } else if ((!(myReach > 6.0) || !(myReach > gap + 1.0)) && !(Math.abs(target.getY() - player.getY()) > 2.5)) {
                            if (this.critJump) {
                                if (player.onGround()) {
                                    if (++this.landedTicks >= 2) {
                                        this.critJump = false;
                                    }
                                } else {
                                    this.landedTicks = 0;
                                }

                                if (this.critJump) {
                                    boolean falling = ready <= 1 && player.getDeltaMovement().y < 0.1;
                                    this.body.drive(this.mc, player, falling ? 0 : 1, falling ? side * 0.35 : 0.0, !falling, false);
                                    if (ready <= 0 && close && !iframes) {
                                        InputControl.dropSprint();
                                        this.strike(player, target, 0.95F, true);
                                    }

                                    return;
                                }
                            }

                            if (this.crits.get() && player.onGround() && !player.isInWater() && !threatened && this.critWindow(ready, myReach, facing, fleeing)
                                )
                             {
                                this.critJump = true;
                                this.landedTicks = 0;
                                this.body.drive(this.mc, player, 1, 0.0, true, true);
                            } else if (ready <= 0 && !shielded) {
                                if (iframes) {
                                    this.body.drive(this.mc, player, myReach > 3.12 ? 1 : (myReach < 2.88 ? -1 : 0), side, false, false);
                                } else {
                                    this.body.drive(this.mc, player, myReach < 1.6 ? -1 : 1, myReach <= 3.5 ? side * 0.35 : 0.0, myReach >= 1.6, false);
                                    if (close) {
                                        this.strike(player, target, 0.95F, false);
                                    }
                                }
                            } else {
                                if (shielded) {
                                    gap = Math.max(gap, enemyReach);
                                }

                                int band = theirReach > gap + 0.12 ? 1 : (theirReach < gap - 0.12 ? -1 : 0);
                                if (band < 0 && fleeing > 0.15) {
                                    band = 0;
                                }

                                boolean counter = threatened && close && charge >= 0.6F && !iframes && !shielded;
                                boolean resetting = this.jumpReset.get() && tick < this.resetUntil && player.onGround() && !player.isInWater();
                                if (resetting) {
                                    this.body.drive(this.mc, player, 1, 0.0, true, true);
                                } else if (ready <= 2 && !counter && this.strafe.get() && !shielded) {
                                    this.body.drive(this.mc, player, 1, 1.0, true, false);
                                } else {
                                    double amount = !this.strafe.get() ? 0.0 : (band == 0 ? 1.0 : (band < 0 ? 0.8 : 0.5));
                                    this.body.drive(this.mc, player, band, amount, band > 0, false);
                                }

                                if (counter) {
                                    this.strike(player, target, 0.6F, false);
                                }
                            }
                        } else {
                            this.body.drive(this.mc, player, 1, 0.0, true, false);
                            if (close && ready <= 0 && !iframes && !shielded) {
                                this.strike(player, target, 0.95F, false);
                            }
                        }
                    }
                }
            }
        } else {
            this.body.forget();
        }
    }

    private boolean critWindow(int ready, double myReach, double facing, double fleeing) {
        if (ready >= 7 && ready <= 10 && myReach >= 2.3 && myReach <= 5.0) {
            return true;
        } else {
            return ready >= 3 && ready <= 10 && !(myReach < 2.3) && !(myReach > 4.2)
                ? facing >= 100.0 && fleeing > 0.05 || this.style.is("Aggressive") && facing > 60.0
                : false;
        }
    }

    private void strike(LocalPlayer player, Player target, float charge, boolean crit) {
        if (!CombatMacroLock.heldByOther(this) && !MeleeAttacks.actedThisTick(player) && this.mc.missTime <= 0) {
            if (!(MeleeAttacks.charge(player) < charge) && MeleeAttacks.isMeleeWeapon(player.getMainHandItem())) {
                if (!crit || MeleeAttacks.airborne(player) && !(player.getDeltaMovement().y >= 0.0)) {
                    Rotation sent = ServerRotation.sent();
                    if (sent != null) {
                        Vec3 eye = player.getEyePosition();
                        double reach = Math.min(3.0, player.entityInteractionRange());
                        AABB box = target.getBoundingBox();
                        Vec3 at = box.contains(eye)
                            ? eye
                            : box.clip(eye, eye.add(Vec3.directionFromRotation(sent.pitch(), sent.yaw()).scale(reach))).orElse(null);
                        if (at != null && MeleeAttacks.lineIsClear(player, eye, at)) {
                            if (MeleeAttacks.attack(this.mc, new EntityHitResult(target, at))) {
                                MeleeAttacks.claimTick(this.mc);
                                this.jitter = randomJitter();
                                this.tapTicks = 1;
                                this.tapBack = player.getEyePosition().distanceTo(TargetingUtils.closestPoint(eye, box)) < 2.3;
                                if (this.tapBack) {
                                    this.tapTicks = 2;
                                }

                                this.critJump = false;
                            }
                        }
                    }
                }
            }
        }
    }

    private static double facingAngle(Player target, LocalPlayer self) {
        Vec3 look = Vec3.directionFromRotation(target.getXRot(), target.getYHeadRot());
        Vec3 toMe = self.position().add(0.0, 1.1, 0.0).subtract(target.getEyePosition()).normalize();
        return Math.toDegrees(Math.acos(Mth.clamp(look.dot(toMe), -1.0, 1.0)));
    }

    private static int ping(LocalPlayer player) {
        PlayerInfo info = player.connection.getPlayerInfo(player.getUUID());
        return info == null ? 0 : Math.max(0, info.getLatency());
    }

    private static double randomJitter() {
        return ThreadLocalRandom.current().nextDouble(-0.06, 0.06);
    }

    private static void holdWeapon(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        if (!MeleeAttacks.isMeleeWeapon(player.getMainHandItem())) {
            int best = -1;
            double most = 0.0;

            for (int i = 0; i < 9; i++) {
                ItemStack stack = inventory.getItem(i);
                if (MeleeAttacks.isMeleeWeapon(stack)) {
                    double damage = MeleeAttacks.attackDamage(stack);
                    if (best < 0 || damage > most) {
                        best = i;
                        most = damage;
                    }
                }
            }

            if (best >= 0) {
                inventory.setSelectedSlot(best);
            }
        }
    }
}
