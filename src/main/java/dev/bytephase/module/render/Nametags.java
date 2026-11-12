package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.EspRenderer;
import dev.bytephase.render.EntitySnapshot;
import dev.bytephase.render.Render2D;
import dev.bytephase.render.RenderProjection;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Projected nametags.
 *
 * <p>Names are drawn with a rounded background, a health bar and an optional
 * distance readout. Target groups and colours are shared with the other ESP
 * modules through {@link EspRenderer}.</p>
 */
public final class Nametags extends Module {
    private static final int BAR_WIDTH = 46;
    private static final net.minecraft.entity.EquipmentSlot[] ARMOR_SLOTS = {
            net.minecraft.entity.EquipmentSlot.HEAD,
            net.minecraft.entity.EquipmentSlot.CHEST,
            net.minecraft.entity.EquipmentSlot.LEGS,
            net.minecraft.entity.EquipmentSlot.FEET
    };
    private static final class Target {
        final LivingEntity entity;
        final String baseLabel;
        String label;
        final boolean player;
        final boolean monster;
        private float cachedHealth = Float.NaN;
        private float cachedAbsorption = Float.NaN;
        private boolean cachedHealthText;
        private boolean cachedShowAbsorption;

        Target(LivingEntity entity, String label) {
            this.entity = entity;
            this.baseLabel = label;
            this.player = entity instanceof PlayerEntity;
            this.monster = EspRenderer.isMonster(entity);
        }

        String label(boolean showHealth, boolean showAbsorption) {
            float health = showHealth ? entity.getHealth() : 0.0f;
            float extra = showHealth && showAbsorption ? entity.getAbsorptionAmount() : 0.0f;
            if (label == null || Float.compare(health, cachedHealth) != 0
                    || Float.compare(extra, cachedAbsorption) != 0
                    || showHealth != cachedHealthText || showAbsorption != cachedShowAbsorption) {
                label = baseLabel;
                if (showHealth) {
                    label += String.format(Locale.ROOT, " %.1f HP", health);
                    if (extra > 0) label += String.format(Locale.ROOT, " +%.1f", extra);
                }
                cachedHealth = health;
                cachedAbsorption = extra;
                cachedHealthText = showHealth;
                cachedShowAbsorption = showAbsorption;
            }
            return label;
        }
    }

    private final List<Target> renderTargets = new ArrayList<>();
    // EntityRendererMixin asks about each rendered entity every frame. Keep
    // membership alongside the ordered HUD list to avoid a linear scan there.
    private final Set<Entity> renderTargetSet = Collections.newSetFromMap(new IdentityHashMap<>());

    private final NumberSetting range = add(new NumberSetting("Range", 128.0, 16.0, 512.0, 1.0));
    private final ModeSetting targets = add(new ModeSetting("Targets",
            EspRenderer.TARGETS, "Players"));
    private final BooleanSetting healthText = add(new BooleanSetting("HealthText", true));
    private final BooleanSetting absorption = add(new BooleanSetting("Absorption", true));
    private final BooleanSetting healthBar = add(new BooleanSetting("HealthBar", true));
    private final BooleanSetting armorIcons = add(new BooleanSetting("ArmorIcons", false));
    private final BooleanSetting distance = add(new BooleanSetting("Distance", true));
    private final BooleanSetting background = add(new BooleanSetting("Background", true));
    private final ColorSetting playerColor = add(new ColorSetting("PlayerColor", 0xFF6466E9));
    private final ColorSetting monsterColor = add(new ColorSetting("MonsterColor", 0xFF6466E9));
    private final ColorSetting otherColor = add(new ColorSetting("OtherColor", 0xFF6466E9));
    private final ColorSetting backgroundColor = add(new ColorSetting("BackgroundColor", 0xB0000000));
    private final ColorSetting healthColor = add(new ColorSetting("HealthColor", 0xFF6466E9));
    private final ColorSetting lowHealthColor = add(new ColorSetting("LowHealthColor", 0xFF6466E9));

    public Nametags() {
        super("Nametags", Category.RENDER, "Clean nametags with health and distance");
    }

    @Override
    public boolean usesProjection() {
        return true;
    }

    @Override
    public boolean usesEntitySnapshot() {
        return true;
    }

    @Override
    protected void onDisable() {
        renderTargets.clear();
        renderTargetSet.clear();
    }

