package dev.bytephase.pvp.module.impl.combat.bot;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import dev.bytephase.pvp.easyplace.builder.Aim;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.util.pvp.Rotation;
import dev.bytephase.pvp.util.pvp.RotationUtils;
import dev.bytephase.pvp.util.pvp.TargetingUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
final class BotBody {
    private static final double KEEP_EXTRA = 8.0;
    private static final float FAR_OFF = 40.0F;
    private static final int FLIP_COOLDOWN = 8;
    private static final int DROP_SCAN = 16;
    private Player target;
    private boolean grounded;
    private int side = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
    private int sideUntil;
    private int flippedAt = -100;
    private double sideCarry;
    private int tick;

    Player target() {
        return this.target;
    }

    int tick() {
        return this.tick;
    }

    void forget() {
        this.target = null;
    }

    void hold(Player who) {
        this.target = who;
    }

    void stayGrounded(boolean grounded) {
        this.grounded = grounded;
    }

    void release(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyUse.setDown(false);
    }

    Player pick(Minecraft mc, LocalPlayer self, double range) {
        return this.pick(mc, self, range, range + 8.0);
    }

    Player pick(Minecraft mc, LocalPlayer self, double range, double keep) {
        this.tick++;
        if (this.tick >= this.sideUntil) {
            this.side = -this.side;
            this.sideUntil = this.tick + 10 + ThreadLocalRandom.current().nextInt(13);
        }

        if (this.target != null
            && (
                !this.target.isAlive()
                    || this.target.isRemoved()
                    || this.target.level() != self.level()
                    || this.target.distanceTo(self) > keep
                    || !MeleeAttacks.isEnemy(self, this.target)
            )) {
            this.target = null;
        }

        if (this.target != null) {
            return this.target;
        }

        Player best = null;

        for (Player other : mc.level.players()) {
            if (other != self && other.isAlive() && !other.isSpectator() && MeleeAttacks.isEnemy(self, other)) {
                double distance = other.distanceTo(self);
                if (distance <= range && (best == null || distance < best.distanceTo(self))) {
                    best = other;
                }
            }
        }

        this.target = best;
        return best;
    }

    void flipSide() {
        if (this.tick - this.flippedAt >= 8) {
            this.flippedAt = this.tick;
            this.side = -this.side;
            this.sideUntil = this.tick + 10 + ThreadLocalRandom.current().nextInt(13);
        }
    }

    void face(Minecraft mc, LocalPlayer self, Player who, float speed) {
        Vec3 eye = self.getEyePosition();
        Vec3 drift = who.position().subtract(who.xo, who.yo, who.zo);
        AABB box = who.getBoundingBox().move(drift);
        AABB core = box.deflate(box.getXsize() * 0.2, box.getYsize() * 0.15, box.getZsize() * 0.2);
        Rotation look = RotationUtils.fromTo(eye, TargetingUtils.closestPoint(eye, core));
        float off = Math.abs(Mth.wrapDegrees(look.yaw() - self.getYRot()));
        Aim.turn(mc, look.yaw(), look.pitch(), (off > 40.0F ? 27.0F : 22.0F) * speed);
    }

    void drive(Minecraft mc, LocalPlayer self, int forward, double sideways, boolean sprint, boolean jump) {
        float yaw = self.getYRot();
        Vec3 ahead = Vec3.directionFromRotation(0.0F, yaw);
        if (forward > 0 && safeStep(self, ahead)) {
            mc.options.keyUp.setDown(true);
            mc.options.keySprint.setDown(sprint);
            if (self.horizontalCollision && self.onGround()) {
                jump = true;
            }
        } else if (forward < 0 && safeStep(self, ahead.scale(-1.0))) {
            mc.options.keyDown.setDown(true);
        }

        if (jump && !this.grounded && self.onGround() && !self.isInWater()) {
            mc.options.keyJump.setDown(true);
        }

        this.sideCarry = this.sideCarry + Math.max(0.0, Math.min(1.0, sideways));
        if (!(this.sideCarry < 1.0)) {
            this.sideCarry--;
            Vec3 left = Vec3.directionFromRotation(0.0F, yaw - 90.0F);
            Vec3 way = this.side > 0 ? left : left.scale(-1.0);
            if (!safeStep(self, way)) {
                this.side = -this.side;
                way = way.scale(-1.0);
                if (!safeStep(self, way)) {
                    return;
                }
            }

            (this.side > 0 ? mc.options.keyLeft : mc.options.keyRight).setDown(true);
        }
    }

