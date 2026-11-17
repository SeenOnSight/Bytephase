package dev.bytephase.pvp.module.impl.combat;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.impl.PlayerAttackEntityEvent;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.CombatMacroLock;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.impl.combat.crystal.CrystalDamage;
import dev.bytephase.pvp.module.impl.combat.crystal.Crystals;
import dev.bytephase.pvp.module.impl.combat.crystal.LootLatch;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.impl.misc.AutoEatModule;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import dev.bytephase.pvp.module.setting.NumberSetting;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

@Environment(EnvType.CLIENT)
public final class CrystalMacroModule extends ClientModule {
    private static final int COUNTER_CAP = 1000;
    private static final int WEAPON_SETTLE_TICKS = 2;
    private static final int MAX_BREAK_ATTEMPTS = 3;
    private static final int GHOST_REST_TICKS = 100;
    private final NumberSetting breakDelay = this.addSetting("break-delay", new NumberSetting("Break Delay", 0.0F, 0.0F, 10.0F).step(1.0F));
    private final NumberSetting placeDelay = this.addSetting("place-delay", new NumberSetting("Place Delay", 0.0F, 0.0F, 10.0F).step(1.0F));
    private final BooleanSetting autoObsidian = this.addSetting("auto-obsidian", new BooleanSetting("Auto Obsidian", true));
    private final NumberSetting obsidianDelay = this.addSetting("obsidian-delay", new NumberSetting("Obsidian Delay", 2.0F, 0.0F, 10.0F).step(1.0F));
    private final BooleanSetting switchBack = this.addSetting("switch-back", new BooleanSetting("Switch Back", true));
    private final NumberSetting maxSelfDamage = this.addSetting("max-self-damage", new NumberSetting("Max Self Damage", 8.0F, 0.0F, 20.0F).step(0.5F));
    private final BooleanSetting humanize = this.addSetting("humanize", new BooleanSetting("Human Pace", true));
    private boolean engaged;
    private int savedSlot = -1;
    private int ownSlot = -1;
    private int ticksSinceAction = 1000;
    private int actionGap;
    private final Int2IntOpenHashMap breakAttempts = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap restUntil = new Int2IntOpenHashMap();
    private ItemStack lastMain;
    private int mainAge = 1000;
    private int pendingBreakId = -1;
    private int pendingBreakAge = 1000;
    private BlockPos pendingCrystalBase;
    private int pendingCrystalAge = 1000;
    private BlockPos pendingObsidian;
    private int pendingObsidianAge = 1000;

    public CrystalMacroModule() {
        super("crystal-macro", "Crystal Macro", "Breaks and places crystals while you hold right-click.", ModuleCategory.COMBAT);
        this.obsidianDelay.visibleWhen(this.autoObsidian::get);
    }

    @Override
    public int settingsVersion() {
        return 1;
    }

    @Override
    public void migrateSettings(int savedVersion) {
        if (savedVersion < 1) {
            this.breakDelay.set(this.breakDelay.get() - 1.0F);
            this.placeDelay.set(this.placeDelay.get() - 1.0F);
            this.obsidianDelay.set(this.obsidianDelay.get() - 1.0F);
        }
    }

