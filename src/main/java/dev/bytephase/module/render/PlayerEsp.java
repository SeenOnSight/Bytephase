package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.EspRenderer;
import dev.bytephase.render.RenderProjection;
import dev.bytephase.render.EntitySnapshot;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

public final class PlayerEsp extends Module {
    public static final String MODEL_FILLED_STYLE = "Model Filled";
    public static final String MODEL_GLOW_STYLE = "Model Glow";
    public static final String MODEL_CHAMS_STYLE = "Model Chams";
    public static final String MODEL_WIREFRAME_STYLE = "Model Wireframe";
    public static final String MODEL_HOLOGRAM_STYLE = "Model Hologram";
    private static final String[] STYLES = java.util.Arrays.copyOf(EspRenderer.STYLES,
            EspRenderer.STYLES.length + 5);
    static {
        STYLES[STYLES.length - 1] = MODEL_GLOW_STYLE;
        STYLES[STYLES.length - 2] = MODEL_CHAMS_STYLE;
        STYLES[STYLES.length - 3] = MODEL_WIREFRAME_STYLE;
        STYLES[STYLES.length - 4] = MODEL_HOLOGRAM_STYLE;
        STYLES[STYLES.length - 5] = MODEL_FILLED_STYLE;
    }

    private final ModeSetting targets = add(new ModeSetting("Targets",
            EspRenderer.TARGETS, "Players + Monsters"));
    private final ModeSetting style = add(new ModeSetting("Style",
            STYLES, MODEL_WIREFRAME_STYLE));
    private final BooleanSetting throughWalls = add(new BooleanSetting("ThroughWalls", true));
    private final BooleanSetting heldItems = add(new BooleanSetting("FilledHeldItems", false));
    private final BooleanSetting endCrystals = add(new BooleanSetting("FilledCrystals", false));
    private final NumberSetting modelOpacity = add(new NumberSetting("ModelOpacity", 45.0, 1.0, 100.0, 1.0));
    private final ColorSetting playerColor = add(new ColorSetting("PlayerColor", 0xFF6466E9));
    private final ColorSetting monsterColor = add(new ColorSetting("MonsterColor", 0xFF6466E9));
    private final ColorSetting otherColor = add(new ColorSetting("OtherColor", 0xFF6466E9));

    private final List<Box> playerBoxes = new ArrayList<>();
    private final List<Box> monsterBoxes = new ArrayList<>();
    private final List<Box> otherBoxes = new ArrayList<>();
    private final List<LivingEntity> renderTargets = new ArrayList<>();

    public PlayerEsp() {
        super("ESP", Category.RENDER, "Boxes around players and hostiles");
    }

    @Override
    public boolean usesEntitySnapshot() {
        return true;
    }

    /** Whether a selected model target should bypass hidden-section culling. */
    public boolean shouldRenderHiddenModel(Entity entity) {
        var mc = BytePhase.mc;
        if (!isEnabled() || !isModelStyle() || !throughWalls.getValue()
                || mc == null || mc.player == null || mc.world == null
                || entity == mc.player || !entity.isAlive() || entity.isRemoved()) return false;
        double range = mc.options.getClampedViewDistance() * 16.0;
        if (entity.squaredDistanceTo(mc.player) > range * range) return false;
        if (entity instanceof LivingEntity) return EspRenderer.matchesTarget(entity, targets.getValue());
        return isModelFilledStyle() && endCrystals.getValue() && entity.getType() == EntityType.END_CRYSTAL;
    }

    /** Whether vanilla should put this entity's model into the outline pass. */
    public boolean shouldModelGlow(LivingEntity entity) {
        var mc = BytePhase.mc;
        if (!isEnabled() || !isModelStyle() || isModelFilledStyle()
                || mc == null || mc.player == null || mc.world == null
                || entity == mc.player || !entity.isAlive() || entity.isRemoved()) return false;
        double range = mc.options.getClampedViewDistance() * 16.0;
        return entity.squaredDistanceTo(mc.player) <= range * range
                && EspRenderer.matchesTarget(entity, targets.getValue());
    }

    /** Selects a model render state for the translucent model-fill style. */
    public boolean shouldModelChams(EntityRenderState state) {
        var mc = BytePhase.mc;
        if (!isEnabled() || !(MODEL_CHAMS_STYLE.equals(style.getValue()) || isModelWireframeStyle()
                || isModelHologramStyle() || isModelFilledStyle())
                || mc == null || mc.player == null || mc.world == null
                || state == null || state.entityType == null) {
            return false;
        }
        if (state instanceof net.minecraft.client.render.entity.state.PlayerEntityRenderState playerState
                && playerState.id == mc.player.getId()) return false;
        double renderRange = mc.options.getClampedViewDistance() * 16.0;
        if (state.squaredDistanceToCamera > renderRange * renderRange) return false;
        boolean player = state.entityType == EntityType.PLAYER;
        boolean monster = state.entityType.getSpawnGroup() == net.minecraft.entity.SpawnGroup.MONSTER;
        return switch (targets.getValue()) {
            case "Players" -> player;
            case "Monsters" -> monster && !player;
            case "Players + Monsters" -> player || (monster && !player);
            default -> true;
        };
    }

