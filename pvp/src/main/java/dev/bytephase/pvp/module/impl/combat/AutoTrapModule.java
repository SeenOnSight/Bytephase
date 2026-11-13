package dev.bytephase.pvp.module.impl.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import dev.bytephase.pvp.chat.ModuleChat;
import dev.bytephase.pvp.event.Stage;
import dev.bytephase.pvp.event.impl.Render3DEvent;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.impl.combat.crystal.Crystals;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.impl.combat.melee.SlotReturn;
import dev.bytephase.pvp.module.setting.BooleanSetting;
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
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;

@Environment(EnvType.CLIENT)
public final class AutoTrapModule extends ClientModule {
    private static final Direction[] SUPPORT_ORDER = new Direction[]{
        Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
    };
    private static final int TARGET_COLOR = -24512;
    private static final int CELL_COLOR = -2130706433;
    private final NumberSetting range = this.addSetting("range", new NumberSetting("Range", 4.0F, 1.0F, 6.0F).step(0.5F));
    private final BooleanSetting rotate = this.addSetting("rotate", new BooleanSetting("Rotate", true));
    private final NumberSetting perTick = this.addSetting("blocks-per-tick", new NumberSetting("Blocks Per Tick", 4.0F, 1.0F, 8.0F).step(1.0F));
    private final BooleanSetting top = this.addSetting("top-block", new BooleanSetting("Top Block", true));
    private final BooleanSetting show = this.addSetting("show-target", new BooleanSetting("Show Target", true));
    private final Minecraft mc = Minecraft.getInstance();
    private final SlotReturn slot = new SlotReturn(this);
    private Player target;
    private List<BlockPos> plan = List.of();
    private boolean warnedEmpty;

    public AutoTrapModule() {
        super("auto-trap", "Auto Trap", "Boxes the nearest enemy in with obsidian: sides at feet and head height, then the top.", ModuleCategory.COMBAT);
        this.perTick.visibleWhen(() -> !this.rotate.get());
    }

    @Override
    protected void onEnable() {
        this.target = null;
        this.plan = List.of();
        this.warnedEmpty = false;
        this.on(TickEvent.TYPE, Stage.PRE, event -> this.tick());
        this.on(Render3DEvent.TYPE, Stage.POST, event -> this.render());
    }

    @Override
    protected void onDisable() {
        this.slot.drop(this.mc, true);
        this.target = null;
        this.plan = List.of();
    }

    private void tick() {
        LocalPlayer player = this.mc.player;
        ClientLevel level = this.mc.level;
        if (player != null && level != null && this.mc.gameMode != null && MeleeAttacks.canAct(this.mc)) {
            this.target = this.pick(player);
            this.plan = this.target == null ? List.of() : this.cells(level, player, this.target);
            if (this.plan.isEmpty()) {
                this.slot.tick(this.mc, true);
            } else {
                int obsidian = Crystals.hotbarSlotOf(player.getInventory(), Items.OBSIDIAN);
                if (obsidian < 0) {
                    if (!this.warnedEmpty) {
                        ModuleChat.send(this, ModuleChat.body().warn("No obsidian in the hotbar"));
                    }

                    this.warnedEmpty = true;
                    this.slot.tick(this.mc, true);
                } else {
                    this.warnedEmpty = false;
                    int budget = this.rotate.get() ? 1 : Math.round(this.perTick.get());
                    int placed = 0;

                    for (BlockPos cell : this.plan) {
                        if (placed < budget) {
                            BlockHitResult hit = support(level, player, cell);
                            if (hit == null) {
                                continue;
                            }

                            if (this.rotate.get()) {
                                BlockHitResult seen = seenByServer(level, player, hit);
                                if (seen == null) {
                                    ServerRotation.request(RotationUtils.fromTo(player.getEyePosition(), hit.getLocation()));
                                    return;
                                }

                                hit = seen;
                            }

                            if (placed != 0 || !MeleeAttacks.actedThisTick(player) && MeleeAttacks.handsFree(this.mc, player)) {
                                if (!this.slot.take(player, obsidian)) {
                                    return;
                                }

                                InteractionResult result = this.mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                                if (result.consumesAction()) {
                                    player.swing(InteractionHand.MAIN_HAND);
                                    MeleeAttacks.noteAction(player);
                                    placed++;
                                }
                                continue;
                            }

                            return;
                        }
                        break;
                    }

                    if (this.rotate.get() && placed > 0) {
                        ServerRotation.request(ServerRotation.sent());
                    }

                    return;
                }
            }
        } else {
            this.slot.tick(this.mc, true);
        }
    }

