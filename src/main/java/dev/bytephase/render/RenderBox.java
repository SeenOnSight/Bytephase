package dev.bytephase.render;

import java.util.Collection;
import java.util.List;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Shared world-space box and line drawing through Minecraft's world buffers. */
public final class RenderBox {
    private static MatrixStack matrices;
    private static VertexConsumerProvider consumers;

    private RenderBox() { }

    /** Called from Fabric's END_MAIN event with its camera-relative render context. */
    public static void beginFrame(MatrixStack frameMatrices, VertexConsumerProvider frameConsumers) {
        matrices = frameMatrices;
        consumers = frameConsumers;
    }

    public static void endFrame() {
        matrices = null;
        consumers = null;
    }

    public static void clearFrame() {
        // Geometry is submitted to Minecraft's world buffers, not retained for the HUD pass.
    }

    /**
     * Unprojects the crosshair to a point inside the view volume. This avoids
     * the camera-plane w=0 and stays centered through view bob and hurt tilt.
     */
    public static Vec3d tracerOrigin() {
        return RenderProjection.crosshairOrigin();
    }

    public static void drawFilledBox(Box box, int argb) {
        if (!ready()) return;
        VertexConsumer buffer = consumers.getBuffer(OverlayLayers.filled());
        int a = argb >>> 24 & 255, r = argb >>> 16 & 255, g = argb >>> 8 & 255, b = argb & 255;
        filledQuads(buffer, matrices.peek(), RenderProjection.cameraPos(), box, r, g, b, a);
    }

    public static void drawFilledBoxes(Collection<Box> boxes, int argb) {
        drawFilledBoxes(boxes, argb, true);
    }

    public static void drawFilledBoxes(Collection<Box> boxes, int argb, boolean throughWalls) {
        if (!ready() || boxes.isEmpty()) return;
        RenderLayer layer = throughWalls ? OverlayLayers.filled() : RenderLayers.debugFilledBox();
        VertexConsumer buffer = consumers.getBuffer(layer);
        MatrixStack.Entry entry = matrices.peek();
        int a = argb >>> 24 & 255, r = argb >>> 16 & 255, g = argb >>> 8 & 255, b = argb & 255;
        Vec3d camera = RenderProjection.cameraPos();
        for (Box box : boxes) filledQuads(buffer, entry, camera, box, r, g, b, a);
    }

    public static void drawOutlineBox(Box box, int argb) {
        drawOutlineBox(box, argb, true);
    }

    public static void drawOutlineBox(Box box, int argb, boolean throughWalls) {
        if (!ready()) return;
        VertexConsumer buffer = consumers.getBuffer(throughWalls ? OverlayLayers.lines() : RenderLayers.lines());
        outlineLines(buffer, matrices.peek(), RenderProjection.cameraPos(), box, argb);
    }

    public static void drawOutlineBoxes(Collection<Box> boxes, int argb) {
        drawOutlineBoxes(boxes, argb, true);
    }

    public static void drawOutlineBoxes(Collection<Box> boxes, int argb, boolean throughWalls) {
        if (!ready() || boxes.isEmpty()) return;
        VertexConsumer buffer = consumers.getBuffer(throughWalls ? OverlayLayers.lines() : RenderLayers.lines());
        MatrixStack.Entry entry = matrices.peek();
        Vec3d camera = RenderProjection.cameraPos();
        for (Box box : boxes) outlineLines(buffer, entry, camera, box, argb);
    }

    public static void drawLine(Vec3d from, Vec3d to, int argb) {
        drawLine(from, to, argb, true);
    }

    public static void drawLine(Vec3d from, Vec3d to, int argb, boolean throughWalls) {
        if (!ready()) return;
        line(consumers.getBuffer(throughWalls ? OverlayLayers.lines() : RenderLayers.lines()),
                matrices.peek(), RenderProjection.cameraPos(), from, to, argb);
    }

    public static void drawLineStrip(List<Vec3d> points, int argb) {
        drawLineStrip(points, argb, true);
    }

    public static void drawLineStrip(List<Vec3d> points, int argb, boolean throughWalls) {
        if (!ready() || points.size() < 2) return;
        VertexConsumer buffer = consumers.getBuffer(throughWalls ? OverlayLayers.lines() : RenderLayers.lines());
        MatrixStack.Entry entry = matrices.peek();
        Vec3d camera = RenderProjection.cameraPos();
        for (int i = 0; i + 1 < points.size(); i++) {
            line(buffer, entry, camera, points.get(i), points.get(i + 1), argb);
        }
    }

    public static void drawLinesFrom(Vec3d origin, Collection<Vec3d> targets, int argb) {
        drawLinesFrom(origin, targets, argb, true);
    }

