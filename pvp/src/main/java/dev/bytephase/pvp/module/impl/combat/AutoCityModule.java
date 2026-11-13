package dev.bytephase.pvp.module.impl.combat;

import dev.bytephase.pvp.chat.ModuleChat;
import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.CombatMacroLock;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.impl.combat.crystal.Combo;
import dev.bytephase.pvp.module.impl.combat.crystal.CrystalDamage;
import dev.bytephase.pvp.module.impl.combat.crystal.Crystals;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.impl.combat.melee.SlotReturn;
import dev.bytephase.pvp.util.pvp.Rotation;
import dev.bytephase.pvp.util.pvp.RotationUtils;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

@Environment(EnvType.CLIENT)
public final class AutoCityModule extends ClientModule {
    private static final float CRYSTAL_POWER = 6.0F;
    private static final double FACE_INSET = 0.05;
    private static final int GIVE_UP_TICKS = 200;
    private static volatile AutoCityModule active;
    private final SlotReturn slots = new SlotReturn(this);
    private LivingEntity target;
    private BlockPos feet;
    private BlockPos block;
    private int ticks;
    private boolean broken;

    public AutoCityModule() {
        super(
            "auto-city",
            "Auto City",
            "Mines a surround block of the best enemy in a hole, then turns off so Crystal Aura can blow through it.",
            ModuleCategory.COMBAT
        );
        this.usesNotifications();
    }

    @Override
    public boolean persistEnabled() {
        return false;
    }

    @Override
    protected void onEnable() {
        this.target = null;
        this.feet = null;
        this.block = null;
        this.ticks = 0;
        this.broken = false;
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
        Minecraft mc = Minecraft.getInstance();
        if (!this.broken && this.block != null && mc.gameMode != null && mc.gameMode.isDestroying()) {
            mc.gameMode.stopDestroyBlock();
        }

        this.slots.drop(mc, true);
        CombatMacroLock.release(this);
        this.target = null;
        this.block = null;
    }

