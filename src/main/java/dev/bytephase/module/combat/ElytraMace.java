package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.AttackLimiter;
import dev.bytephase.util.CameraAim;
import dev.bytephase.util.MaceUtil;
import dev.bytephase.util.RotationUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Chains Wind Burst smashes with elytra flights until the target dies.
 *
 * <p>A smash with a Wind Burst mace arms it (so it pairs with AutoWindBurst or
 * your own hits). From there it loops:</p>
 * <ol>
 *   <li><b>Equip:</b> select the elytra in your hotbar and right-click it,
 *       which swaps it with your chestplate, like you would by hand.</li>
 *   <li><b>Fly:</b> start gliding and rocket straight at the target's head.</li>
 *   <li><b>Drop:</b> once close and above them, select the slot that now holds
 *       your chestplate and right-click it to put it back on, which ends the
 *       glide.</li>
 *   <li><b>Smash:</b> hit the target with the mace (the module does this
 *       itself) as you fall onto them. The Wind Burst launches you up again and
 *       the loop starts over.</li>
 * </ol>
 *
 * <p>Needs the elytra in your hotbar (otherwise it falls back to inventory
 * clicks), rockets, and a Wind Burst mace in the hotbar.</p>
 */
public final class ElytraMace extends Module {
    // 0 aims straight at the target's head. Raise it only to approach from higher up.
    private final NumberSetting aimHeight = add(new NumberSetting("AimHeight", 0.0, 0.0, 10.0, 0.5));
    // Take the elytra off once this close (in a straight line) to the target...
    private final NumberSetting switchRange = add(new NumberSetting("SwitchRange", 7.0, 3.0, 25.0, 0.5));
    // ...and at least this far above their feet, so the drop is long enough to smash.
    private final NumberSetting minAbove = add(new NumberSetting("MinAbove", 3.0, 0.0, 15.0, 0.5));
    private final BooleanSetting lead = add(new BooleanSetting("Lead", true));
    private final NumberSetting reach = add(new NumberSetting("Reach", 3.0, 2.0, 6.0, 0.1));
    // Off by default: the smash and Wind Burst launch work at any charge.
    private final BooleanSetting waitForCharge = add(new BooleanSetting("WaitForCharge", false));
    private final BooleanSetting loop = add(new BooleanSetting("Loop", true));
    private final NumberSetting maxLoops = add(new NumberSetting("MaxLoops", 30.0, 1.0, 100.0, 1.0));
    private final BooleanSetting targetHostiles = add(new BooleanSetting("TargetHostiles", false));
    private final BooleanSetting targetPassives = add(new BooleanSetting("TargetPassives", false));
    private final ModeSetting equipMode = add(new ModeSetting("Equip", new String[]{"Hotbar", "Inventory"}, "Hotbar"));
    private final NumberSetting equipDelay = add(new NumberSetting("EquipDelay", 2.0, 0.0, 10.0, 1.0));
    private final NumberSetting rocketDelay = add(new NumberSetting("RocketDelay", 12.0, 4.0, 40.0, 1.0));
    private final NumberSetting minSpeed = add(new NumberSetting("MinSpeed", 1.0, 0.2, 3.0, 0.1));
    private final NumberSetting maxFlight = add(new NumberSetting("MaxFlight", 150.0, 40.0, 400.0, 10.0));
    private final NumberSetting maxRange = add(new NumberSetting("MaxRange", 100.0, 20.0, 250.0, 5.0));
    private final NumberSetting rotationSpeed = add(new NumberSetting("RotationSpeed", 50.0, 5.0, 360.0, 5.0));
    private final NumberSetting smoothing = add(new NumberSetting("Smoothing", 15.0, 0.0, 100.0, 5.0));
    // Prints each lap and the exact reason a sequence stopped.
    private final BooleanSetting debug = add(new BooleanSetting("Debug", false));

    private enum Stage { IDLE, EQUIP, GLIDE, FLY, FALL, LAUNCH }

    private static final long LAST_HIT_MEMORY_NANOS = 20_000_000_000L;

    private Stage stage = Stage.IDLE;
    private LivingEntity target;
    // Whoever the local player hit most recently, with any weapon.
    private LivingEntity lastHit;
    private long lastHitNanos;
    // Hotbar slot that holds the chestplate while the elytra is worn (and the elytra while it is not).
    private int stowSlot = -1;
    // Inventory index used only by the click fallback.
    private int stowIndex = -1;
    private boolean hadChestplate;
    private int ticks;
    private int wait;
    private int attempts;
    private int loops;
    private int rocketCooldown;
    // Whether the smash that started LAUNCH was made with a Wind Burst mace.
    private boolean smashWindBurst;

