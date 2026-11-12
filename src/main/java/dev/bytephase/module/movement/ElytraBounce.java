package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.input.KeyOverride;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.Vec3d;

/** Lets a gliding player bounce from the ground and relaunch while jump is held. */
public final class ElytraBounce extends Module {
    private final NumberSetting angle = add(new NumberSetting("Angle", 75.0, 0.0, 90.0, 1.0));
    private final BooleanSetting autoForward = add(new BooleanSetting("AutoForward", true));
    private final BooleanSetting autoJump = add(new BooleanSetting("AutoJump", true));
    private final BooleanSetting preserveMomentum = add(new BooleanSetting("PreserveMomentum", true));
    private boolean wasGliding;

    public ElytraBounce() {
        super("ElytraBounce", Category.MOVEMENT, "Instant elytra recast with horizontal momentum retention");
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        KeyOverride.releaseAll(this);
        reset();
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            KeyOverride.releaseAll(this);
            reset();
            return;
        }

        var player = mc.player;
        boolean hasElytra = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).isOf(Items.ELYTRA);
        if (mc.currentScreen != null) {
            KeyOverride.releaseAll(this);
            reset();
            return;
        }
        if (!hasElytra) {
            KeyOverride.releaseAll(this);
            reset();
            return;
        }
        if (autoForward.getValue()) {
            KeyOverride.set(this, mc.options.forwardKey, true);
        } else {
            KeyOverride.release(this, mc.options.forwardKey);
        }
        if (autoJump.getValue()) {
            KeyOverride.set(this, mc.options.jumpKey, true);
        } else {
            KeyOverride.release(this, mc.options.jumpKey);
        }
        boolean jumpHeld = mc.options.jumpKey.isPressed();
        if (!jumpHeld) {
            reset();
            return;
        }

        // Minecraft pitch is positive downwards. Only change the view while
        // bounce is active and a jump/recast is being requested.
        player.setPitch(angle.asFloat());

        // Ground contact can precede the server's glide flag update. Handle it
        // first or a stale isGliding() flag prevents the bounce entirely.
        if (player.isOnGround()) {
            if (wasGliding) {
                Vec3d momentum = player.getVelocity();
                player.jump();
                if (preserveMomentum.getValue()) {
                    preserveHorizontalSpeed(player, momentum);
                }
            }
            wasGliding = false;
            return;
        }
        if (player.isGliding()) {
            wasGliding = true;
            return;
        }

        // Recast immediately on any airborne tick while jump is held. If a
        // server rejects a request, the next tick retries without pausing or
        // resetting horizontal velocity.
        if (player.checkGliding() && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(
                    player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            wasGliding = true;
        }
    }

    private void preserveHorizontalSpeed(net.minecraft.client.network.ClientPlayerEntity player, Vec3d beforeJump) {
        var afterJump = player.getVelocity();
        double oldSpeed = beforeJump.horizontalLength();
        double newSpeed = afterJump.horizontalLength();
        if (oldSpeed > newSpeed && newSpeed > 1.0e-6) {
            double scale = oldSpeed / newSpeed;
            player.setVelocityClient(new Vec3d(
                    afterJump.x * scale, afterJump.y, afterJump.z * scale));
        } else if (oldSpeed > 1.0e-6 && newSpeed <= 1.0e-6) {
            player.setVelocityClient(new Vec3d(beforeJump.x, afterJump.y, beforeJump.z));
        }
    }

    private void reset() {
        wasGliding = false;
    }

}
