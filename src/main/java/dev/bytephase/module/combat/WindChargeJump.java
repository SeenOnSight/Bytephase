package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.util.MaceUtil;
import net.minecraft.item.Items;

/**
 * One key press: look straight down, jump, throw a wind charge at your feet at
 * the top of the jump, and get ready with the mace.
 *
 * <p>The charge explodes right under you, throwing you roughly seven blocks
 * up. Throwing at the apex of the jump (rather than from the ground) gives the
 * launch on top of the jump's own height, which is what makes it the "perfect"
 * wind jump. Bind the module to a key; it runs once and switches itself off.
 * Pair it with AutoWindBurst or hit manually on the way down.</p>
 */
public final class WindChargeJump extends Module {
    private static final double APEX_VELOCITY = 0.05;
    private static final int MAX_WAIT_TICKS = 12;

    private final ModeSetting timing = add(new ModeSetting("Timing", new String[]{"Apex", "Immediate"}, "Apex"));
    private final BooleanSetting jump = add(new BooleanSetting("Jump", true));
    private final BooleanSetting lookDown = add(new BooleanSetting("LookDown", true));
    private final BooleanSetting swapToMace = add(new BooleanSetting("SwapToMace", true));
    private final BooleanSetting restoreLook = add(new BooleanSetting("RestoreLook", true));

    private enum Stage { START, WAIT, THROW }

    private Stage stage = Stage.START;
    private int ticks;
    private float savedPitch;

    public WindChargeJump() {
        super("WindChargeJump", Category.COMBAT, "One key: look down, jump, wind charge at the apex, ready the mace");
    }

    @Override
    protected void onEnable() {
        stage = Stage.START;
        ticks = 0;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || mc.player.isDead()) {
            setEnabled(false);
            return;
        }
        var player = mc.player;
        switch (stage) {
            case START -> {
                if (!MaceUtil.hasItem(mc, Items.WIND_CHARGE)) {
                    setEnabled(false);
                    return;
                }
                savedPitch = player.getPitch();
                if (lookDown.getValue()) {
                    player.setPitch(90.0f);
                }
                boolean jumped = false;
                if (jump.getValue() && player.isOnGround()) {
                    player.jump();
                    jumped = true;
                }
                // Already airborne (or no jump wanted): there is no apex to wait for.
                stage = timing.getValue().equals("Immediate") || !jumped ? Stage.THROW : Stage.WAIT;
                ticks = 0;
                if (stage == Stage.THROW) {
                    throwCharge(mc);
                }
            }
            case WAIT -> {
                ticks++;
                if (lookDown.getValue()) {
                    player.setPitch(90.0f);
                }
                if (player.getVelocity().y <= APEX_VELOCITY || ticks >= MAX_WAIT_TICKS) {
                    throwCharge(mc);
                }
            }
            case THROW -> finish(mc);
        }
    }

    private void throwCharge(net.minecraft.client.MinecraftClient mc) {
        var player = mc.player;
        if (lookDown.getValue()) {
            player.setPitch(90.0f);
            MaceUtil.sendLook(mc, player.getYaw(), 90.0f);
        }
        // Keep the charge selected only if we are not about to move to the mace.
        MaceUtil.useItem(mc, Items.WIND_CHARGE, true);
        stage = Stage.THROW;
    }

    private void finish(net.minecraft.client.MinecraftClient mc) {
        var player = mc.player;
        if (swapToMace.getValue()) {
            int mace = MaceUtil.findBestMace(player);
            if (mace >= 0) {
                MaceUtil.selectNow(mc, mace);
            }
        }
        if (restoreLook.getValue()) {
            player.setPitch(savedPitch);
        }
        setEnabled(false);
    }
}