    @Override
    public void onTick() {
        renderTargets.clear();
        renderTargetSet.clear();
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) return;
        double maxDistSq = range.getValue() * range.getValue();
        String targetMode = targets.getValue();
        for (LivingEntity living : EntitySnapshot.livingEntities()) {
            Entity entity = living;
            if (entity != mc.player && entity.squaredDistanceTo(mc.player) <= maxDistSq
                    && EspRenderer.matchesTarget(entity, targetMode)) {
                double distanceValue = Math.sqrt(entity.squaredDistanceTo(mc.player));
                String label = living.getName().getString();
                if (distance.getValue()) {
                    label += " " + Math.round(distanceValue) + "m";
                }
                Target target = new Target(living, label);
                target.label(healthText.getValue(), absorption.getValue());
                renderTargets.add(target);
                renderTargetSet.add(living);
            }
        }
        renderTargets.sort(java.util.Comparator.comparingDouble(target -> target.entity.squaredDistanceTo(mc.player)));
    }

    /** Hide vanilla labels only for entities present in the current overlay snapshot. */
    public boolean shouldHide(Entity entity) {
        if (!isEnabled() || !(entity instanceof LivingEntity living) || !living.isAlive()
                || entity.isRemoved() || BytePhase.mc == null || BytePhase.mc.world != entity.getEntityWorld()
                || BytePhase.mc.player == null || entity.squaredDistanceTo(BytePhase.mc.player) > range.getValue() * range.getValue()
                || !EspRenderer.matchesTarget(living, targets.getValue())) return false;
        return renderTargetSet.contains(entity);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        float delta = RenderProjection.tickDelta();
        for (Target target : renderTargets) {
            LivingEntity living = target.entity;
            Entity entity = living;
            // Membership and range were decided when the snapshot was built;
            // only liveness can change between ticks.
            if (!living.isAlive() || living.isRemoved()) {
                continue;
            }

            // Packets can change health between ticks. Refresh the text on
            // change, not on every frame, so it agrees with the live health bar.
            String label = target.label(healthText.getValue(), absorption.getValue());

            Vec3d above = entity.getLerpedPos(delta).add(0, entity.getHeight() + 0.6, 0);
            double[] projected = RenderProjection.project(above);
            if (projected == null) {
                continue;
            }
            if (!RenderProjection.isOnScreen(projected, mc.getWindow().getScaledWidth(),
                    mc.getWindow().getScaledHeight())) {
                continue;
            }

            int textWidth = mc.textRenderer.getWidth(label);
            int width = Math.max(textWidth, healthBar.getValue() ? BAR_WIDTH : 0) + 8;
            int height = healthBar.getValue() ? 22 : 13;
            int x = (int) (projected[0] - width / 2.0);
            int y = (int) projected[1] - height;
            int iconHeight = armorIcons.getValue() && living instanceof PlayerEntity ? 18 : 0;
            int occupiedWidth = iconHeight > 0 ? Math.max(width, 72) : width;
            int occupiedX = (int) (projected[0] - occupiedWidth / 2.0);
            if (!dev.bytephase.render.OverlayLabels.reserve(occupiedX, y - iconHeight,
                    occupiedWidth, height + iconHeight)) continue;

            if (armorIcons.getValue() && living instanceof PlayerEntity player) {
                int count = 0;
                for (var slot : ARMOR_SLOTS) if (!player.getEquippedStack(slot).isEmpty()) count++;
                int iconX = (int) projected[0] - count * 9;
                for (var slot : ARMOR_SLOTS) {
                    var stack = player.getEquippedStack(slot);
                    if (!stack.isEmpty()) {
                        context.drawItem(player, stack, iconX, y - 18, player.getId());
                        iconX += 18;
                    }
                }
            }
            if (background.getValue()) {
                Render2D.roundedRect(context, x, y, width, height, 4, backgroundColor.getValue());
            }
            int nameColor = target.player ? playerColor.getValue()
                    : target.monster ? monsterColor.getValue()
                    : otherColor.getValue();
            context.drawTextWithShadow(mc.textRenderer, label,
                    x + (width - textWidth) / 2, y + 3, nameColor);

            if (healthBar.getValue()) {
                float fraction = clamp01(living.getHealth() / Math.max(1.0f, living.getMaxHealth()));
                int barX = x + (width - BAR_WIDTH) / 2;
                int barY = y + 15;
                context.fill(barX, barY, barX + BAR_WIDTH, barY + 3, 0x50FFFFFF);
                int color = Render2D.lerpColor(lowHealthColor.getValue(), healthColor.getValue(), fraction);
                context.fill(barX, barY, barX + Math.round(BAR_WIDTH * fraction), barY + 3,
                        0xFF000000 | (color & 0xFFFFFF));
            }
        }
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