    public static boolean mining() {
        AutoCityModule module = active;
        return module != null && module.block != null && !module.broken;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || player.isDeadOrDying() || this.broken || userClicks(mc)) {
            this.requestDisable();
        } else if (this.block == null && !this.pick(mc, player)) {
            this.stop("No enemy in a hole within reach");
        } else {
            ClientLevel level = mc.level;
            BlockState state = level.getBlockState(this.block);
            if (state.canBeReplaced()) {
                this.opened();
            } else {
                this.ticks++;
                String problem = this.problem(player, state);
                if (problem != null) {
                    this.stop(problem);
                } else if (MeleeAttacks.canAct(mc) && !AutoInvTotemModule.wantsHotbar() && !player.isUsingItem() && !player.isHandsBusy()) {
                    if (CombatMacroLock.tryAcquire(this) && this.slots.take(player, toolSlot(player.getInventory(), state))) {
                        BlockHitResult aim = aim(level, player, this.block);
                        if (aim == null) {
                            this.stop("Lost sight of the block");
                        } else {
                            ServerRotation.request(RotationUtils.fromTo(player.getEyePosition(), aim.getLocation()));
                            BlockHitResult held = held(level, player, this.block);
                            if (held != null && !MeleeAttacks.actedThisTick(player)) {
                                if (mc.gameMode.continueDestroyBlock(this.block, held.getDirection())) {
                                    player.swing(InteractionHand.MAIN_HAND);
                                }

                                MeleeAttacks.noteAction(player);
                                if (level.getBlockState(this.block).canBeReplaced()) {
                                    this.opened();
                                    this.note(ModuleChat.body().text("Opened ").name(this.target.getName().getString()).text("'s surround"));
                                }
                            }
                        }
                    }
                } else {
                    if (mc.gameMode.isDestroying()) {
                        mc.gameMode.stopDestroyBlock();
                    }

                    CombatMacroLock.release(this);
                }
            }
        }
    }

    private void opened() {
        this.broken = true;
        CombatMacroLock.release(this);
    }

    private void stop(String reason) {
        this.note(ModuleChat.body().text(reason));
        this.requestDisable();
    }

    private static boolean userClicks(Minecraft mc) {
        return mc.screen == null
            && (Crystals.isPhysicallyDown(mc, mc.options.keyAttack) || MeleeAttacks.clickPending(mc) || Crystals.isPhysicallyDown(mc, mc.options.keyUse));
    }

    private boolean pick(Minecraft mc, LocalPlayer player) {
        ClientLevel level = mc.level;
        float best = 0.0F;

        for (LivingEntity enemy : Combo.enemies(mc, player)) {
            if (Combo.inHole(enemy)) {
                BlockPos at = enemy.blockPosition();

                for (Direction side : Plane.HORIZONTAL) {
                    BlockPos pos = at.relative(side);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getDestroySpeed(level, pos) < 0.0F)
                        && Crystals.isBase(level.getBlockState(pos.below()))
                        && player.isWithinBlockInteractionRange(pos, 0.0)
                        && toolSlot(player.getInventory(), state) != -1
                        && aim(level, player, pos) != null) {
                        Vec3 blast = Crystals.explosionAt(pos.below());
                        float own = CrystalDamage.estimate(blast, player, 6.0F, pos);
                        if (!CrystalDamage.endangers(player, own)) {
                            float value = CrystalDamage.estimate(blast, enemy, 6.0F, pos) - 0.5F * own;
                            if (!(value <= best)) {
                                best = value;
                                this.target = enemy;
                                this.feet = at;
                                this.block = pos;
                            }
                        }
                    }
                }
            }
        }

        return this.block != null;
    }

    private String problem(LocalPlayer player, BlockState state) {
        BlockPos at = this.target.blockPosition();
        if (!this.target.isAlive() || at.getX() != this.feet.getX() || at.getZ() != this.feet.getZ() || Math.abs(at.getY() - this.feet.getY()) > 1) {
            return "Target left the hole";
        } else if (!player.isWithinBlockInteractionRange(this.block, 0.0)) {
            return "Block went out of reach";
        } else if (toolSlot(player.getInventory(), state) == -1) {
            return "No pickaxe that can break it";
        } else if (!ServerRotation.canRotate(player)) {
            return "Can't aim while flying, swimming or riding";
        } else {
            return this.ticks > 200 ? "Gave up, the block took too long" : null;
        }
    }

    private void note(ModuleChat.Body body) {
        if (this.notificationsOn()) {
            ModuleChat.actionBar(this, body);
        }
    }

    private static int toolSlot(Inventory inventory, BlockState state) {
        int best = -1;
        float fastest = 0.0F;

        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isCorrectToolForDrops(state) && !(stack.getDestroySpeed(state) <= fastest)) {
                best = slot;
                fastest = stack.getDestroySpeed(state);
            }
        }

        return best;
    }

    private static BlockHitResult aim(ClientLevel level, LocalPlayer player, BlockPos pos) {
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange();
        BlockHitResult best = null;
        double nearest = reach * reach;

        for (Direction face : Direction.values()) {
            Vec3 point = Vec3.atCenterOf(pos).relative(face, 0.5);
            double distance = eye.distanceToSqr(point);
            if (!(distance > nearest)) {
                BlockHitResult hit = level.clip(new ClipContext(eye, point.relative(face, -0.05), Block.OUTLINE, Fluid.NONE, player));
                if (hit.getType() == Type.BLOCK && hit.getBlockPos().equals(pos) && hit.getDirection() == face) {
                    best = hit;
                    nearest = distance;
                }
            }
        }

        return best;
    }

    private static BlockHitResult held(ClientLevel level, LocalPlayer player, BlockPos pos) {
        Rotation sent = ServerRotation.sent();
        if (sent == null) {
            return null;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(Vec3.directionFromRotation(sent.pitch(), sent.yaw()).scale(player.blockInteractionRange()));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, Block.OUTLINE, Fluid.NONE, player));
        return hit.getType() == Type.BLOCK && hit.getBlockPos().equals(pos) ? hit : null;
    }
}