    public static void drawLinesFrom(Vec3d origin, Collection<Vec3d> targets,
                                     int argb, boolean throughWalls) {
        if (!ready() || targets.isEmpty()) return;
        VertexConsumer buffer = consumers.getBuffer(throughWalls ? OverlayLayers.lines() : RenderLayers.lines());
        MatrixStack.Entry entry = matrices.peek();
        Vec3d camera = RenderProjection.cameraPos();
        for (Vec3d target : targets) line(buffer, entry, camera, origin, target, argb);
    }

    private static boolean ready() {
        return matrices != null && consumers != null;
    }

    private static void filledQuads(VertexConsumer buffer, MatrixStack.Entry entry, Vec3d camera,
                                    Box box, int r, int g, int b, int a) {
        float x1 = (float) (box.minX - camera.x), y1 = (float) (box.minY - camera.y), z1 = (float) (box.minZ - camera.z);
        float x2 = (float) (box.maxX - camera.x), y2 = (float) (box.maxY - camera.y), z2 = (float) (box.maxZ - camera.z);
        quad(buffer, entry, x1,y1,z1, x1,y2,z1, x2,y2,z1, x2,y1,z1, r,g,b,a);
        quad(buffer, entry, x2,y1,z2, x2,y2,z2, x1,y2,z2, x1,y1,z2, r,g,b,a);
        quad(buffer, entry, x2,y1,z1, x2,y2,z1, x2,y2,z2, x2,y1,z2, r,g,b,a);
        quad(buffer, entry, x1,y1,z2, x1,y2,z2, x1,y2,z1, x1,y1,z1, r,g,b,a);
        quad(buffer, entry, x1,y1,z2, x1,y1,z1, x2,y1,z1, x2,y1,z2, r,g,b,a);
        quad(buffer, entry, x1,y2,z1, x1,y2,z2, x2,y2,z2, x2,y2,z1, r,g,b,a);
    }

    private static void quad(VertexConsumer buffer, MatrixStack.Entry entry,
                             float x1,float y1,float z1, float x2,float y2,float z2,
                             float x3,float y3,float z3, float x4,float y4,float z4,
                             int r,int g,int b,int a) {
        buffer.vertex(entry,x1,y1,z1).color(r,g,b,a);
        buffer.vertex(entry,x2,y2,z2).color(r,g,b,a);
        buffer.vertex(entry,x3,y3,z3).color(r,g,b,a);
        buffer.vertex(entry,x4,y4,z4).color(r,g,b,a);
    }

    private static void outlineLines(VertexConsumer buffer, MatrixStack.Entry entry,
                                     Vec3d camera, Box box, int argb) {
        float x1 = (float) (box.minX - camera.x), y1 = (float) (box.minY - camera.y), z1 = (float) (box.minZ - camera.z);
        float x2 = (float) (box.maxX - camera.x), y2 = (float) (box.maxY - camera.y), z2 = (float) (box.maxZ - camera.z);
        line(buffer, entry, x1,y1,z1, x2,y1,z1, argb);
        line(buffer, entry, x2,y1,z1, x2,y1,z2, argb);
        line(buffer, entry, x2,y1,z2, x1,y1,z2, argb);
        line(buffer, entry, x1,y1,z2, x1,y1,z1, argb);
        line(buffer, entry, x1,y2,z1, x2,y2,z1, argb);
        line(buffer, entry, x2,y2,z1, x2,y2,z2, argb);
        line(buffer, entry, x2,y2,z2, x1,y2,z2, argb);
        line(buffer, entry, x1,y2,z2, x1,y2,z1, argb);
        line(buffer, entry, x1,y1,z1, x1,y2,z1, argb);
        line(buffer, entry, x2,y1,z1, x2,y2,z1, argb);
        line(buffer, entry, x2,y1,z2, x2,y2,z2, argb);
        line(buffer, entry, x1,y1,z2, x1,y2,z2, argb);
    }

    private static void line(VertexConsumer buffer, MatrixStack.Entry entry, Vec3d camera,
                             Vec3d from, Vec3d to, int argb) {
        line(buffer, entry,
                (float) (from.x - camera.x), (float) (from.y - camera.y), (float) (from.z - camera.z),
                (float) (to.x - camera.x), (float) (to.y - camera.y), (float) (to.z - camera.z), argb);
    }

    private static void line(VertexConsumer buffer, MatrixStack.Entry entry,
                             float x1, float y1, float z1, float x2, float y2, float z2, int argb) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-6f) return;
        float nx = dx / length, ny = dy / length, nz = dz / length;
        int r = argb >>> 16 & 255, g = argb >>> 8 & 255, b = argb & 255, a = argb >>> 24 & 255;
        buffer.vertex(entry, x1,y1,z1).color(r,g,b,a).normal(entry,nx,ny,nz).lineWidth(1.5f);
        buffer.vertex(entry, x2,y2,z2).color(r,g,b,a).normal(entry,nx,ny,nz).lineWidth(1.5f);
    }
}
