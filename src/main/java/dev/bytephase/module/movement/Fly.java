package dev.bytephase.module.movement;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Free flight.
 *
 * <p>Abilities mode lifts the vanilla
 * fly speed, Velocity mode writes the velocity directly so it works on servers
 * that never grant flight. Anti-kick drops the player slightly while hovering,
 * which is the classic "not flying" position pattern.</p>
 */
public final class Fly extends Module {
    private static final double ANTI_KICK_DROP = 0.0313;

    private final ModeSetting mode = add(new ModeSetting("Mode", new String[]{"Velocity", "Abilities"}, "Velocity"));
    private final NumberSetting speed = add(new NumberSetting("Speed", 0.6, 0.1, 5.0, 0.05));
    private final BooleanSetting verticalSpeedMatch = add(new BooleanSetting("VerticalMatch", true));
    private final BooleanSetting noSneak = add(new BooleanSetting("NoSneak", true));
    private final BooleanSetting antiKick = add(new BooleanSetting("AntiKick", true));

    private int idleTicks;
    private boolean savedAbilities;
    private boolean savedAllowFlying;
    private boolean savedFlying;
    private float savedFlySpeed;
    private ClientPlayerEntity abilitiesOwner;

    public Fly() {
        super("Fly", Category.MOVEMENT, "Free flight");
    }

    @Override
    protected void onEnable() {
        idleTicks = 0;
        savedAbilities = false;
    }

    @Override
    protected void onDisable() {
        restoreAbilities();
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) {
            return;
        }
        var player = mc.player;
        player.fallDistance = 0.0f;

        if (mode.getValue().equals("Abilities")) {
            if (player.isSpectator()) {
                restoreAbilities();
                return;
            }
            if (savedAbilities && abilitiesOwner != player) {
                restoreAbilities();
            }
            var abilities = player.getAbilities();
            if (!savedAbilities) {
                savedAllowFlying = abilities.allowFlying;
                savedFlying = abilities.flying;
                savedFlySpeed = abilities.getFlySpeed();
                abilitiesOwner = player;
                savedAbilities = true;
            }
            abilities.allowFlying = true;
            abilities.flying = true;
            abilities.setFlySpeed((float) (0.05 * speed.getValue() * 2.0));
            return;
        }

        // A live mode change must undo Abilities mode immediately; waiting for
        // the module to be disabled leaves allowFlying/flying enabled.
        restoreAbilities();

        var options = mc.options;
        double dx = (options.leftKey.isPressed() ? -1.0 : 0.0) + (options.rightKey.isPressed() ? 1.0 : 0.0);
        double dz = (options.forwardKey.isPressed() ? 1.0 : 0.0) - (options.backKey.isPressed() ? 1.0 : 0.0);
        double dy = 0.0;
        if (options.jumpKey.isPressed()) {
            dy += verticalSpeedMatch.getValue() ? speed.getValue() : 0.6;
        }
        if (options.sneakKey.isPressed() && !noSneak.getValue()) {
            dy -= verticalSpeedMatch.getValue() ? speed.getValue() : 0.6;
        }

        float yaw = player.getYaw();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double vx = 0.0;
        double vz = 0.0;
        if (horizontal > 0.0) {
            double norm = 1.0 / horizontal;
            dx *= norm;
            dz *= norm;
            float rad = yaw * MathHelper.RADIANS_PER_DEGREE;
            vx = (-Math.sin(rad) * dz - Math.cos(rad) * dx) * speed.getValue();
            vz = (Math.cos(rad) * dz - Math.sin(rad) * dx) * speed.getValue();
        }

        if (antiKick.getValue() && horizontal == 0.0 && dy == 0.0) {
            idleTicks++;
            if (idleTicks % 20 == 0) {
                dy = -ANTI_KICK_DROP * 4.0;
            }
        } else {
            idleTicks = 0;
        }

        player.setVelocityClient(new Vec3d(vx, dy, vz));
    }

    private void restoreAbilities() {
        if (!savedAbilities || abilitiesOwner == null) {
            return;
        }
        var abilities = abilitiesOwner.getAbilities();
        abilities.allowFlying = savedAllowFlying;
        abilities.flying = savedFlying;
        abilities.setFlySpeed(savedFlySpeed);
        savedAbilities = false;
        abilitiesOwner = null;
    }
}
