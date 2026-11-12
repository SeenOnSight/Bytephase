package dev.bytephase.util;

import net.minecraft.util.math.MathHelper;

/**
 * Eased aim movement shared by the combat modules.
 *
 * <p>A plain "move the yaw by N degrees per tick" rotation runs at a constant
 * speed and stops dead on the target, which reads as robotic. This smoother
 * keeps an angular velocity: it ramps up toward the requested turn speed, backs
 * off as the remaining angle shrinks, and never overshoots. With humanising
 * enabled it also adds a small wandering offset so the aim is not a perfectly
 * straight line.</p>
 */
public final class AimSmoother {
    private static final float SNAP = 0.02f;
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final float WOBBLE_YAW = 0.45f;
    private static final float WOBBLE_PITCH = 0.3f;

    private float yaw;
    private float pitch;
    private float yawVelocity;
    private float pitchVelocity;
    private float phase;
    private boolean initialized;

    public boolean isInitialized() {
        return initialized;
    }

    public void reset(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.yawVelocity = 0.0f;
        this.pitchVelocity = 0.0f;
        this.phase = (float) (Math.random() * Math.PI * 2.0);
        this.initialized = true;
    }

    public void reset() {
        this.yawVelocity = 0.0f;
        this.pitchVelocity = 0.0f;
        this.initialized = false;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    /**
     * Turns the aim toward the requested angles by at most {@code maxStep}
     * degrees this tick.
     *
     * @param smoothing 0 keeps a hard constant turn, 1 is the gentlest easing.
     */
    public void tick(float targetYaw, float targetPitch, float maxStep, float smoothing,
                     boolean humanize) {
        if (!initialized) {
            reset(targetYaw, targetPitch);
            return;
        }
        phase += 0.11f;
        if (phase > TWO_PI) phase -= TWO_PI;
        float wobbleYaw = humanize ? (float) Math.sin(phase) * WOBBLE_YAW : 0.0f;
        float wobblePitch = humanize ? (float) Math.sin(phase * 0.63f + 1.7f) * WOBBLE_PITCH : 0.0f;
        yaw = advance(yaw, targetYaw + wobbleYaw, maxStep, smoothing, true);
        pitch = advance(pitch, MathHelper.clamp(targetPitch + wobblePitch, -90.0f, 90.0f),
                maxStep, smoothing, false);
    }

    private float advance(float current, float target, float maxStep, float smoothing,
                          boolean wrap) {
        float delta = wrap ? MathHelper.wrapDegrees(target - current) : target - current;
        if (Math.abs(delta) <= SNAP) {
            if (wrap) yawVelocity = 0.0f;
            else pitchVelocity = 0.0f;
            // Equivalent yaw angles can differ by whole revolutions. Keep the
            // current revolution instead of turning a tiny correction into a
            // raw 360-degree packet jump at the snapping boundary.
            return wrap ? current + delta : target;
        }
        float velocity = wrap ? yawVelocity : pitchVelocity;
        velocity = nextVelocity(velocity, delta, maxStep, smoothing);
        float next = current + velocity;
        float remaining = wrap ? MathHelper.wrapDegrees(target - next) : target - next;
        if (Math.abs(remaining) <= SNAP) {
            if (wrap) yawVelocity = 0.0f;
            else pitchVelocity = 0.0f;
            return wrap ? next + remaining : target;
        }
        if (wrap) yawVelocity = velocity;
        else pitchVelocity = velocity;
        return next;
    }

    private static float nextVelocity(float velocity, float delta, float maxStep,
                                      float smoothing) {
        float easing = MathHelper.clamp(smoothing, 0.0f, 1.0f);
        float desired = MathHelper.clamp(delta * (1.0f - easing * 0.85f), -maxStep, maxStep);
        // Ramp the turn speed instead of jumping to it: the aim accelerates out
        // of rest and decelerates as it settles onto the target.
        float acceleration = Math.max(0.05f, maxStep / (1.0f + easing * 6.0f));
        float next = velocity + MathHelper.clamp(desired - velocity, -acceleration, acceleration);
        if (Math.abs(next) > Math.abs(delta)) {
            next = delta;
        }
        return next;
    }
}