    public ElytraMace() {
        super("ElytraMace", Category.COMBAT, "Wind Burst smash, elytra back onto the target, smash again until it dies");
    }

    /** True while a sequence is running; other inventory managers should stay out of the armor slots. */
    public static boolean isBusy() {
        if (BytePhase.modules == null) {
            return false;
        }
        ElytraMace module = BytePhase.modules.get(ElytraMace.class);
        return module != null && module.isEnabled() && module.stage != Stage.IDLE;
    }

    /**
     * The entity this module is (or is about to be) chasing: the current
     * target, or whoever was hit within the last few seconds.
     */
    public static LivingEntity preferredTarget() {
        if (BytePhase.modules == null) {
            return null;
        }
        ElytraMace module = BytePhase.modules.get(ElytraMace.class);
        if (module == null || !module.isEnabled()) {
            return null;
        }
        if (module.stage != Stage.IDLE && module.target != null && module.target.isAlive()) {
            return module.target;
        }
        return module.recentVictim();
    }

    /** Players always; hostile and passive mobs only when enabled. */
    private boolean validTarget(LivingEntity entity) {
        var mc = BytePhase.mc;
        if (entity == null || entity == mc.player || !entity.isAlive() || entity.isRemoved() || entity.isSpectator()) {
            return false;
        }
        if (entity instanceof PlayerEntity) {
            return true;
        }
        boolean hostile = entity.getType().getSpawnGroup() == SpawnGroup.MONSTER;
        return hostile ? targetHostiles.getValue() : targetPassives.getValue();
    }

    private LivingEntity recentVictim() {
        if (validTarget(lastHit) && System.nanoTime() - lastHitNanos < LAST_HIT_MEMORY_NANOS) {
            return lastHit;
        }
        return null;
    }

    @Override
    protected void onDisable() {
        lastHit = null;
        if (stage != Stage.IDLE) {
            restoreChestplate(false);
        }
        finish();
    }

    /** Arms the sequence when the local player smashes with a Wind Burst mace. */
    @Override
    public void onAttack(PlayerEntity self, Entity attacked) {
        var mc = BytePhase.mc;
        if (mc == null || self != mc.player || !(attacked instanceof LivingEntity living) || !validTarget(living)) {
            return;
        }
        lastHit = living;
        lastHitNanos = System.nanoTime();
        if (stage != Stage.IDLE) {
            // Hitting someone new mid-sequence re-aims the flight at them.
            if (stage != Stage.EQUIP && stage != Stage.LAUNCH) {
                target = living;
            }
            // A smash landed by something else (AutoWindBurst, your own click)
            // while we are dropping counts as the lap's smash, so the loop goes on.
            if (stage == Stage.FALL && MaceUtil.isMace(self.getMainHandStack())
                    && MaceUtil.canSmash(self)) {
                beginLaunch(MaceUtil.level(self.getMainHandStack(), Enchantments.WIND_BURST) > 0);
            }
            return;
        }
        ItemStack held = self.getMainHandStack();
        if (!MaceUtil.isMace(held) || MaceUtil.level(held, Enchantments.WIND_BURST) <= 0
                || !MaceUtil.canSmash(self)) {
            return;
        }
        boolean wearingElytra = isGlider(self.getEquippedStack(EquipmentSlot.CHEST));
        if (!wearingElytra && findElytra() < 0 || !hasRocket()) {
            return;
        }
        target = living;
        loops = 0;
        beginEquip();
    }

    private void beginEquip() {
        stage = Stage.EQUIP;
        ticks = 0;
        wait = equipDelay.asInt();
        attempts = 0;
        rocketCooldown = 0;
        stowSlot = -1;
        stowIndex = -1;
    }

