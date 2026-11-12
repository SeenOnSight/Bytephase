package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.EntitySnapshot;
import dev.bytephase.render.EspRenderer;
import dev.bytephase.render.RenderBox;
import dev.bytephase.render.RenderProjection;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Highlights dropped items, with optional names and tracers.
 *
 * <p>The item list and label strings are built once per tick; only the
 * interpolation boxes are refreshed per frame. This keeps the common case of
 * a busy item pile from repeating name lookups every frame.</p>
 */
public final class ItemEsp extends Module {
    private static final int MAX_ITEMS = 128;

    private static final class Entry {
        final ItemEntity entity;
        final String label;

        Entry(ItemEntity entity, String label) {
            this.entity = entity;
            this.label = label;
        }
    }

    private final NumberSetting range = add(new NumberSetting("Range", 64.0, 8.0, 256.0, 1.0));
    private final BooleanSetting names = add(new BooleanSetting("Names", true));
    private final BooleanSetting tracers = add(new BooleanSetting("Tracers", false));
    private final ModeSetting style = add(new ModeSetting("Style",
            EspRenderer.STYLES, "Highlight"));
    private final BooleanSetting throughWalls = add(new BooleanSetting("ThroughWalls", true));
    private final ColorSetting color = add(new ColorSetting("Color", 0xFF6466E9));

    private final List<Entry> entries = new ArrayList<>();
    private final List<Box> boxes = new ArrayList<>();
    private final List<Vec3d> centers = new ArrayList<>();

    public ItemEsp() {
        super("ItemESP", Category.RENDER, "Highlights dropped items");
    }

    @Override
    public boolean usesEntitySnapshot() {
        return true;
    }

    @Override
    public boolean usesProjection() {
        return true;
    }

    @Override
    protected void onEnable() {
        entries.clear();
        boxes.clear();
        centers.clear();
    }

    @Override
    protected void onDisable() {
        entries.clear();
        boxes.clear();
        centers.clear();
    }

    @Override
    public void onTick() {
        entries.clear();
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) {
            return;
        }
        double rangeSq = range.getValue() * range.getValue();
        for (ItemEntity item : EntitySnapshot.itemEntities()) {
            if (item.isRemoved() || item.getStack().isEmpty()
                    || item.squaredDistanceTo(mc.player) > rangeSq) {
                continue;
            }
            String label = names.getValue()
                    ? item.getStack().getName().getString() + " x" + item.getStack().getCount()
                    : null;
            entries.add(new Entry(item, label));
            if (entries.size() >= MAX_ITEMS) {
                return;
            }
        }
    }

    @Override
    public void onRender3D(MatrixStack matrices,
                           net.minecraft.client.render.VertexConsumerProvider consumers,
                           float tickDelta) {
        if (entries.isEmpty()) {
            return;
        }
        boxes.clear();
        centers.clear();
        float delta = RenderProjection.tickDelta();
        boolean drawTracers = tracers.getValue();
        for (Entry entry : entries) {
            ItemEntity item = entry.entity;
            if (item.isRemoved() || item.getStack().isEmpty()) {
                continue;
            }
            Box box = item.getBoundingBox()
                    .offset(item.getLerpedPos(delta).subtract(item.getEntityPos()))
                    .expand(0.05);
            boxes.add(box);
            if (drawTracers) {
                centers.add(box.getCenter());
            }
        }
        EspRenderer.drawBoxes(boxes, color.getValue(), style.getValue(), throughWalls.getValue());
        if (drawTracers) {
            RenderBox.drawLinesFrom(RenderBox.tracerOrigin(), centers,
                    EspRenderer.lineColor(color.rgb(), 0x80), throughWalls.getValue());
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!names.getValue() || entries.isEmpty()) {
            return;
        }
        var mc = BytePhase.mc;
        if (mc == null) {
            return;
        }
        float delta = RenderProjection.tickDelta();
        int labelColor = 0xFF000000 | color.rgb();
        for (Entry entry : entries) {
            ItemEntity item = entry.entity;
            if (item.isRemoved() || item.getStack().isEmpty() || entry.label == null) {
                continue;
            }
            Vec3d above = item.getLerpedPos(delta).add(0, item.getHeight() + 0.3, 0);
            double[] projected = RenderProjection.project(above);
            if (!RenderProjection.isOnScreen(projected, mc.getWindow().getScaledWidth(),
                    mc.getWindow().getScaledHeight())) {
                continue;
            }
            int textWidth = mc.textRenderer.getWidth(entry.label);
            int x = (int) (projected[0] - textWidth / 2.0);
            int y = (int) projected[1];
            context.fill(x - 2, y - 1, x + textWidth + 2, y + 10, 0x90000000);
            context.drawTextWithShadow(mc.textRenderer, entry.label, x, y, labelColor);
        }
    }
}