    @Override
    protected void onEnable() {
        this.resetState();
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        this.endHold(mc);
        LocalPlayer player = mc.player;
        if (player != null && !hotbarTakenByOther()) {
            this.restoreSlot(player);
        }

        this.resetState();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        this.ageCounters();
        LocalPlayer player = mc.player;
        if (player != null && mc.level != null && mc.gameMode != null && !player.isDeadOrDying()) {
            ItemStack main = player.getMainHandItem();
            if (main != this.lastMain) {
                this.lastMain = main;
                this.mainAge = 0;
            }

            this.forgetGone(mc.level);
            LootLatch.update(mc, player);
            if (LootLatch.latched()) {
                this.endHold(mc);
                if (!player.isUsingItem() && !AutoEatModule.isEating() && (this.ownsSelection(player) || activationAllows(player))) {
                    suppressVanillaUse(mc);
                }
            } else if (!this.holdActive(mc, player)) {
                this.endHold(mc);
                if (mc.screen == null && !hotbarTakenByOther() && !MeleeAttacks.actedThisTick(player)) {
                    this.restoreSlot(player);
                }
            } else if (!AutoInvTotemModule.wantsHotbar() && !CombatMacroLock.heldByOther(this)) {
                if (this.engaged) {
                    if (!this.keepEngaged(player)) {
                        this.endHold(mc);
                        return;
                    }
                } else if (player.isUsingItem() || !this.ownsSelection(player) && !activationAllows(player)) {
                    return;
                }

                CrystalMacroModule.Plan plan = this.plan(mc, player);
                if (this.engaged || plan != null || this.ownsSelection(player)) {
                    this.engaged = true;
                    CombatMacroLock.tryAcquire(this);
                    suppressVanillaUse(mc);
                    if (plan != null
                        && MeleeAttacks.handsFree(mc, player)
                        && !MeleeAttacks.actedThisTick(player)
                        && !ServerRotation.pending()
                        && !ServerRotation.desynced()) {
                        switch (plan.step()) {
                            case BREAK:
                                this.breakCrystal(mc, player, plan);
                                break;
                            case CRYSTAL:
                                if (this.useOnCrosshair(mc, player, this.placeDelay, plan)) {
                                    this.pendingCrystalBase = plan.target();
                                    this.pendingCrystalAge = 0;
                                }
                                break;
                            case OBSIDIAN:
                                if (this.useOnCrosshair(mc, player, this.obsidianDelay, plan)) {
                                    this.pendingObsidian = plan.target();
                                    this.pendingObsidianAge = 0;
                                }
                        }
                    }
                }
            } else {
                CombatMacroLock.release(this);
                if (this.engaged) {
                    suppressVanillaUse(mc);
                }
            }
        } else {
            this.endHold(mc);
            this.forgetSlots();
        }
    }

    @EventHandler
    public void onAttack(PlayerAttackEntityEvent event) {
        if (event.target instanceof EndCrystal crystal && crystal.level().isClientSide()) {
            this.markBreak(crystal);
        }
    }

    private boolean holdActive(Minecraft mc, LocalPlayer player) {
        return MeleeAttacks.canAct(mc) && mc.isWindowActive() && !player.isSpectator() && Crystals.isPhysicallyDown(mc, mc.options.keyUse);
    }

    private static boolean hotbarTakenByOther() {
        return AutoEatModule.isEating() || AutoInvTotemModule.wantsHotbar();
    }

    private boolean keepEngaged(LocalPlayer player) {
        if (this.ownsSelection(player)) {
            return true;
        }

        this.forgetSlots();
        return activationAllows(player);
    }

    private boolean ownsSelection(LocalPlayer player) {
        return this.ownSlot != -1 && player.getInventory().getSelectedSlot() == this.ownSlot;
    }

    private static boolean activationAllows(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        ItemStack offhand = player.getOffhandItem();
        return !main.is(Items.END_CRYSTAL) && (!offhand.is(Items.END_CRYSTAL) || !Crystals.isCombatItem(main))
            ? (main.is(ItemTags.SWORDS) || main.is(ItemTags.AXES) || main.is(Items.MACE))
                && (offhand.isEmpty() || offhand.is(Items.TOTEM_OF_UNDYING))
                && Crystals.hotbarSlotOf(player.getInventory(), Items.END_CRYSTAL) != -1
            : true;
    }

    private void endHold(Minecraft mc) {
        CombatMacroLock.release(this);
        if (this.engaged) {
            this.engaged = false;
            mc.options.keyUse.setDown(mc.screen == null && Crystals.isPhysicallyDown(mc, mc.options.keyUse));
        }
    }