    @Override
    public void onTick() {
        if (stage == Stage.IDLE) {
            return;
        }
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.player.isDead()) {
            finish("not in a world, or dead");
            return;
        }
        PlayerEntity player = mc.player;
        ticks++;
        if (!validTarget(target)) {
            // The target died or left. Carry on with someone else you hit, otherwise stop.
            LivingEntity next = recentVictim();
            if (next == null || next == target) {
                abort("target died or left");
                return;
            }
            target = next;
        }
        if (player.getEntityPos().distanceTo(target.getEntityPos()) > maxRange.getValue()) {
            abort("target beyond MaxRange");
            return;
        }
        switch (stage) {
            case EQUIP -> equip(player);
            case GLIDE -> glide(player);
            case FLY -> fly(player);
            case FALL -> fall(player);
            case LAUNCH -> launch(player);
            default -> { }
        }
    }

    // ---- stages ----------------------------------------------------------

    private void equip(PlayerEntity player) {
        // Give the Wind Burst launch a tick or two to take hold, and give a
        // previous attempt time to be confirmed before judging it.
        if (wait > 0) {
            wait--;
            return;
        }
        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        if (isGlider(chest)) {
            stage = Stage.GLIDE;
            ticks = 0;
            attempts = 0;
            return;
        }
        if (!hasRocket()) {
            abort("out of firework rockets (hotbar or offhand)");
            return;
        }
        if (attempts >= 4) {
            abort("could not put the elytra on");
            return;
        }
        attempts++;
        if (attempts == 1) {
            hadChestplate = !chest.isEmpty();
        }
        int hotbar = equipMode.getValue().equals("Hotbar") && attempts <= 2 ? findElytraHotbar() : -1;
        if (hotbar >= 0) {
            // Select the elytra and right-click it: it swaps with the chestplate.
            stowSlot = hotbar;
            useHotbarSlot(hotbar);
        } else {
            int index = findElytra();
            if (index < 0 || !swapChest(index)) {
                abort("no usable elytra");
                return;
            }
            stowIndex = index;
        }
        wait = confirmTicks();
    }

    private void glide(PlayerEntity player) {
        var mc = BytePhase.mc;
        if (player.isGliding()) {
            stage = Stage.FLY;
            ticks = 0;
            return;
        }
        if (!isGlider(player.getEquippedStack(EquipmentSlot.CHEST)) || ticks > 15) {
            abort("could not start gliding");
            return;
        }
        if (!player.isOnGround() && player.checkGliding() && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(player,
                    ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }
    }

    private void fly(PlayerEntity player) {
        // Gliding stopping on its own (hit a wall, ran out of elytra) ends the approach.
        if (ticks > 3 && !player.isGliding() || ticks > maxFlight.asInt()) {
            beginFall();
            return;
        }
        Vec3d head = target.getEyePos().add(0.0, aimHeight.getValue(), 0.0);
        double distance = player.getEntityPos().distanceTo(target.getEntityPos());
        Vec3d goal = head;
        if (lead.getValue()) {
            // Aim where a moving target will be by the time we arrive.
            double speed = Math.max(0.8, player.getVelocity().length());
            double ticksAhead = Math.min(12.0, distance / speed);
            Vec3d motion = new Vec3d(target.getX() - target.lastX, target.getY() - target.lastY,
                    target.getZ() - target.lastZ);
            goal = head.add(motion.multiply(ticksAhead));
        }
        aimAt(goal);
        rocketCooldown--;
        if (rocketCooldown <= 0 && player.getVelocity().length() < minSpeed.getValue() && useRocket()) {
            rocketCooldown = rocketDelay.asInt();
        }
        boolean farEnoughAbove = player.getY() >= target.getY() + minAbove.getValue();
        // Within range and high enough: drop. Very close: drop regardless, since
        // flying through them does nothing.
        if (distance <= switchRange.getValue() && farEnoughAbove || distance <= 3.0) {
            beginFall();
        }
    }

    /** Puts the chestplate back on (ending the glide) so the drop builds up smash distance. */
    private void beginFall() {
        attempts = 0;
        restoreChestplate(false);
        wait = confirmTicks();
        stage = Stage.FALL;
        ticks = 0;
    }

    private void fall(PlayerEntity player) {
        var mc = BytePhase.mc;
        aimAt(target.getBoundingBox().getCenter());
        // Elytra still on: the restore did not take. Retry, then fall back to clicks.
        if (isGlider(player.getEquippedStack(EquipmentSlot.CHEST))) {
            if (wait > 0) {
                wait--;
                return;
            }
            if (attempts >= 3) {
                abort("could not take the elytra off");
                return;
            }
            attempts++;
            restoreChestplate(attempts >= 2);
            wait = confirmTicks();
            return;
        }
        if (player.isOnGround() || ticks > 120) {
            finish("landed without a smash");
            return;
        }
        // Still gliding, or not fallen far enough yet.
        if (!MaceUtil.canSmash(player)
                || distanceTo(player.getEyePos(), target.getBoundingBox()) > reach.getValue()) {
            return;
        }
        // Optional: full damage needs a charged mace, but the launch does not. Never wait forever.
        if (waitForCharge.getValue() && player.getAttackCooldownProgress(0.5f) < 0.85f && ticks < 25) {
            return;
        }
        int mace = MaceUtil.findBestMace(player);
        if (mace < 0) {
            abort("no mace in the hotbar");
            return;
        }
        if (player.getInventory().getSelectedSlot() != mace) {
            MaceUtil.selectNow(mc, mace);
        }
        if (AttackLimiter.tryAttack(player, target, true)) {
            beginLaunch(MaceUtil.level(player.getMainHandStack(), Enchantments.WIND_BURST) > 0);
        }
    }

    private void beginLaunch(boolean windBurst) {
        stage = Stage.LAUNCH;
        ticks = 0;
        smashWindBurst = windBurst;
        note("smash landed" + (windBurst ? "" : " (mace has no Wind Burst)"));
    }

    /**
     * Starts the next lap after a smash. The Wind Burst hit itself is what
     * launches you, so the lap does not wait on a velocity reading (which can
     * arrive late or low); the reading is only used to confirm a mace that is
     * not known to have Wind Burst.
     */
    private void launch(PlayerEntity player) {
        boolean launched = player.getVelocity().y > 0.3;
        if (!smashWindBurst && !launched && ticks < 6) {
            return;
        }
        if (!smashWindBurst && !launched) {
            finish("that smash did not launch you");
            return;
        }
        // Give the launch and a possible death packet a moment to arrive.
        if (ticks < 2) {
            return;
        }
        if (!loop.getValue()) {
            finish("Loop is off");
        } else if (!validTarget(target)) {
            finish("target is dead");
        } else if (loops + 1 >= maxLoops.asInt()) {
            finish("MaxLoops reached (" + maxLoops.asInt() + ")");
        } else if (!hasRocket()) {
            finish("out of firework rockets (hotbar or offhand)");
        } else if (findElytraAnywhere() < 0) {
            finish("no usable elytra left");
        } else {
            loops++;
            note("lap " + (loops + 1));
            beginEquip();
        }
    }

    private void aimAt(Vec3d point) {
        var player = BytePhase.mc.player;
        Vec3d eye = player.getEyePos();
        CameraAim.aim(RotationUtil.yawTo(eye, point), RotationUtil.pitchTo(eye, point),
                rotationSpeed.asFloat(), smoothing.asFloat() / 100.0f, false);
    }

    private void note(String message) {
        var mc = BytePhase.mc;
        if (debug.getValue() && mc != null && mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal("[BytePhase] ElytraMace: " + message));
        }
    }

    private void abort(String why) {
        note("stopped - " + why);
        restoreChestplate(false);
        finish();
    }

    private void finish(String why) {
        note("finished - " + why);
        finish();
    }

    private void finish() {
        CameraAim.release();
        stage = Stage.IDLE;
        target = null;
        stowSlot = -1;
        stowIndex = -1;
    }

    private static double distanceTo(Vec3d eye, Box box) {
        double x = MathHelper.clamp(eye.x, box.minX, box.maxX);
        double y = MathHelper.clamp(eye.y, box.minY, box.maxY);
        double z = MathHelper.clamp(eye.z, box.minZ, box.maxZ);
        return eye.distanceTo(new Vec3d(x, y, z));
    }

    /** Ticks to wait for the server to confirm a swap: a round trip plus a margin. */
    private static int confirmTicks() {
        var mc = BytePhase.mc;
        int ping = 0;
        if (mc != null && mc.player != null && mc.getNetworkHandler() != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) {
                ping = entry.getLatency();
            }
        }
        return Math.min(8, ping / 50 + 2);
    }

    // ---- elytra / chestplate swapping -----------------------------------

    private static boolean isGlider(ItemStack stack) {
        return stack != null && stack.contains(DataComponentTypes.GLIDER);
    }

    private static boolean isChestplate(ItemStack stack) {
        if (stack == null || stack.isEmpty() || isGlider(stack)) {
            return false;
        }
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
    }

    private static boolean usableElytra(ItemStack stack) {
        // An elytra at one durability left cannot glide.
        return isGlider(stack) && (!stack.isDamageable() || stack.getMaxDamage() - stack.getDamage() > 1);
    }

    private static int findElytraHotbar() {
        var inventory = BytePhase.mc.player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (usableElytra(inventory.getStack(slot))) {
                return slot;
            }
        }
        return -1;
    }

    /** Inventory index of a usable elytra anywhere (hotbar or main), or -1. */
    private static int findElytra() {
        var inventory = BytePhase.mc.player.getInventory();
        for (int i = 0; i < inventory.getMainStacks().size(); i++) {
            if (usableElytra(inventory.getStack(i))) {
                return i;
            }
        }
        return -1;
    }

    /** Whether another lap is possible: an elytra is worn or can be found. */
    private static int findElytraAnywhere() {
        if (isGlider(BytePhase.mc.player.getEquippedStack(EquipmentSlot.CHEST))) {
            return 0;
        }
        return findElytra();
    }

    private static int findChestplateHotbar() {
        var inventory = BytePhase.mc.player.getInventory();
        int found = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!isChestplate(stack)) {
                continue;
            }
            if (stack.isOf(Items.NETHERITE_CHESTPLATE)) {
                return slot;
            }
            if (found < 0) {
                found = slot;
            }
        }
        return found;
    }

    private static int findChestplate() {
        var inventory = BytePhase.mc.player.getInventory();
        int found = -1;
        for (int i = 0; i < inventory.getMainStacks().size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!isChestplate(stack)) {
                continue;
            }
            if (stack.isOf(Items.NETHERITE_CHESTPLATE)) {
                return i;
            }
            if (found < 0) {
                found = i;
            }
        }
        return found;
    }

    private static int findEmptySlot() {
        var inventory = BytePhase.mc.player.getInventory();
        for (int i = 0; i < inventory.getMainStacks().size(); i++) {
            if (inventory.getStack(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Selects a hotbar slot, right-clicks it, and selects the previous slot
     * again. Right-clicking armor equips it and swaps it with whatever is
     * worn, which is exactly how you would put the elytra (or chestplate) on
     * by hand.
     */
    private static void useHotbarSlot(int slot) {
        var mc = BytePhase.mc;
        var player = mc.player;
        int previous = player.getInventory().getSelectedSlot();
        MaceUtil.selectNow(mc, slot);
        mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
        if (previous != slot) {
            MaceUtil.selectNow(mc, previous);
        }
    }

    /** Puts the chestplate back (best effort); ends gliding because the elytra is no longer worn. */
    private void restoreChestplate(boolean forceClick) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || !isGlider(mc.player.getEquippedStack(EquipmentSlot.CHEST))) {
            return;
        }
        var inventory = mc.player.getInventory();
        if (!forceClick) {
            // The chestplate is in the hotbar slot the elytra came from.
            if (stowSlot >= 0 && stowSlot < 9 && isChestplate(inventory.getStack(stowSlot))) {
                useHotbarSlot(stowSlot);
                return;
            }
            int hotbar = hadChestplate ? findChestplateHotbar() : -1;
            if (hotbar >= 0) {
                stowSlot = hotbar;
                useHotbarSlot(hotbar);
                return;
            }
        }
        int index = stowIndex;
        boolean valid = index >= 0 && (hadChestplate ? isChestplate(inventory.getStack(index))
                : inventory.getStack(index).isEmpty());
        if (!valid) {
            index = hadChestplate ? findChestplate() : findEmptySlot();
        }
        if (index >= 0) {
            swapChest(index);
        }
    }

    /**
     * Fallback for an elytra that is not in the hotbar: swaps the chest slot
     * with an inventory slot using three PICKUP clicks (the same method
     * AutoArmor and ChestSwap use). Works whether either side is empty.
     */
    private static boolean swapChest(int inventoryIndex) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.interactionManager == null
                || !(mc.player.currentScreenHandler instanceof PlayerScreenHandler handler)
                || !handler.getCursorStack().isEmpty()) {
            return false;
        }
        int slot = inventoryIndex >= PlayerScreenHandler.INVENTORY_START
                ? inventoryIndex
                : inventoryIndex + PlayerScreenHandler.HOTBAR_START;
        int chestSlot = PlayerScreenHandler.EQUIPMENT_START + 1;
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(handler.syncId, chestSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        return true;
    }

    // ---- rockets --------------------------------------------------------

    private static boolean hasRocket() {
        var player = BytePhase.mc.player;
        if (player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            return true;
        }
        return findRocketSlot() >= 0;
    }

    private static int findRocketSlot() {
        var inventory = BytePhase.mc.player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getStack(slot).isOf(Items.FIREWORK_ROCKET)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean useRocket() {
        var mc = BytePhase.mc;
        var player = mc.player;
        if (player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            mc.interactionManager.interactItem(player, Hand.OFF_HAND);
            player.swingHand(Hand.OFF_HAND);
            return true;
        }
        int slot = findRocketSlot();
        if (slot < 0) {
            return false;
        }
        int previous = player.getInventory().getSelectedSlot();
        MaceUtil.selectNow(mc, slot);
        mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
        player.swingHand(Hand.MAIN_HAND);
        MaceUtil.selectNow(mc, previous);
        return true;
    }
}
