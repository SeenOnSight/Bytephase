package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.RenderBox;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.NumberSetting;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Draws the path you have walked behind you.
 *
 * <p>A point is recorded once you move further than {@code Spacing} from the
 * last one, so the trail is independent of frame rate and tick rate and long
 * sessions do not grow without bound: entries older than {@code Lifetime} and
 * anything past {@code MaxPoints} are dropped. The trail is cleared on a world
 * change, because coordinates from another dimension would draw through
 * unrelated terrain.</p>
 */
public final class BreadCrumbs extends Module {
    private static final class Point {
        final Vec3d pos;
        final long time;

        Point(Vec3d pos, long time) {
            this.pos = pos;
            this.time = time;
        }
    }

    private final NumberSetting lifetime = add(new NumberSetting("Lifetime", 20.0, 1.0, 120.0, 1.0));
    private final NumberSetting maxPoints = add(new NumberSetting("MaxPoints", 200.0, 20.0, 2000.0, 20.0));
    private final NumberSetting spacing = add(new NumberSetting("Spacing", 0.25, 0.05, 2.0, 0.05));
    private final BooleanSetting throughWalls = add(new BooleanSetting("ThroughWalls", true));
    private final ColorSetting color = add(new ColorSetting("Color", 0xFF6466E9));

    private final List<Point> points = new ArrayList<>();
    private final List<Vec3d> path = new ArrayList<>();
    private World activeWorld;

    public BreadCrumbs() {
        super("BreadCrumbs", Category.RENDER, "Draws your walked path behind you");
    }

    @Override
    protected void onEnable() {
        clear();
        activeWorld = BytePhase.mc == null ? null : BytePhase.mc.world;
    }

    @Override
    protected void onDisable() {
        clear();
        activeWorld = null;
    }

    private void clear() {
        points.clear();
        path.clear();
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            clear();
            return;
        }
        if (mc.world != activeWorld) {
            clear();
            activeWorld = mc.world;
            return;
        }
        long now = System.currentTimeMillis();
        Vec3d feet = mc.player.getEntityPos();
        if (points.isEmpty() || points.getLast().pos.squaredDistanceTo(feet)
                >= spacing.getValue() * spacing.getValue()) {
            points.add(new Point(feet, now));
        }

        long limit = (long) (lifetime.getValue() * 1000.0);
        Iterator<Point> iterator = points.iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().time > limit) {
                iterator.remove();
            } else {
                break;
            }
        }
        int cap = maxPoints.asInt();
        while (points.size() > cap) {
            points.removeFirst();
        }
        // Points only change here, so rebuild the draw list once per tick
        // instead of copying up to 2,000 entries every rendered frame.
        path.clear();
        for (Point point : points) {
            path.add(point.pos);
        }
    }

    @Override
    public void onRender3D(MatrixStack matrices,
                           net.minecraft.client.render.VertexConsumerProvider consumers,
                           float tickDelta) {
        if (path.size() < 2) {
            return;
        }
        RenderBox.drawLineStrip(path, color.getValue(), throughWalls.getValue());
    }
}