    private void restoreSlot(LocalPlayer player) {
        if (this.ownSlot != -1) {
            Inventory inventory = player.getInventory();
            if (this.switchBack.get()
                && Inventory.isHotbarSlot(this.savedSlot)
                && this.savedSlot != this.ownSlot
                && inventory.getSelectedSlot() == this.ownSlot) {
                inventory.setSelectedSlot(this.savedSlot);
            }

            this.forgetSlots();
        }
    }

    private void forgetSlots() {
        this.savedSlot = -1;
        this.ownSlot = -1;
    }

    private void resetState() {
        this.engaged = false;
        this.forgetSlots();
        this.ticksSinceAction = 1000;
        this.actionGap = 0;
        this.breakAttempts.clear();
        this.restUntil.clear();
        this.lastMain = null;
        this.mainAge = 1000;
        this.pendingBreakId = -1;
        this.pendingBreakAge = 1000;
        this.pendingCrystalBase = null;
        this.pendingCrystalAge = 1000;
        this.pendingObsidian = null;
        this.pendingObsidianAge = 1000;
        CombatMacroLock.release(this);
    }

    private static void suppressVanillaUse(Minecraft mc) {
        KeyMapping use = mc.options.keyUse;
        use.setDown(false);

        while (use.consumeClick()) {
        }
    }

    private CrystalMacroModule.Plan plan(Minecraft mc, LocalPlayer player) {
        HitResult hit = dev.bytephase.pvp.marlow.MarlowCrystal.raycast(player);
        int window = Crystals.confirmWindow(mc, player);
        if (this.pendingCrystalBase != null && Crystals.hasCrystal(mc.level, this.pendingCrystalBase, null)) {
            this.pendingCrystalBase = null;
        }

        if (hit instanceof EntityHitResult entityHit) {
            if (!(entityHit.getEntity() instanceof EndCrystal crystal && crystal.isAlive())) {
                return null;
            } else if (crystal.getId() == this.pendingBreakId && this.pendingBreakAge < window) {
                return null;
            } else if (this.restUntil.get(crystal.getId()) > player.tickCount) {
                return null;
            } else {
                float selfDamage = CrystalDamage.estimate(crystal.position(), player);
                if (!(selfDamage > this.maxSelfDamage.get()) && !CrystalDamage.endangers(player, selfDamage)) {
                    int weapon = Crystals.weaponRoute(player);
                    return weapon == -1 ? null : CrystalMacroModule.Plan.forBreak(crystal, weapon);
                } else {
                    return null;
                }
            }
        } else if (hit instanceof BlockHitResult blockHit && blockHit.getType() == Type.BLOCK) {
            ClientLevel level = mc.level;
            BlockPos pos = blockHit.getBlockPos();
            if (!level.getWorldBorder().isWithinBounds(pos)) {
                return null;
            }

            BlockState state = level.getBlockState(pos);
            if (Crystals.isBase(state)) {
                if (pos.equals(this.pendingCrystalBase) && this.pendingCrystalAge < window) {
                    return null;
                }

                if (!Crystals.canHost(level, pos, null)) {
                    return null;
                }

                int route = Crystals.route(player, Items.END_CRYSTAL, state, this.savedSlot);
                return route == -1 ? null : CrystalMacroModule.Plan.forPlace(CrystalMacroModule.Step.CRYSTAL, blockHit, pos, route);
            } else {
                if (!this.autoObsidian.get()) {
                    return null;
                }

                if (Crystals.route(player, Items.END_CRYSTAL, Blocks.OBSIDIAN.defaultBlockState(), this.savedSlot) == -1) {
                    return null;
                }

                int route = Crystals.route(player, Items.OBSIDIAN, state, this.savedSlot);
                if (route == -1) {
                    return null;
                }

                BlockPos target = Crystals.obsidianTarget(level, player, blockHit, state);
                return target != null && (!target.equals(this.pendingObsidian) || this.pendingObsidianAge >= window)
                    ? CrystalMacroModule.Plan.forPlace(CrystalMacroModule.Step.OBSIDIAN, blockHit, target, route)
                    : null;
            }
        } else {
            return null;
        }
    }

