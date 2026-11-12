package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.MaceUtil;
import dev.bytephase.util.RotationUtil;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * One key press: throw an ender pearl where you are looking, then a wind
 * charge a few ticks later so the launch carries you into the pearl's path.
 *
 * <p>In {@code Auto} timing the module simulates the pearl's flight from your
 * aim and the launch you would get from the charge, then picks the delay where
 * the two paths come closest. The simulation uses vanilla's projectile and
 * player physics but an estimated launch strength ({@code LaunchBoost}), so
 * treat the result as a good starting point and use {@code Manual} timing once
 * you know the delay you like. With {@code Directional} on, the charge is
 * thrown at the ground <em>behind</em> you so the blast pushes you toward the
 * pearl instead of straight up.</p>
 */
public final class PearlCatch extends Module {
    private static final int SIM_TICKS = 80;
    private static final int WIND_FLIGHT_TICKS = 2;
    private static final int MAX_DELAY = 30;

    private final ModeSetting timing = add(new ModeSetting("Timing", new String[]{"Auto", "Manual"}, "Auto"));
    private final NumberSetting delay = add(new NumberSetting("Delay", 5.0, 0.0, 30.0, 1.0));
    private final BooleanSetting directional = add(new BooleanSetting("Directional", true));
    private final NumberSetting launchBoost = add(new NumberSetting("LaunchBoost", 1.4, 0.5, 3.0, 0.1));
    private final BooleanSetting swapToMace = add(new BooleanSetting("SwapToMace", false));
    private final BooleanSetting report = add(new BooleanSetting("Report", true));

    private enum Stage { START, WAIT }

    private Stage stage = Stage.START;
    private int countdown;
    private float windYaw;
    private float windPitch;

    public PearlCatch() {
        super("PearlCatch", Category.COMBAT, "One key: ender pearl, then a timed wind charge to catch it");
    }

    @Override
    protected void onEnable() {
        stage = Stage.START;
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
        if (stage == Stage.START) {
            if (!MaceUtil.hasItem(mc, Items.ENDER_PEARL) || !MaceUtil.hasItem(mc, Items.WIND_CHARGE)) {
                setEnabled(false);
                return;
            }
            Vec3d eye = player.getEyePos();
            Vec3d look = Vec3d.fromPolar(player.getPitch(), player.getYaw());
            // Aim the charge behind you when the pearl travels sideways, so the blast pushes you toward it.
            Vec3d horizontal = new Vec3d(look.x, 0.0, look.z);
            boolean sideways = directional.getValue() && horizontal.length() > 0.25;
            Vec3d pushDirection = sideways ? horizontal.normalize() : Vec3d.ZERO;
            Vec3d windPoint = sideways
                    ? player.getEntityPos().add(pushDirection.multiply(-1.4))
                    : player.getEntityPos().add(0.0, -1.0, 0.0);
            windYaw = RotationUtil.yawTo(eye, windPoint);
            windPitch = sideways ? RotationUtil.pitchTo(eye, windPoint) : 90.0f;

            int wait = delay.asInt();
            if (timing.getValue().equals("Auto")) {
                double[] solved = solveDelay(look, pushDirection);
                wait = (int) solved[0];
                if (report.getValue()) {
                    mc.inGameHud.getChatHud().addMessage(Text.literal("[BytePhase] PearlCatch: wind charge in "
                            + wait + " ticks (closest approach ~" + Math.round(solved[1] * 10.0) / 10.0
                            + " blocks" + (solved[1] > 4.0 ? ", low confidence" : "") + ")"));
                }
            }
            // The pearl goes where you are looking right now.
            if (!MaceUtil.useItem(mc, Items.ENDER_PEARL, true)) {
                setEnabled(false);
                return;
            }
            countdown = wait;
            stage = Stage.WAIT;
            if (countdown > 0) {
                return;
            }
        }
        if (countdown-- > 0) {
            return;
        }
        throwWind(mc);
    }

    /** Throws the charge along the computed aim, then puts the camera back. */
    private void throwWind(net.minecraft.client.MinecraftClient mc) {
        var player = mc.player;
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        player.setYaw(windYaw);
        player.setPitch(windPitch);
        MaceUtil.sendLook(mc, windYaw, windPitch);
        MaceUtil.useItem(mc, Items.WIND_CHARGE, !swapToMace.getValue());
        player.setYaw(yaw);
        player.setPitch(pitch);
        if (swapToMace.getValue()) {
            int mace = MaceUtil.findBestMace(player);
            if (mace >= 0) {
                MaceUtil.selectNow(mc, mace);
            }
        }
        setEnabled(false);
    }

    /**
     * Simulates the pearl and a player launched by the charge at each possible
     * delay and returns {best delay in ticks, closest approach in blocks}.
     */
    private double[] solveDelay(Vec3d look, Vec3d pushDirection) {
        var mc = BytePhase.mc;
        var player = mc.player;
        Vec3d[] pearl = simulatePearl(look);

        double boost = launchBoost.getValue();
        // A sideways blast carries horizontally as well as upward.
        Vec3d launch = pushDirection.equals(Vec3d.ZERO)
                ? new Vec3d(0.0, boost, 0.0)
                : new Vec3d(pushDirection.x * 0.55 * boost, 0.85 * boost, pushDirection.z * 0.55 * boost);

        int bestDelay = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int delayTicks = 0; delayTicks <= MAX_DELAY; delayTicks++) {
            int explosion = delayTicks + WIND_FLIGHT_TICKS;
            Vec3d position = player.getEntityPos();
            Vec3d velocity = launch;
            double closest = Double.MAX_VALUE;
            for (int tick = explosion; tick < SIM_TICKS; tick++) {
                position = position.add(velocity);
                // Vanilla player physics in the air: gravity, then drag.
                velocity = new Vec3d(velocity.x * 0.91, (velocity.y - 0.08) * 0.98, velocity.z * 0.91);
                Vec3d body = position.add(0.0, 0.9, 0.0);
                closest = Math.min(closest, body.distanceTo(pearl[tick]));
                if (position.y < player.getY() - 1.0) {
                    break;
                }
            }
            // Strictly better by a margin, so equal paths keep the earlier (safer) delay.
            if (closest < bestDistance - 0.1) {
                bestDistance = closest;
                bestDelay = delayTicks;
            }
        }
        return new double[]{bestDelay, bestDistance};
    }

    /** Pearl position at every tick after it is thrown, stopping where it hits a block. */
    private Vec3d[] simulatePearl(Vec3d look) {
        var mc = BytePhase.mc;
        var player = mc.player;
        Vec3d position = player.getEyePos().subtract(0.0, 0.1, 0.0);
        // Thrown items inherit the thrower's motion (vertical only while airborne).
        Vec3d inherited = new Vec3d(player.getVelocity().x,
                player.isOnGround() ? 0.0 : player.getVelocity().y, player.getVelocity().z);
        Vec3d velocity = look.multiply(1.5).add(inherited);
        Vec3d[] path = new Vec3d[SIM_TICKS];
        boolean landed = false;
        for (int tick = 0; tick < SIM_TICKS; tick++) {
            if (!landed) {
                Vec3d next = position.add(velocity);
                var hit = mc.world.raycast(new RaycastContext(position, next,
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
                if (hit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
                    next = hit.getPos();
                    landed = true;
                }
                position = next;
                // Vanilla ender pearl: drag, then gravity.
                velocity = velocity.multiply(0.99).subtract(0.0, 0.03, 0.0);
            }
            path[tick] = position;
        }
        return path;
    }
}