    private Player pick(LocalPlayer player) {
        double reach = this.range.get() + 1.5;
        Player best = null;

        for (Player other : this.mc.level.players()) {
            if (other != player && other.isAlive() && MeleeAttacks.isEnemy(player, other)) {
                double distance = other.distanceTo(player);
                if (distance <= reach && (best == null || distance < best.distanceTo(player))) {
                    best = other;
                }
            }
        }

        return best;
    }

    private List<BlockPos> cells(ClientLevel level, LocalPlayer player, Player who) {
        AABB box = who.getBoundingBox();
        int feet = Mth.floor(box.minY + 0.01);
        Set<Long> columns = new LinkedHashSet<>();

        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX - 1.0E-4); x++) {
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ - 1.0E-4); z++) {
                columns.add(BlockPos.asLong(x, 0, z));
            }
        }

        List<BlockPos> out = new ArrayList<>();

        for (int dy = 0; dy <= 1; dy++) {
            List<BlockPos> ring = new ArrayList<>();

            for (long key : columns) {
                BlockPos column = BlockPos.of(key).atY(feet + dy);

                for (Direction side : Plane.HORIZONTAL) {
                    BlockPos cell = column.relative(side);
                    if (!columns.contains(BlockPos.asLong(cell.getX(), 0, cell.getZ())) && this.open(level, player, cell) && !ring.contains(cell)) {
                        ring.add(cell);
                    }
                }
            }

            ring.sort(Comparator.comparingDouble(cellx -> cellx.distToCenterSqr(player.getEyePosition())));
            out.addAll(ring);
        }

        if (this.top.get()) {
            for (long key : columns) {
                BlockPos cell = BlockPos.of(key).atY(feet + 2);
                if (this.open(level, player, cell)) {
                    out.add(cell);
                }
            }
        }

        return out;
    }

    private boolean open(ClientLevel level, LocalPlayer player, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        if (state.canBeReplaced() && (state.getFluidState().isEmpty() || state.getFluidState().isSource())) {
            return cell.getCenter().distanceTo(player.getEyePosition()) > player.blockInteractionRange()
                ? false
                : level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), cell, CollisionContext.empty());
        } else {
            return false;
        }
    }

    public static BlockHitResult support(ClientLevel level, LocalPlayer player, BlockPos cell) {
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange();

        for (Direction toward : SUPPORT_ORDER) {
            BlockPos against = cell.relative(toward);
            BlockState state = level.getBlockState(against);
            if (!state.canBeReplaced() && !state.getCollisionShape(level, against).isEmpty()) {
                Direction face = toward.getOpposite();
                Vec3 point = Vec3.atCenterOf(against).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
                if (!(point.distanceTo(eye) > reach)) {
                    BlockHitResult ray = level.clip(
                        new ClipContext(eye, point.add(Vec3.atLowerCornerOf(toward.getUnitVec3i()).scale(0.05)), Block.OUTLINE, Fluid.NONE, player)
                    );
                    if (ray.getType() == Type.BLOCK && ray.getBlockPos().equals(against) && ray.getDirection() == face) {
                        return new BlockHitResult(point, face, against, false);
                    }
                }
            }
        }

        return null;
    }

    public static BlockHitResult seenByServer(ClientLevel level, LocalPlayer player, BlockHitResult wanted) {
        Rotation sent = ServerRotation.sent();
        if (sent == null) {
            return null;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(Vec3.directionFromRotation(sent.pitch(), sent.yaw()).scale(player.blockInteractionRange()));
        BlockHitResult ray = level.clip(new ClipContext(eye, end, Block.OUTLINE, Fluid.NONE, player));
        return ray.getType() == Type.BLOCK && ray.getBlockPos().equals(wanted.getBlockPos()) && ray.getDirection() == wanted.getDirection() ? ray : null;
    }

    private void render() {
        if (this.show.get() && this.target != null) {
            Gizmos.cuboid(this.target.getBoundingBox(), GizmoStyle.stroke(-24512, 2.0F));

            for (BlockPos cell : this.plan) {
                Gizmos.cuboid(cell, GizmoStyle.stroke(-2130706433, 1.0F));
            }
        }
    }
}