    private void breakCrystal(Minecraft mc, LocalPlayer player, CrystalMacroModule.Plan plan) {
        if (this.due(this.breakDelay)) {
            if (plan.route() != -2) {
                this.select(player, plan.route());
            }

            if (this.mainAge >= 2 || Crystals.enchantsAddDamage(player.getMainHandItem()) || Crystals.dealsDamage(player, ItemStack.EMPTY)) {
                if (Crystals.strike(mc, player, plan.crystal())) {
                    MeleeAttacks.claimTick(mc);
                    this.markBreak(plan.crystal());
                    int id = plan.crystal().getId();
                    if (this.breakAttempts.addTo(id, 1) + 1 >= 3) {
                        this.breakAttempts.remove(id);
                        this.restUntil.put(id, player.tickCount + 100);
                    }

                    this.acted();
                }
            }
        }
    }

    private boolean useOnCrosshair(Minecraft mc, LocalPlayer player, NumberSetting delay, CrystalMacroModule.Plan plan) {
        if (!this.due(delay)) {
            return false;
        }

        if (plan.route() != -2) {
            this.select(player, plan.route());
        }

        if (!Crystals.rightClick(mc, player, plan.blockHit())) {
            return false;
        }

        MeleeAttacks.claimTick(mc);
        this.acted();
        return true;
    }

    private boolean due(NumberSetting delay) {
        return this.ticksSinceAction > Math.max(delay.getInt(), this.actionGap - 1);
    }

    private void acted() {
        this.ticksSinceAction = 0;
        this.actionGap = this.humanize.get() ? Crystals.humanGap() : 0;
    }

    private void forgetGone(ClientLevel level) {
        this.breakAttempts.keySet().removeIf(id -> level.getEntity(id) == null);
        this.restUntil.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    private void select(LocalPlayer player, int slot) {
        Inventory inventory = player.getInventory();
        if (this.savedSlot == -1) {
            this.savedSlot = inventory.getSelectedSlot();
        }

        inventory.setSelectedSlot(slot);
        this.ownSlot = slot;
        this.lastMain = player.getMainHandItem();
        this.mainAge = 0;
    }

    private void markBreak(EndCrystal crystal) {
        this.pendingBreakId = crystal.getId();
        this.pendingBreakAge = 0;
    }

    private void ageCounters() {
        this.ticksSinceAction = Math.min(this.ticksSinceAction + 1, 1000);
        this.mainAge = Math.min(this.mainAge + 1, 1000);
        this.pendingBreakAge = Math.min(this.pendingBreakAge + 1, 1000);
        this.pendingCrystalAge = Math.min(this.pendingCrystalAge + 1, 1000);
        this.pendingObsidianAge = Math.min(this.pendingObsidianAge + 1, 1000);
    }

    @Environment(EnvType.CLIENT)
    private record Plan(CrystalMacroModule.Step step, EndCrystal crystal, BlockHitResult blockHit, BlockPos target, int route) {
        static CrystalMacroModule.Plan forBreak(EndCrystal crystal, int weapon) {
            return new CrystalMacroModule.Plan(CrystalMacroModule.Step.BREAK, crystal, null, null, weapon);
        }

        static CrystalMacroModule.Plan forPlace(CrystalMacroModule.Step step, BlockHitResult blockHit, BlockPos target, int route) {
            return new CrystalMacroModule.Plan(step, null, blockHit, target, route);
        }
    }

    @Environment(EnvType.CLIENT)
    private enum Step {
        BREAK,
        CRYSTAL,
        OBSIDIAN;
    }
}
