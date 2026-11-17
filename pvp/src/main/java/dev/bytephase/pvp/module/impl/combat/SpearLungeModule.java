package dev.bytephase.pvp.module.impl.combat;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Optional;
import dev.bytephase.pvp.chat.ModuleChat;
import dev.bytephase.pvp.event.EventPriority;
import dev.bytephase.pvp.event.Stage;
import dev.bytephase.pvp.event.impl.KeyInputEvent;
import dev.bytephase.pvp.event.impl.MouseInputEvent;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.input.ModuleKeybinds;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.CombatMacroLock;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.impl.combat.melee.SlotReturn;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import dev.bytephase.pvp.module.setting.KeySetting;
import dev.bytephase.pvp.mixin.AccessorElytraFlyMultiPlayerGameMode;
import dev.bytephase.pvp.util.pvp.Rotation;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class SpearLungeModule extends ClientModule {
    private static final int BUFFER_TICKS = 10;
    private static final int MAX_WINDUP_TICKS = 200;
    private static final double POKE_RANGE = 4.4;
    private static final double POKE_MARGIN = 0.125;
    private final KeySetting key = this.addSetting("lunge-key", new KeySetting("Lunge Key", -1));
    private final BooleanSetting reachPoke = this.addSetting("reach-poke", new BooleanSetting("Reach Poke", false));
    private final BooleanSetting switchBack = this.addSetting("switch-back", new BooleanSetting("Switch Back", true));
    private final SlotReturn slots = new SlotReturn(this);
    private boolean pending;
    private int pendingTicks;
    private int preparedSlot = -1;
    private int preparedTick;
    private int returnSlot = -1;
    private boolean preparedLunge;
    private LocalPlayer owner;

    public SpearLungeModule() {
        super("spear-swap", "Spear Swap", "Swaps to your Lunge spear and lunges. Reach Poke stabs with a spear past sword reach.", ModuleCategory.COMBAT);
    }

    @Override
    protected void onEnable() {
        this.pending = false;
        this.preparedSlot = -1;
        this.returnSlot = -1;
        this.owner = Minecraft.getInstance().player;
        this.on(KeyInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onKey);
        this.on(MouseInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onMouse);
        this.on(TickEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onTick);
    }

    @Override
    protected void onDisable() {
        this.pending = false;
        this.preparedSlot = -1;
        this.returnSlot = -1;
        this.owner = null;
        this.slots.drop(Minecraft.getInstance(), this.switchBack.get());
    }

    private void onKey(KeyInputEvent event) {
        if (event.action != 0 && this.ownsActionKey(event.key)) {
            // A number-key action must not also queue vanilla's hotbar selection.
            event.setCancelled(true);
        }
        if (event.action == 1 && this.ownsActionKey(event.key)) {
            this.onPress(event.key);
        }
    }

    private void onMouse(MouseInputEvent event) {
        if (event.action != 0 && this.ownsActionKey(1000 + event.button)) {
            event.setCancelled(true);
        }
        if (event.action == 1 && this.ownsActionKey(1000 + event.button)) {
            this.onPress(1000 + event.button);
        }
    }

    private boolean ownsActionKey(int pressed) {
        Minecraft mc = Minecraft.getInstance();
        return ModuleKeybinds.isBindable(this.key.value()) && pressed == this.key.value()
            && mc.screen == null && mc.player != null && mc.level != null;
    }

    private void onPress(int pressed) {
        int bound = this.key.value();
        if (ModuleKeybinds.isBindable(bound) && pressed == bound) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null && mc.player != null && mc.level != null
                    && !this.pending && this.preparedSlot < 0 && !this.slots.holding()) {
                if (bestSpear(mc.player.getInventory(), true) < 0) {
                    ModuleChat.actionBar(this, ModuleChat.body().warn("No Lunge spear").text(" in your hotbar"));
                } else {
                    this.returnSlot = mc.player.getInventory().getSelectedSlot();
                    this.pending = true;
                    this.pendingTicks = 0;
                }
            }
        }
    }

    private void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != this.owner) {
            this.slots.drop(mc, false);
            this.owner = player;
            this.pending = false;
            this.preparedSlot = -1;
            this.returnSlot = -1;
        }

        if (this.preparedSlot < 0) this.slots.tick(mc, this.switchBack.get());
        if (player != null && mc.level != null && mc.gameMode != null) {
            if (this.preparedSlot >= 0) {
                this.finishPreparedStab(mc, player);
                return;
            }
            if (this.pending) {
                this.tryLunge(mc, player);
            }

            if (this.reachPoke.get() && MeleeAttacks.clickPending(mc)) {
                this.tryPoke(mc, player);
            }
        }
    }

    private void tryLunge(Minecraft mc, LocalPlayer player) {
        if (++this.pendingTicks > BUFFER_TICKS) {
            this.pending = false;
        } else if (this.canStab(mc, player) && !player.isInWater() && !player.isFallFlying() && !player.isPassenger()) {
            if (player.getFoodData().hasEnoughFood() || player.getAbilities().mayfly) {
                Inventory inventory = player.getInventory();
                int slot = bestSpear(inventory, true);
                if (slot < 0) {
                    this.pending = false;
                } else {
                    // Selecting the spear restarts its own attack charge. The
                    // pre-swap hand's cooldown cannot determine when it can jab.
                    if (this.slots.take(player, slot, this.returnSlot)) {
                        this.pending = false;
                        this.prepareStab(player, slot, true);
                    }
                }
            }
        }
    }

    private void tryPoke(Minecraft mc, LocalPlayer player) {
        if (this.canStab(mc, player) && !player.getMainHandItem().has(DataComponents.PIERCING_WEAPON)) {
            if (MeleeAttacks.crosshairHit(mc) == null) {
                Inventory inventory = player.getInventory();
                int slot = bestSpear(inventory, false);
                if (slot >= 0) {
                    if (enemyPastReach(player) && this.slots.take(player, slot)) {
                        this.prepareStab(player, slot, false);
                    }
                }
            }
        }
    }

    private void prepareStab(LocalPlayer player, int slot, boolean lunge) {
        this.preparedSlot = slot;
        this.preparedTick = player.tickCount;
        this.preparedLunge = lunge;
        Minecraft mc = Minecraft.getInstance();
        ((AccessorElytraFlyMultiPlayerGameMode)mc.gameMode).pvp$elytraFlySyncSelected();
    }

    private void finishPreparedStab(Minecraft mc, LocalPlayer player) {
        if (mc.screen != null || player.getInventory().getSelectedSlot() != this.preparedSlot) {
            this.cancelPreparedStab(mc);
            return;
        }
        // Let vanilla tick the newly equipped spear before applying its attack
        // attributes and Lunge enchantment. Never stab with the pre-swap hand.
        if (player.tickCount <= this.preparedTick) return;
        ItemStack spear = player.getMainHandItem();
        PiercingWeapon weapon = spear.get(DataComponents.PIERCING_WEAPON);
        if (weapon == null || spear.isEmpty() || spear.nextDamageWillBreak()
                || this.preparedLunge && lungeLevel(spear) <= 0) {
            this.cancelPreparedStab(mc);
            return;
        }
        float delay = player.getCurrentItemAttackStrengthDelay();
        float minimumCharge = spear.getOrDefault(DataComponents.MINIMUM_ATTACK_CHARGE, 0.0F);
        int windupLimit = Float.isFinite(delay) && Float.isFinite(minimumCharge)
            ? (int)Math.min(MAX_WINDUP_TICKS,
                Math.max(BUFFER_TICKS, Math.ceil(delay * Math.max(0.0F, minimumCharge)) + BUFFER_TICKS))
            : MAX_WINDUP_TICKS;
        if (player.tickCount - this.preparedTick > windupLimit) {
            this.cancelPreparedStab(mc);
            return;
        }
        if (!this.canStab(mc, player) || !canJab(mc, player, spear)) return;
        if (this.preparedLunge && (player.isInWater() || player.isFallFlying() || player.isPassenger()
                || !player.getFoodData().hasEnoughFood() && !player.getAbilities().mayfly)) {
            this.cancelPreparedStab(mc);
            return;
        }
        if (!this.preparedLunge && !enemyPastReach(player)) {
            this.cancelPreparedStab(mc);
            return;
        }
        if (stab(mc, player, weapon)) {
            this.preparedSlot = -1;
            this.returnSlot = -1;
        } else {
            this.cancelPreparedStab(mc);
        }
    }

    private void cancelPreparedStab(Minecraft mc) {
        this.preparedSlot = -1;
        this.returnSlot = -1;
        this.slots.drop(mc, this.switchBack.get());
    }

    private static boolean stab(Minecraft mc, LocalPlayer player, PiercingWeapon weapon) {
        MeleeAttacks.syncSprint(player);
        mc.gameMode.piercingAttack(weapon);
        if (MeleeAttacks.actedThisTick(player)) {
            player.swing(InteractionHand.MAIN_HAND);
            MeleeAttacks.claimTick(mc);
            return true;
        }
        return false;
    }

    private boolean canStab(Minecraft mc, LocalPlayer player) {
        return MeleeAttacks.canAct(mc) && MeleeAttacks.handsFree(mc, player) && !MeleeAttacks.actedThisTick(player)
            ? !CombatMacroLock.heldByOther(this) && !AutoInvTotemModule.wantsHotbar()
            : false;
    }

    private static boolean canJab(Minecraft mc, LocalPlayer player, ItemStack spear) {
        return mc.missTime <= 0 && !mc.gameMode.isSpectator()
            ? spear.isItemEnabled(mc.level.enabledFeatures()) && !player.cannotAttackWithItem(spear, 0)
            : false;
    }

    private static boolean enemyPastReach(LocalPlayer player) {
        Rotation sent = ServerRotation.sent();
        Vec3 look = sent != null ? Vec3.directionFromRotation(sent.pitch(), sent.yaw()) : player.getViewVector(1.0F);
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(look.scale(POKE_RANGE));
        double reach = player.entityInteractionRange();
        AABB area = player.getBoundingBox().expandTowards(look.scale(POKE_RANGE)).inflate(1.0);

        for (Entity entity : player.level().getEntities(player, area)) {
            if (MeleeAttacks.isEnemy(player, entity)) {
                Optional<Vec3> at = entity.getBoundingBox().inflate(POKE_MARGIN).clip(eye, end);
                if (at.isPresent() && eye.distanceTo(at.get()) > reach && MeleeAttacks.lineIsClear(player, eye, at.get())) {
                    return true;
                }
            }
        }

        return false;
    }

    private static int bestSpear(Inventory inventory, boolean lunge) {
        int selected = inventory.getSelectedSlot();
        int best = -1;
        int bestLevel = 0;

        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.has(DataComponents.PIERCING_WEAPON) && !stack.nextDamageWillBreak()) {
                int level = lungeLevel(stack);
                boolean better = !lunge ? best < 0 || level < bestLevel : level > bestLevel || level > 0 && level == bestLevel && slot == selected;
                if (better) {
                    best = slot;
                    bestLevel = level;
                }
            }
        }

        return best;
    }

    private static int lungeLevel(ItemStack stack) {
        for (Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(Enchantments.LUNGE)) {
                return entry.getIntValue();
            }
        }

        return 0;
    }
}
