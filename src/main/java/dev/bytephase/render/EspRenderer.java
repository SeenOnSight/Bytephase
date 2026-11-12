package dev.bytephase.render;

import dev.bytephase.render.esp.ClarityEspRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared ESP target filters and box styles.
 *
 * <p>Every entity ESP uses the same target mode strings and the same colour
 * handling. The plain styles use the existing batched {@link RenderBox}
 * primitives. The {@code Shader*} styles queue the same boxes for the
 * supplied Clarity post-processing shaders, which are flushed once per frame
 * after the box batches have been submitted.</p>
 */
public final class EspRenderer {
    private static final List<Box> VISIBLE_BOXES = new ArrayList<>();
    public static final String[] STYLES = {
            "Box", "Filled", "Highlight", "Shader Glow", "Shader Blur",
            "Shader Galaxy", "Shader Prism", "Shader Neon", "Shader Scan"
    };
    public static final String[] TARGETS = {"Players", "Monsters", "Players + Monsters", "All"};

    private EspRenderer() {
    }

    /** Whether this frame queued any box for the shader renderer. */
    public static boolean hasQueuedShader() {
        return ClarityEspRenderer.hasQueued();
    }

    public static void clearQueuedShader() {
        ClarityEspRenderer.clearQueued();
    }

    public static boolean isPlayer(Entity entity) {
        return entity instanceof PlayerEntity;
    }

    public static boolean isMonster(Entity entity) {
        return entity != null && entity.getType().getSpawnGroup() == SpawnGroup.MONSTER;
    }

    /**
     * @return whether a living/entity target is included by the selected mode.
     */
    public static boolean matchesTarget(Entity entity, String mode) {
        if (entity == null || !entity.isAlive() || entity.isRemoved()) {
            return false;
        }
        boolean player = isPlayer(entity);
        boolean monster = isMonster(entity);
        return switch (mode) {
            case "Players" -> player;
            case "Monsters" -> monster;
            case "Players + Monsters" -> player || monster;
            default -> true;
        };
    }

    public static void drawBox(Box box, int argb, String style) {
        drawBoxes(java.util.Collections.singletonList(box), argb, style, true);
    }

    public static void drawBoxes(Collection<Box> boxes, int argb, String style) {
        drawBoxes(boxes, argb, style, true);
    }

    /**
     * Queues the selected style for all boxes. All fills and outlines are
     * batched by {@link RenderBox}; this method only issues the minimum
     * number of layer writes for the chosen style.
     *
     * @param throughWalls render with the no-depth overlay layers so terrain
     *                     does not hide the boxes
     */
    public static void drawBoxes(Collection<Box> boxes, int argb, String style, boolean throughWalls) {
        if (boxes.isEmpty()) {
            return;
        }
        // Every box ESP funnels through this method. Cull off-camera geometry
        // once here so block, storage, chunk, item, and entity ESP all avoid
        // uploading vertices that cannot contribute to the image. The shared
        // scratch list is safe because these calls are sequential on the render
        // thread; shader batches copy their Box references before returning.
        VISIBLE_BOXES.clear();
        for (Box box : boxes) {
            if (RenderProjection.isVisible(box)) {
                VISIBLE_BOXES.add(box);
            }
        }
        if (VISIBLE_BOXES.isEmpty()) {
            return;
        }
        int rgb = argb & 0xFFFFFF;
        int alpha = (argb >>> 24) & 0xFF;
        int outline = withAlpha(rgb, Math.max(alpha, 210));
        int fill = withAlpha(rgb, clamp(20, (alpha * 48) / 255, 72));
        String selected = style == null ? "Highlight" : style;
        switch (selected) {
            case "Box" -> RenderBox.drawOutlineBoxes(VISIBLE_BOXES, outline, throughWalls);
            case "Filled" -> RenderBox.drawFilledBoxes(VISIBLE_BOXES, fill, throughWalls);
            case "Highlight" -> {
                RenderBox.drawFilledBoxes(VISIBLE_BOXES, fill, throughWalls);
                RenderBox.drawOutlineBoxes(VISIBLE_BOXES, outline, throughWalls);
            }
            default -> {
                // Shader styles use the same batched world-space boxes as
                // their mask input, then composite once at the end of the frame.
                ClarityEspRenderer.queue(VISIBLE_BOXES, argb, selected, throughWalls);
            }
        }
    }

    public static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    public static int lineColor(int rgb, int alpha) {
        return withAlpha(rgb, alpha);
    }

    private static int clamp(int min, int value, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