    void keepDistance(Minecraft mc, LocalPlayer self, Player who, double min, double ideal, double max, boolean dance) {
        double distance = Math.hypot(who.getX() - self.getX(), who.getZ() - self.getZ());
        int forward = distance > ideal + 0.25 ? 1 : (distance < min ? -1 : 0);
        this.drive(mc, self, forward, dance && distance <= max + 1.0 ? 1.0 : 0.0, distance > max, false);
    }

    void flee(Minecraft mc, LocalPlayer self, Player from) {
        float away = RotationUtils.fromTo(from.getEyePosition(), self.getEyePosition()).yaw();
        Aim.turn(mc, away, 5.0F, 27.0F);
        this.drive(mc, self, 1, 0.0, true, false);
    }

    void away(Minecraft mc, LocalPlayer self, Vec3 point) {
        Vec3 way = self.position().subtract(point).multiply(1.0, 0.0, 1.0);
        if (way.lengthSqr() < 1.0E-6) {
            way = Vec3.directionFromRotation(0.0F, self.getYRot()).scale(-1.0);
        }

        this.go(mc, self, way);
    }

    boolean toward(Minecraft mc, LocalPlayer self, Vec3 point) {
        Vec3 way = point.subtract(self.position()).multiply(1.0, 0.0, 1.0);
        if (way.lengthSqr() < 0.09) {
            return true;
        }

        this.go(mc, self, way);
        if (!this.grounded && self.horizontalCollision && self.onGround()) {
            mc.options.keyJump.setDown(true);
        }

        return false;
    }

    void dropToward(Minecraft mc, LocalPlayer self, Vec3 point) {
        Vec3 way = point.subtract(self.position()).multiply(1.0, 0.0, 1.0);
        if (!(way.lengthSqr() < 0.09) && safeStep(self, way.normalize(), 16)) {
            keys(mc, self, way);
        }
    }

    private void go(Minecraft mc, LocalPlayer self, Vec3 way) {
        if (safeStep(self, way.normalize())) {
            keys(mc, self, way);
        }
    }

    private static void keys(Minecraft mc, LocalPlayer self, Vec3 way) {
        float wanted = (float)Math.toDegrees(Math.atan2(-way.x, way.z));
        float relative = Mth.wrapDegrees(wanted - self.getYRot());
        float off = Math.abs(relative);
        if (off <= 67.5F) {
            mc.options.keyUp.setDown(true);
        }

        if (off >= 112.5F) {
            mc.options.keyDown.setDown(true);
        }

        if (relative <= -22.5F && relative >= -157.5F) {
            mc.options.keyLeft.setDown(true);
        }

        if (relative >= 22.5F && relative <= 157.5F) {
            mc.options.keyRight.setDown(true);
        }
    }

    void backOff(Minecraft mc, LocalPlayer self) {
        this.drive(mc, self, -1, 0.0, false, false);
    }

    boolean use(Minecraft mc, LocalPlayer self, Predicate<ItemStack> want) {
        int slot = hotbarSlot(self, want);
        if (slot < 0) {
            return false;
        } else {
            Inventory inventory = self.getInventory();
            if (inventory.getSelectedSlot() != slot) {
                inventory.setSelectedSlot(slot);
                return true;
            } else {
                mc.options.keyUse.setDown(true);
                return true;
            }
        }
    }

    static int hotbarSlot(LocalPlayer self, Predicate<ItemStack> want) {
        Inventory inventory = self.getInventory();
        if (want.test(inventory.getItem(inventory.getSelectedSlot()))) {
            return inventory.getSelectedSlot();
        }

        for (int i = 0; i < 9; i++) {
            if (want.test(inventory.getItem(i))) {
                return i;
            }
        }

        return -1;
    }

    static boolean safeStep(LocalPlayer self, Vec3 way) {
        return safeStep(self, way, 3);
    }

    private static boolean safeStep(LocalPlayer self, Vec3 way, int deepest) {
        Level level = self.level();
        BlockPos feet = BlockPos.containing(self.getX() + way.x * 0.8, self.getY() + 0.1, self.getZ() + way.z * 0.8);

        for (int down = 0; down <= deepest; down++) {
            BlockPos pos = feet.below(down);
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.LAVA)
                || state.is(BlockTags.FIRE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.COBWEB)
                || down <= 1 && state.is(Blocks.MAGMA_BLOCK)) {
                return false;
            }

            if (!state.getCollisionShape(level, pos).isEmpty()) {
                return true;
            }
        }

        return false;
    }
}
