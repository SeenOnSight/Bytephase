package dev.bytephase.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Captures the world matrices once per frame.
 *
 * <p>1.21.2+ world rendering is camera-relative: the view matrix contains only
 * the camera rotation and geometry is emitted relative to the camera. The
 * world render pipelines apply that rotation themselves through the model view
 * matrix, so vertex emission must subtract {@code cameraPos} in double
 * precision and must not bake the rotation in, or geometry rotates twice.</p>
 */
public final class RenderProjection {
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final FrustumIntersection FRUSTUM = new FrustumIntersection();
    private static final Quaternionf CAMERA_ROTATION = new Quaternionf();
    private static final Quaternionf INVERSE_CAMERA_ROTATION = new Quaternionf();
    // project() is called once per visible entity by several HUD modules.
    // Reuse its scratch objects and result so dense scenes do not allocate
    // multiple objects per entity per frame.
    private static final Vector3f PROJECT_VIEW_POS = new Vector3f();
    private static final Vector4f PROJECT_CLIP_POS = new Vector4f();
    private static final double[] PROJECT_RESULT = new double[3];
    private static Vec3d cameraPos = Vec3d.ZERO;
    private static float tickDelta;

    private RenderProjection() {
    }

    public static Vec3d cameraPos() {
        return cameraPos;
    }

    public static Matrix4f projection(Matrix4f destination) {
        return destination.set(PROJECTION);
    }

    public static float verticalProjectionScale() {
        return PROJECTION.m11();
    }

    public static Matrix4f viewRotation(Matrix4f destination) {
        return destination.set(VIEW);
    }

    /** Projection multiplied by the camera-relative rotation, matching the sky shaders. */
    public static Matrix4f viewProjection(Matrix4f destination) {
        return destination.set(PROJECTION).mul(VIEW);
    }

    /**
     * Copies the camera basis into world space.
     *
     * <p>{@link #VIEW} maps world space to camera space, so its <em>rows</em>
     * are the camera's right/up/backward axes expressed in world space. Reading
     * columns instead transposes the basis and rotates reticles by the inverse
     * camera rotation.</p>
     */
    public static void cameraAxes(float[] right, float[] up, float[] forward) {
        right[0] = VIEW.m00();
        right[1] = VIEW.m01();
        right[2] = VIEW.m02();
        up[0] = VIEW.m10();
        up[1] = VIEW.m11();
        up[2] = VIEW.m12();
        forward[0] = -VIEW.m20();
        forward[1] = -VIEW.m21();
        forward[2] = -VIEW.m22();
    }

    public static void cameraForward(float[] forward) {
        forward[0] = -VIEW.m20();
        forward[1] = -VIEW.m21();
        forward[2] = -VIEW.m22();
    }

    public static float tickDelta() {
        return tickDelta;
    }

    /**
     * @param projection the perspective projection matrix
     * @param view       the camera-relative view (rotation) matrix
     */
    public static void onFrame(Matrix4f projection, Matrix4f view, Quaternionf cameraRotation,
                               Vec3d camera, float delta) {
        PROJECTION.set(projection);
        VIEW.set(view);
        // World geometry is camera-relative in 1.21.11. Keep a reusable
        // frustum intersection structure so render modules can reject boxes
        // before submitting their vertices without allocating a Frustum each
        // frame.
        PROJECTION.mul(VIEW, VIEW_PROJECTION);
        FRUSTUM.set(VIEW_PROJECTION);
        CAMERA_ROTATION.set(cameraRotation);
        INVERSE_CAMERA_ROTATION.set(cameraRotation).conjugate();
        cameraPos = camera;
        tickDelta = delta;
    }

    /** Returns whether a world-space box intersects the current camera view. */
    public static boolean isVisible(Box box) {
        float minX = (float) (box.minX - cameraPos.x);
        float minY = (float) (box.minY - cameraPos.y);
        float minZ = (float) (box.minZ - cameraPos.z);
        float maxX = (float) (box.maxX - cameraPos.x);
        float maxY = (float) (box.maxY - cameraPos.y);
        float maxZ = (float) (box.maxZ - cameraPos.z);
        return FRUSTUM.intersectAab(minX, minY, minZ, maxX, maxY, maxZ)
                != FrustumIntersection.OUTSIDE;
    }

    /** Returns whether a projected point is inside the scaled game viewport. */
    public static boolean isOnScreen(double[] projected, int width, int height) {
        return projected != null && projected[0] >= 0.0 && projected[0] < width
                && projected[1] >= 0.0 && projected[1] < height;
    }

    /**
     * Identity stack for vertices that have already been made camera-relative.
     *
     * <p>Callers must subtract {@link #cameraPos()} in {@code double} before
     * casting to {@code float}. Passing absolute world coordinates through a
     * translation matrix converts them to {@code float} first, which loses
     * metres of precision at far coordinates such as 2b2t's.</p>
     */
    public static MatrixStack cameraSpaceStack() {
        return new MatrixStack();
    }

    /**
     * Projects a world position to scaled GUI coordinates.
     *
     * @return null when the point is behind the camera.
     */
    public static double[] project(Vec3d worldPos) {
        // Camera.getRotation() maps camera-local axes into world space. Apply
        // its inverse to the world-relative point; the WorldRenderer view
        // matrix is tailored for the GPU pipeline and may include additional
        // camera transforms that must not be applied to a HUD projection.
        Vector3f viewPos = PROJECT_VIEW_POS.set(
                (float) (worldPos.x - cameraPos.x),
                (float) (worldPos.y - cameraPos.y),
                (float) (worldPos.z - cameraPos.z));
        INVERSE_CAMERA_ROTATION.transform(viewPos);
        float depth = -viewPos.z;
        if (depth <= 0.01f) {
            return null;
        }
        Vector4f pos = PROJECT_CLIP_POS.set(viewPos.x, viewPos.y, viewPos.z, 1.0f);
        PROJECTION.transform(pos);
        if (pos.w <= 0.0f) return null;
        float x = pos.x / pos.w;
        float y = pos.y / pos.w;
        MinecraftClient mc = MinecraftClient.getInstance();
        double gw = mc.getWindow().getScaledWidth();
        double gh = mc.getWindow().getScaledHeight();
        double sx = (x * 0.5 + 0.5) * gw;
        double sy = (0.5 - y * 0.5) * gh;
        PROJECT_RESULT[0] = sx;
        PROJECT_RESULT[1] = sy;
        PROJECT_RESULT[2] = depth;
        return PROJECT_RESULT;
    }
}