    /** Tint used for the translucent surface; the outline remains fully opaque. */
    public int modelChamsTint(EntityRenderState state) {
        if (state != null && state.entityType == EntityType.PLAYER) {
            return 0xB0000000 | playerColor.rgb();
        }
        if (state != null && state.entityType != null
                && state.entityType.getSpawnGroup() == net.minecraft.entity.SpawnGroup.MONSTER) {
            return 0xB0000000 | monsterColor.rgb();
        }
        return 0xB0000000 | otherColor.rgb();
    }

    public boolean isModelFilledStyle() {
        return isEnabled() && MODEL_FILLED_STYLE.equals(style.getValue());
    }

    public boolean fillHeldItems(EntityRenderState state) {
        return heldItems.getValue() && isModelFilledStyle() && shouldModelChams(state);
    }

    public boolean fillOwnHeldItems() {
        return heldItems.getValue() && isModelFilledStyle();
    }

    public boolean fillCrystal(EntityRenderState state) {
        var mc = BytePhase.mc;
        if (!endCrystals.getValue() || !isModelFilledStyle() || mc == null || mc.player == null
                || mc.world == null || state == null) return false;
        double range = mc.options.getClampedViewDistance() * 16.0;
        return state.squaredDistanceToCamera <= range * range;
    }

    public int modelFilledTint(EntityRenderState state) {
        int rgb = modelChamsTint(state) & 0xFFFFFF;
        int alpha = (int) Math.round(modelOpacity.getValue() * 255.0 / 100.0);
        return alpha << 24 | rgb;
    }

    public int modelWireframeTint(EntityRenderState state) {
        if (state != null && state.entityType == EntityType.PLAYER) return 0xF0000000 | playerColor.rgb();
        if (state != null && state.entityType != null
                && state.entityType.getSpawnGroup() == net.minecraft.entity.SpawnGroup.MONSTER) {
            return 0xF0000000 | monsterColor.rgb();
        }
        return 0xF0000000 | otherColor.rgb();
    }

    public int modelHologramFillTint(EntityRenderState state) {
        int color = modelWireframeTint(state);
        return 0x68000000 | (color & 0xFFFFFF);
    }

    public boolean isModelHologramStyle() {
        return isEnabled() && MODEL_HOLOGRAM_STYLE.equals(style.getValue());
    }

    public boolean throughWalls() {
        return throughWalls.getValue();
    }

    private boolean isModelStyle() {
        return MODEL_FILLED_STYLE.equals(style.getValue()) || MODEL_GLOW_STYLE.equals(style.getValue()) || MODEL_CHAMS_STYLE.equals(style.getValue())
                || MODEL_WIREFRAME_STYLE.equals(style.getValue()) || MODEL_HOLOGRAM_STYLE.equals(style.getValue());
    }

    public boolean isModelWireframeStyle() {
        return isEnabled() && MODEL_WIREFRAME_STYLE.equals(style.getValue());
    }

    /** Opaque color consumed by Minecraft's native entity-outline mask. */
    public int modelGlowColor(LivingEntity entity) {
        if (entity instanceof PlayerEntity) return 0xFF000000 | playerColor.rgb();
        if (EspRenderer.isMonster(entity)) return 0xFF000000 | monsterColor.rgb();
        return 0xFF000000 | otherColor.rgb();
    }

    @Override
    public void onTick() {
        renderTargets.clear();
        // Native model glow is selected directly from EntityRenderState in
        // EntityRendererMixin. It has no per-tick box target list to build.
        if (isModelStyle()) {
            return;
        }
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) return;
        double renderRange = mc.options.getClampedViewDistance() * 16.0;
        double rangeSq = renderRange * renderRange;
        String targetMode = targets.getValue();
        for (LivingEntity entity : EntitySnapshot.livingEntities()) {
            if (entity != mc.player && entity.squaredDistanceTo(mc.player) <= rangeSq
                    && EspRenderer.matchesTarget(entity, targetMode)) {
                renderTargets.add(entity);
            }
        }
    }

    @Override
    public void onRender3D(MatrixStack matrices,
                           net.minecraft.client.render.VertexConsumerProvider consumers,
                           float tickDelta) {
        if (isModelStyle()) {
            // The models themselves are submitted by vanilla into its outline
            // framebuffer; drawing an AABB here would defeat the silhouette.
            return;
        }
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        playerBoxes.clear();
        monsterBoxes.clear();
        otherBoxes.clear();

        float delta = RenderProjection.tickDelta();
        for (LivingEntity entity : renderTargets) {
            if (!entity.isAlive() || entity.isRemoved()) {
                continue;
            }
            // Build the AABB from the entity's current pose dimensions at its
            // interpolated render position. This is the same interpolation
            // vanilla uses for EntityRenderState.x/y/z, and avoids coupling
            // the visual box to getBoundingBox() (which HitBoxes expands).
            // The 2 cm margin only stops filled faces z-fighting with entity
            // models that touch their own AABB.
            Box box = entity.getDimensions(entity.getPose())
                    .getBoxAt(entity.getLerpedPos(delta))
                    .expand(0.02);
            if (entity instanceof PlayerEntity) {
                playerBoxes.add(box);
            } else if (EspRenderer.isMonster(entity)) {
                monsterBoxes.add(box);
            } else {
                otherBoxes.add(box);
            }
        }

        boolean walls = throughWalls.getValue();
        EspRenderer.drawBoxes(playerBoxes, playerColor.getValue(), style.getValue(), walls);
        EspRenderer.drawBoxes(monsterBoxes, monsterColor.getValue(), style.getValue(), walls);
        EspRenderer.drawBoxes(otherBoxes, otherColor.getValue(), style.getValue(), walls);
    }
}
