package dev.bytephase.pvp.module.impl.combat;

import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.impl.PacketAppliedEvent;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.CombatMacroLock;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.impl.combat.anchor.AnchorGuard;
import dev.bytephase.pvp.module.impl.combat.crystal.CrystalDamage;
import dev.bytephase.pvp.module.impl.combat.crystal.Crystals;
import dev.bytephase.pvp.module.impl.combat.crystal.LootGuard;
import dev.bytephase.pvp.module.impl.combat.crystal.LootLatch;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.impl.misc.AutoEatModule;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import dev.bytephase.pvp.module.setting.ModeSetting;
import dev.bytephase.pvp.module.setting.NumberSetting;
import dev.bytephase.pvp.util.pvp.Rotation;
import dev.bytephase.pvp.util.pvp.PlacementRate;
import dev.bytephase.pvp.util.pvp.RotationUtils;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

@Environment(EnvType.CLIENT)
public final class SingleAnchorMacroModule extends ClientModule {
    private static final String SINGLE = "Single";
    private static final String DOUBLE = "Double";
    private static final String DETONATE = "Detonate";
    private static final String CANCEL = "Cancel";
    private static final int MAX_STEPS_PER_TICK = 4;
    private static final int GUARD_TRIES = 2;
    private static final int SETTLE_TICKS = 5;
    private static final float BLAST_PROOF = 600.0F;
    private final NumberSetting placeCps = this.addSetting("place-cps", new NumberSetting("Place CPS", 20.0F, 0.5F, 20.0F).step(0.5F));
    private final PlacementRate placementRate = new PlacementRate();
    private final NumberSetting breakCps = this.addSetting("break-cps", new NumberSetting("Break CPS", 20.0F, 0.5F, 20.0F).step(0.5F));
    private final PlacementRate breakRate = new PlacementRate();
    private LocalPlayer ratePlayer;
    private ClientLevel rateLevel;
    private final ModeSetting mode = this.addSetting("mode", new ModeSetting("Mode", "Single", "Single", "Double"));
    private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 0.0F, 0.0F, 100.0F).step(1.0F));
    private final NumberSetting placeDelay = this.addSetting("place-delay", new NumberSetting("Place Delay (ms)", 0.0F, 0.0F, 5000.0F).step(50.0F));
    private final NumberSetting chargeDelay = this.addSetting("charge-delay", new NumberSetting("Charge Delay (ms)", 0.0F, 0.0F, 5000.0F).step(50.0F));
    private final NumberSetting detonateDelay = this.addSetting("detonate-delay", new NumberSetting("Detonate Delay (ms)", 0.0F, 0.0F, 5000.0F).step(50.0F));
    private final NumberSetting guardDelay = this.addSetting("guard-delay", new NumberSetting("Guard Delay (ms)", 0.0F, 0.0F, 5000.0F).step(50.0F));
    private final NumberSetting replaceDelay = this.addSetting("replace-delay", new NumberSetting("Replace Delay (ms)", 0.0F, 0.0F, 5000.0F).step(50.0F));
    private final NumberSetting repeatDelay = this.addSetting("repeat-delay", new NumberSetting("Repeat Delay (ms)", 0.0F, 0.0F, 5000.0F).step(50.0F));
    private final NumberSetting switchBackDelay = this.addSetting("switch-back-delay", new NumberSetting("Switch Back Delay (ms)", 0.0F, 0.0F, 5000.0F).step(50.0F));
    private final BooleanSetting blastGuard = this.addSetting("safe-anchor", new BooleanSetting("Safe Anchor", true));
    private final ModeSetting withoutGuard = this.addSetting("without-guard", new ModeSetting("If Unguarded", "Detonate", "Detonate", "Cancel"));
    private final BooleanSetting switchBack = this.addSetting("switch-back", new BooleanSetting("Switch Back", false));
    private final BooleanSetting protectLoot = this.addSetting("protect-loot", new BooleanSetting("Protect Loot", true));
    private SingleAnchorMacroModule.Step step = SingleAnchorMacroModule.Step.IDLE;
    private int tick;
    private int readyAt;
    private int deadline;
    private int repeatReadyAt;
    private int previousSlot = -1;
    private int ownSlot = -1;
    private boolean refilled;
    private BlockPos anchorPos;
    private Vec3 sightline;
    private BlockHitResult replaceHit;
    private ClientLevel activeLevel;
    private AnchorGuard.Spot guard;
    private Block guardBlock;
    private BlockState guardReported;
    private int guardTries;
    private boolean rapidCycle;
    private boolean cycleDetonated;
    private boolean suppressingUse;
    private BlockPos detonatedPos;
    private ClientLevel detonatedLevel;
    private int detonationUntil;

    public SingleAnchorMacroModule() {
        super("anchor-macro", "Anchor Macro", "Places, charges and detonates anchors while you hold use, with separate placement and break speeds.", ModuleCategory.COMBAT);
        this.withoutGuard.visibleWhen(this.blastGuard::get);
        this.guardDelay.visibleWhen(this.blastGuard::get);
        this.replaceDelay.visibleWhen(() -> this.mode.is(DOUBLE));
        this.switchBackDelay.visibleWhen(this.switchBack::get);
    }

    @Override
    protected void onEnable() {
        this.resetPlacementRate();
        this.clearDetonation();
        this.stop();
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (this.step != SingleAnchorMacroModule.Step.IDLE && mc.player != null && !(mc.screen instanceof AbstractContainerScreen)) {
            Inventory inventory = mc.player.getInventory();
            if (this.switchBackDue(inventory)) {
                inventory.setSelectedSlot(this.previousSlot);
            }
        }

        this.stop();
        this.clearDetonation();
        this.resetPlacementRate();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        this.tick++;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != this.ratePlayer || mc.level != this.rateLevel) {
            this.placementRate.reset();
            this.breakRate.reset();
            this.repeatReadyAt = 0;
            this.ratePlayer = player;
            this.rateLevel = mc.level;
        }
        if (!mc.isWindowActive()) {
            this.stop();
            return;
        }
        if (player != null && mc.level != null && mc.gameMode != null && !player.isDeadOrDying()) {
            LootLatch.update(mc, player);
            LootGuard.update(mc.level, player);
            Inventory inventory = player.getInventory();
            if (this.step == SingleAnchorMacroModule.Step.IDLE && this.suppressingUse) {
                this.stop();
            }
            if (mc.screen != null) {
                if (this.step != SingleAnchorMacroModule.Step.IDLE && this.step != SingleAnchorMacroModule.Step.SWITCH_BACK) {
                    this.wrapUp(inventory);
                }
            } else if (!AutoEatModule.isEating() && !AutoInvTotemModule.wantsHotbar()) {
                if (this.step != SingleAnchorMacroModule.Step.IDLE && (this.hotbarMovedByPlayer(inventory) || mc.level != this.activeLevel)) {
                    this.stop();
                }

                if ((LootLatch.latched() || LootLatch.killedNow())
                    && this.step != SingleAnchorMacroModule.Step.IDLE
                    && this.step != SingleAnchorMacroModule.Step.SWITCH_BACK) {
                    this.wrapUp(inventory);
                }

                if (LootLatch.latched() && this.step == SingleAnchorMacroModule.Step.IDLE) {
                    if (!player.isUsingItem() && anchorInPlay(mc, player)) {
                        suppressUse(mc);
                    }
                } else if (this.step == SingleAnchorMacroModule.Step.IDLE
                        && (this.waitingForDetonation(mc) || this.waitingForRepeat(mc))) {
                    suppressUse(mc);
                } else if (this.step != SingleAnchorMacroModule.Step.IDLE || this.tryStart(mc, player)) {
                    swallowClicks(mc);
                    if (this.step == SingleAnchorMacroModule.Step.SWITCH_BACK) {
                        if (this.tick >= this.readyAt && !MeleeAttacks.actedThisTick(player)) {
                            if (this.switchBackDue(inventory)) {
                                inventory.setSelectedSlot(this.previousSlot);
                            }

                            this.stop();
                        }
                    } else {
                        for (int i = 0;
                            i < MAX_STEPS_PER_TICK
                                && this.tick >= this.readyAt
                                && this.step != SingleAnchorMacroModule.Step.IDLE
                                && this.step != SingleAnchorMacroModule.Step.SWITCH_BACK;
                            i++
                        ) {
                            if (this.tick >= this.deadline) {
                                this.expire(inventory);
                            } else {
                                if (MeleeAttacks.actedThisTick(player) || !MeleeAttacks.handsFree(mc, player)) {
                                    break;
                                }

                                this.runStep(mc, player);
                            }
                        }
                    }
                }
            } else {
                this.stop();
            }
        } else {
            this.stop();
            this.clearDetonation();
        }
    }

    @EventHandler
    public void onPacketApplied(PacketAppliedEvent event) {
        if (this.step == SingleAnchorMacroModule.Step.AWAIT_GUARD && this.guard != null) {
            BlockPos spot = this.guard.pos();
            if (event.packet instanceof ClientboundBlockUpdatePacket update) {
                if (update.getPos().equals(spot)) {
                    this.guardReported = update.getBlockState();
                }
            } else if (event.packet instanceof ClientboundSectionBlocksUpdatePacket section) {
                section.runUpdates((pos, state) -> {
                    if (pos.equals(spot)) {
                        this.guardReported = state;
                    }
                });
            }
        }
    }

    private boolean tryStart(Minecraft mc, LocalPlayer player) {
        boolean held = Crystals.isPhysicallyDown(mc, mc.options.keyUse);
        if ((held || MeleeAttacks.rightClickComing(mc)) && mc.isWindowActive()
                && MeleeAttacks.canAct(mc) && !CombatMacroLock.heldByOther(this)) {
            if (!player.isSpectator()
                && canClick(player)
                && !MeleeAttacks.actedThisTick(player)
                && MeleeAttacks.handsFree(mc, player)
                && !player.getOffhandItem().is(Items.GLOWSTONE)) {
                if (player.pick(player.blockInteractionRange(), 1.0F, false) instanceof BlockHitResult hit && hit.getType() == Type.BLOCK) {
                    ClientLevel level = mc.level;
                    BlockState clicked = level.getBlockState(hit.getBlockPos());
                    boolean onAnchor = clicked.is(Blocks.RESPAWN_ANCHOR);
                    BlockPos target;
                    if (onAnchor) {
                        target = hit.getBlockPos().immutable();
                    } else {
                        if (!player.getMainHandItem().is(Items.RESPAWN_ANCHOR) || ServerRotation.pending()) {
                            return false;
                        }
                        if (!this.placementRate.ready(player.tickCount, this.placeCps.get())) {
                            this.suppressUse(mc);
                            return false;
                        }

                        target = Crystals.placedObsidian(level, player, hit, clicked);
                    }

                    if (target == null || level.dimension() == Level.NETHER) {
                        return false;
                    }

                    if (this.protectLoot.get() && LootGuard.near(Vec3.atCenterOf(target))) {
                        return false;
                    }

                    Inventory inventory = player.getInventory();
                    boolean charged = chargeOf(clicked) > 0;
                    if (!charged && findHotbar(inventory, Items.GLOWSTONE) == -1) {
                        return false;
                    }

                    if (AnchorGuard.detonatorSlot(inventory) == -1) {
                        return false;
                    }

                    if (this.blastGuard.get() && this.withoutGuard.is("Cancel") && guardSlot(inventory, this.glowstoneNeeded(charged)) == -1) {
                        return false;
                    }

                    if (!CombatMacroLock.tryAcquire(this)) {
                        return false;
                    }

                    this.previousSlot = inventory.getSelectedSlot();
                    this.rapidCycle = held;
                    this.cycleDetonated = false;
                    this.activeLevel = level;
                    this.anchorPos = target;
                    this.sightline = hit.getLocation();
                    if (onAnchor) {
                        this.scheduleNow(this.afterPlacement());
                    } else {
                        this.scheduleNow(SingleAnchorMacroModule.Step.PLACE);
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
    }

    private void swallowClicks(Minecraft mc) {
        MeleeAttacks.takeAttackClicks(mc);
        suppressUse(mc);
    }

    private static boolean anchorInPlay(Minecraft mc, LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        return !main.is(Items.RESPAWN_ANCHOR) && !main.is(Items.GLOWSTONE)
            ? mc.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK && mc.level.getBlockState(hit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)
            : true;
    }

    private void suppressUse(Minecraft mc) {
        this.suppressingUse = true;
        KeyMapping use = mc.options.keyUse;
        use.setDown(false);

        while (use.consumeClick()) {
        }
    }

    private void runStep(Minecraft mc, LocalPlayer player) {
        switch (this.step) {
            case PLACE:
                this.place(mc, player);
                break;
            case GUARD:
                this.placeGuard(mc, player);
                break;
            case AWAIT_GUARD:
                this.awaitGuard(mc, player);
                break;
            case CHARGE:
                this.charge(mc, player);
                break;
            case DETONATE:
                this.detonate(mc, player);
                break;
            case REPLACE:
                this.replace(mc, player);
        }
    }

    private SingleAnchorMacroModule.Step afterPlacement() {
        return this.blastGuard.get() ? SingleAnchorMacroModule.Step.GUARD : SingleAnchorMacroModule.Step.CHARGE;
    }

    private void place(Minecraft mc, LocalPlayer player) {
        Inventory inventory = player.getInventory();
        if (!player.getMainHandItem().is(Items.RESPAWN_ANCHOR) || !canClick(player)
                || ServerRotation.pending() || ServerRotation.desynced()) {
            this.wrapUp(inventory);
            return;
        }
        if (!this.placementRate.ready(player.tickCount, this.placeCps.get())) {
            this.readyAt = this.tick + 1;
            this.deadline = this.readyAt + this.settleWindow();
            return;
        }
        if (!(player.pick(player.blockInteractionRange(), 1.0F, false) instanceof BlockHitResult hit)
                || hit.getType() != Type.BLOCK) {
            this.wrapUp(inventory);
            return;
        }
        BlockPos target = Crystals.placedObsidian(mc.level, player, hit, mc.level.getBlockState(hit.getBlockPos()));
        if (!this.anchorPos.equals(target)
                || this.protectLoot.get() && LootGuard.near(Vec3.atCenterOf(target))) {
            this.wrapUp(inventory);
            return;
        }
        if (!AnchorGuard.click(mc, player, hit)) {
            this.readyAt = this.tick + 1;
            return;
        }
        this.sightline = hit.getLocation();
        this.placementRate.placed(player.tickCount, this.placeCps.get());
        this.schedule(this.afterPlacement());
    }

    private void expire(Inventory inventory) {
        if (this.step != SingleAnchorMacroModule.Step.GUARD && this.step != SingleAnchorMacroModule.Step.AWAIT_GUARD) {
            this.wrapUp(inventory);
        } else {
            this.skipGuard(inventory);
        }
    }

    private void placeGuard(Minecraft mc, LocalPlayer player) {
        Inventory inventory = player.getInventory();
        BlockState anchor = mc.level.getBlockState(this.anchorPos);
        if (!anchor.is(Blocks.RESPAWN_ANCHOR)) {
            this.wrapUp(inventory);
        } else {
            AnchorGuard.Spot spot = AnchorGuard.find(mc.level, player, this.anchorPos, this.sightline);
            if (spot != null && spot.standing()) {
                this.guarded();
            } else {
                int slot = guardSlot(inventory, this.glowstoneNeeded(chargeOf(anchor) > 0));
                Rotation sent = ServerRotation.sent();
                BlockHitResult hit = spot != null && sent != null ? AnchorGuard.hitAlong(mc.level, player, sent, spot) : null;
                if (spot != null && slot != -1 && (hit != null || ServerRotation.canRotate(player))) {
                    if (hit == null) {
                        ServerRotation.request(RotationUtils.fromTo(player.getEyePosition(), spot.aim()));
                        MeleeAttacks.claimTick(mc);
                        this.readyAt = this.tick + 1;
                    } else {
                        this.guard = spot;
                        this.guardBlock = Block.byItem(inventory.getItem(slot).getItem());
                        this.guardReported = null;
                        this.select(inventory, slot);
                        if (!AnchorGuard.click(mc, player, hit)) {
                            this.readyAt = this.tick + 1;
                            return;
                        }
                        ServerRotation.request(RotationUtils.fromTo(player.getEyePosition(), Vec3.atCenterOf(this.anchorPos)));
                        this.guardTries++;
                        if (this.withoutGuard.is("Cancel")) {
                            this.schedule(SingleAnchorMacroModule.Step.AWAIT_GUARD);
                            this.deadline = this.readyAt + Crystals.confirmWindow(mc, player);
                        } else {
                            this.schedule(SingleAnchorMacroModule.Step.CHARGE);
                        }
                    }
                } else {
                    this.skipGuard(inventory);
                }
            }
        }
    }

    private void awaitGuard(Minecraft mc, LocalPlayer player) {
        boolean shown = mc.level.getBlockState(this.guard.pos()).is(this.guardBlock);
        if (shown && this.guardReported != null && this.guardReported.is(this.guardBlock)) {
            this.guarded();
        } else if (shown) {
            this.readyAt = this.tick + 1;
        } else if (this.guardTries < 2) {
            this.scheduleNow(SingleAnchorMacroModule.Step.GUARD);
        } else {
            this.skipGuard(player.getInventory());
        }
    }

    private void guarded() {
        this.guard = null;
        this.scheduleNow(SingleAnchorMacroModule.Step.CHARGE);
    }

    private void skipGuard(Inventory inventory) {
        this.guard = null;
        if (this.withoutGuard.is("Cancel")) {
            this.wrapUp(inventory);
        } else {
            this.scheduleNow(SingleAnchorMacroModule.Step.CHARGE);
        }
    }

    private void charge(Minecraft mc, LocalPlayer player) {
        Inventory inventory = player.getInventory();
        BlockState state = mc.level.getBlockState(this.anchorPos);
        if (!state.is(Blocks.RESPAWN_ANCHOR)) {
            this.wrapUp(inventory);
        } else if (chargeOf(state) > 0 && !this.refilled) {
            this.scheduleNow(SingleAnchorMacroModule.Step.DETONATE);
        } else {
            int glowstone = findHotbar(inventory, Items.GLOWSTONE);
            if (glowstone != -1 && canClick(player)) {
                BlockHitResult hit = AnchorGuard.anchorHit(mc.level, player, this.anchorPos);
                if (hit == null) {
                    this.readyAt = this.tick + 1;
                } else {
                    this.select(inventory, glowstone);
                    if (AnchorGuard.click(mc, player, hit)) {
                        this.schedule(SingleAnchorMacroModule.Step.DETONATE);
                    } else {
                        this.readyAt = this.tick + 1;
                    }
                }
            } else {
                this.wrapUp(inventory);
            }
        }
    }

    private void detonate(Minecraft mc, LocalPlayer player) {
        Inventory inventory = player.getInventory();
        if (chargeOf(mc.level.getBlockState(this.anchorPos)) != 0 && canClick(player) && !player.getOffhandItem().is(Items.GLOWSTONE)) {
            if (!this.breakRate.ready(player.tickCount, this.breakCps.get())) {
                this.readyAt = this.tick + 1;
                this.deadline = this.readyAt + this.settleWindow();
                return;
            }
            BlockHitResult hit = AnchorGuard.anchorHit(mc.level, player, this.anchorPos);
            if (hit == null) {
                this.readyAt = this.tick + 1;
            } else {
                int refillSlot = this.mode.is("Double")
                        && !this.refilled
                        && hit.getDirection() == Direction.UP
                        && this.anchorPos.getY() >= player.getBlockY()
                        && findHotbar(inventory, Items.GLOWSTONE) != -1
                    ? findHotbar(inventory, Items.RESPAWN_ANCHOR)
                    : -1;
                int slot = refillSlot != -1 ? refillSlot : AnchorGuard.detonatorSlot(inventory);
                if (slot == -1) {
                    this.wrapUp(inventory);
                } else if (this.lethal(player, inventory.getItem(slot))) {
                    this.wrapUp(inventory);
                } else {
                    this.select(inventory, slot);
                    if (!AnchorGuard.click(mc, player, hit)) {
                        this.readyAt = this.tick + 1;
                        return;
                    }
                    this.breakRate.placed(player.tickCount, this.breakCps.get());
                    this.cycleDetonated = true;
                    this.detonatedPos = this.anchorPos;
                    this.detonatedLevel = mc.level;
                    this.detonationUntil = this.tick + Math.max(20, Crystals.confirmWindow(mc, player) * 2);
                    if (slot == refillSlot) {
                        this.refilled = true;
                        this.replaceHit = hit;
                        this.schedule(SingleAnchorMacroModule.Step.REPLACE);
                    } else {
                        this.wrapUp(inventory);
                    }
                }
            }
        } else {
            this.wrapUp(inventory);
        }
    }

    private void replace(Minecraft mc, LocalPlayer player) {
        Inventory inventory = player.getInventory();
        if (mc.level.getBlockState(this.anchorPos).is(Blocks.RESPAWN_ANCHOR)) {
            this.readyAt = this.tick + 1;
            return;
        }
        if (!this.placementRate.ready(player.tickCount, this.placeCps.get())) {
            this.readyAt = this.tick + 1;
            this.deadline = this.readyAt + this.settleWindow();
            return;
        }
        Rotation sent = ServerRotation.sent();
        Vec3 eye = player.getEyePosition();
        boolean stillAimed = sent != null
            && new AABB(this.anchorPos)
                .clip(eye, eye.add(Vec3.directionFromRotation(sent.pitch(), sent.yaw()).scale(player.blockInteractionRange())))
                .isPresent();
        if (this.replaceHit != null && stillAimed && player.getMainHandItem().is(Items.RESPAWN_ANCHOR) && canClick(player)) {
            if (!AnchorGuard.click(mc, player, this.replaceHit)) {
                this.readyAt = this.tick + 1;
                return;
            }
            this.placementRate.placed(player.tickCount, this.placeCps.get());
            this.clearDetonation();
            this.replaceHit = null;
            this.schedule(SingleAnchorMacroModule.Step.CHARGE);
        } else {
            this.wrapUp(inventory);
        }
    }

    private boolean lethal(LocalPlayer player, ItemStack held) {
        if (!held.is(Items.TOTEM_OF_UNDYING) && !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            float damage = CrystalDamage.estimate(Vec3.atCenterOf(this.anchorPos), player, 5.0F, this.anchorPos);
            return CrystalDamage.endangers(player, damage);
        } else {
            return false;
        }
    }

    private void wrapUp(Inventory inventory) {
        Minecraft mc = Minecraft.getInstance();
        if (this.cycleDetonated) {
            this.repeatReadyAt = this.tick + millisToTicks(this.repeatDelay.getInt());
        }
        if (this.cycleDetonated && this.rapidCycle && mc.screen == null
                && mc.level == this.activeLevel && mc.isWindowActive()
                && Crystals.isPhysicallyDown(mc, mc.options.keyUse)) {
            int anchorSlot = findHotbar(inventory, Items.RESPAWN_ANCHOR);
            if (anchorSlot != -1) {
                inventory.setSelectedSlot(anchorSlot);
                this.stop();
                return;
            }
        }
        if (this.switchBackDue(inventory)) {
            this.scheduleNow(SingleAnchorMacroModule.Step.SWITCH_BACK);
        } else {
            this.stop();
        }
    }

    private boolean switchBackDue(Inventory inventory) {
        return this.switchBack.get()
            && Inventory.isHotbarSlot(this.previousSlot)
            && this.ownSlot != -1
            && this.ownSlot != this.previousSlot
            && inventory.getSelectedSlot() == this.ownSlot;
    }

    private boolean hotbarMovedByPlayer(Inventory inventory) {
        return inventory.getSelectedSlot() != (this.ownSlot != -1 ? this.ownSlot : this.previousSlot);
    }

    private boolean waitingForRepeat(Minecraft mc) {
        return this.isEnabled() && this.tick < this.repeatReadyAt
            && mc.player != null && mc.level != null
            && !mc.player.isUsingItem() && anchorInPlay(mc, mc.player);
    }

    private void stop() {
        if (this.suppressingUse) {
            Minecraft mc = Minecraft.getInstance();
            mc.options.keyUse.setDown(mc.screen == null && mc.isWindowActive()
                && !this.waitingForRepeat(mc)
                && Crystals.isPhysicallyDown(mc, mc.options.keyUse));
            this.suppressingUse = false;
        }
        this.step = SingleAnchorMacroModule.Step.IDLE;
        this.anchorPos = null;
        this.sightline = null;
        this.replaceHit = null;
        this.activeLevel = null;
        this.previousSlot = -1;
        this.ownSlot = -1;
        this.refilled = false;
        this.guard = null;
        this.guardBlock = null;
        this.guardReported = null;
        this.guardTries = 0;
        this.rapidCycle = false;
        this.cycleDetonated = false;
        CombatMacroLock.release(this);
    }

    private boolean waitingForDetonation(Minecraft mc) {
        if (this.detonatedPos != null && this.detonatedLevel == mc.level
                && this.tick <= this.detonationUntil
                && mc.level.getBlockState(this.detonatedPos).is(Blocks.RESPAWN_ANCHOR)) {
            return true;
        }
        this.clearDetonation();
        return false;
    }

    private void clearDetonation() {
        this.detonatedPos = null;
        this.detonatedLevel = null;
        this.detonationUntil = 0;
    }

    private void resetPlacementRate() {
        this.placementRate.reset();
        this.breakRate.reset();
        this.repeatReadyAt = 0;
        this.ratePlayer = null;
        this.rateLevel = null;
    }

    private void schedule(SingleAnchorMacroModule.Step next) {
        this.schedule(next, this.rapidCycle ? 0 : 1);
    }

    private void scheduleNow(SingleAnchorMacroModule.Step next) {
        this.schedule(next, 0);
    }

    private void schedule(SingleAnchorMacroModule.Step next, int baseTicks) {
        this.step = next;
        int stageMillis = switch (next) {
            case PLACE -> this.placeDelay.getInt();
            case GUARD -> this.guardDelay.getInt();
            case CHARGE -> this.chargeDelay.getInt();
            case DETONATE -> this.detonateDelay.getInt();
            case REPLACE -> this.placeDelay.getInt() + this.replaceDelay.getInt();
            case SWITCH_BACK -> this.switchBackDelay.getInt();
            default -> 0;
        };
        this.readyAt = this.tick + baseTicks + this.delay.getInt() + millisToTicks(stageMillis);
        this.deadline = this.readyAt + this.settleWindow();
    }

    private static int millisToTicks(int millis) {
        return (millis + 49) / 50;
    }

    private int settleWindow() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? SETTLE_TICKS : Math.max(SETTLE_TICKS, Crystals.confirmWindow(mc, mc.player));
    }

    private void select(Inventory inventory, int slot) {
        if (inventory.getSelectedSlot() != slot) {
            inventory.setSelectedSlot(slot);
            this.ownSlot = slot;
        }
    }

    private static boolean canClick(LocalPlayer player) {
        return !player.isSecondaryUseActive();
    }

    private static int chargeOf(BlockState state) {
        return state.is(Blocks.RESPAWN_ANCHOR) ? state.getValue(RespawnAnchorBlock.CHARGE) : 0;
    }

    private int glowstoneNeeded(boolean charged) {
        int needed = charged ? 0 : 1;
        return this.mode.is("Double") ? needed + 1 : needed;
    }

    private static int guardSlot(Inventory inventory, int glowstoneNeeded) {
        int weaker = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(Items.GLOWSTONE) && AnchorGuard.isGuardBlock(stack)) {
                if (Block.byItem(stack.getItem()).getExplosionResistance() >= 600.0F) {
                    return i;
                }

                if (weaker == -1) {
                    weaker = i;
                }
            }
        }

        if (weaker != -1) {
            return weaker;
        }

        int glowstone = findHotbar(inventory, Items.GLOWSTONE);
        return glowstone != -1 && countHotbar(inventory, Items.GLOWSTONE) > glowstoneNeeded ? glowstone : -1;
    }

    private static int findHotbar(Inventory inventory, Item item) {
        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i).is(item)) {
                return i;
            }
        }

        return -1;
    }

    private static int countHotbar(Inventory inventory, Item item) {
        int count = 0;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    @Environment(EnvType.CLIENT)
    private enum Step {
        IDLE,
        PLACE,
        GUARD,
        AWAIT_GUARD,
        CHARGE,
        DETONATE,
        REPLACE,
        SWITCH_BACK;
    }
}
