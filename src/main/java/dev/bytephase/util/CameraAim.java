package dev.bytephase.util;

import dev.bytephase.BytePhase;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Per-frame, exponentially damped aim steering.
 *
 * <p>Rotating the player once per tick makes the camera jump in twenty
 * 50&nbsp;ms steps, which reads as choppy no matter how small the steps are.
 * Mouse look does not work that way: it is applied every rendered frame, so it
 * stays smooth at any frame rate. This class does the same thing for aim
 * assistance. Modules publish a target angle each tick, and every frame the
 * player's yaw and pitch are moved a fraction of the remaining angle given by
 * {@code 1 - exp(-rate * dt)}.</p>
 *
 * <p>That exponential form is what makes the motion frame-rate independent and
 * free of overshoot: the same time produces the same progress whatever the FPS,
 * and the step shrinks as the angle closes instead of arriving at full speed and
 * stopping dead. Because the delta is measured from the player's live angles,
 * your own mouse movement during the assist is never fought or reverted.</p>
 */
public final class CameraAim {
    /** Never take a frame longer than this into account (pause, lag spike). */
    private static final double MAX_FRAME_SECONDS = 0.1;
    private static final float WOBBLE_YAW = 0.4f;
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final float WOBBLE_PITCH = 0.25f;

    private static boolean active;
    private static float targetYaw;
    private static float targetPitch;
    private static float maxDegreesPerTick;
    private static float smoothing;
    private static boolean humanize;
    private static float phase;
    private static long lastFrameNanos;

    private CameraAim() {
    }

    /** Publishes the angle the aim should settle on. */
    public static void aim(float yaw, float pitch, float maxDegreesPerTick, float smoothing,
                           boolean humanize) {
        CameraAim.active = true;
        CameraAim.targetYaw = yaw;
        CameraAim.targetPitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        CameraAim.maxDegreesPerTick = Math.max(0.5f, maxDegreesPerTick);
        CameraAim.smoothing = MathHelper.clamp(smoothing, 0.0f, 1.0f);
        CameraAim.humanize = humanize;
    }

    /** Stops steering; the camera stays wherever the last frame left it. */
    public static void release() {
        active = false;
        lastFrameNanos = 0L;
    }

    public static boolean isActive() {
        return active;
    }

    /** Called once per rendered frame, after the mouse has been applied. */
    public static void frame(ClientPlayerEntity player) {
        if (!active || player == null) {
            return;
        }
        var mc = BytePhase.mc;
        if (mc == null || mc.currentScreen != null || player.isDead() || player.isSpectator()) {
            lastFrameNanos = 0L;
            return;
        }
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 1.0 / 60.0 : (now - lastFrameNanos) / 1.0e9;
        lastFrameNanos = now;
        frame(player, dt);
    }

    /**
     * Advances the aim by one frame of {@code dt} seconds. Exposed separately so
     * the motion can be exercised without depending on wall-clock timing.
     */
    public static void frame(ClientPlayerEntity player, double dt) {
        if (!active || player == null) {
            return;
        }
        double step = Math.min(MAX_FRAME_SECONDS, Math.max(0.0, dt));
        if (step <= 0.0) {
            return;
        }
        // 3/s is a very gentle drift, 33/s is snappy. Smoothing picks a rate in
        // between, so the value always means "how long the assist takes".
        double rate = 3.0 + (1.0 - smoothing) * 30.0;
        float alpha = (float) (1.0 - Math.exp(-rate * step));
        phase += (float) (step * 7.0);
        if (phase > TWO_PI) phase %= TWO_PI;

        float yawTarget = targetYaw;
        float pitchTarget = targetPitch;
        if (humanize) {
            yawTarget += (float) Math.sin(phase) * WOBBLE_YAW;
            pitchTarget += (float) Math.sin(phase * 0.63f + 1.7f) * WOBBLE_PITCH;
        }

        // A tick's worth of budget is spread over the frames in that tick, so
        // the configured speed keeps its meaning.
        float maxStep = maxDegreesPerTick * 20.0f * (float) step;
        float yawDelta = MathHelper.wrapDegrees(yawTarget - player.getYaw()) * alpha;
        float pitchDelta = (pitchTarget - player.getPitch()) * alpha;
        yawDelta = MathHelper.clamp(yawDelta, -maxStep, maxStep);
        pitchDelta = MathHelper.clamp(pitchDelta, -maxStep, maxStep);

        if (Math.abs(yawDelta) > 1.0e-4f) {
            player.setYaw(player.getYaw() + yawDelta);
        }
        if (Math.abs(pitchDelta) > 1.0e-4f) {
            player.setPitch(MathHelper.clamp(player.getPitch() + pitchDelta, -90.0f, 90.0f));
        }
    }
}
