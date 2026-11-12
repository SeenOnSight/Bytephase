package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.EntitySnapshot;
import dev.bytephase.render.OverlayLabels;
import dev.bytephase.render.RenderProjection;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.NumberSetting;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Floating markers report observed health loss, rather than guessed attack damage. */
public final class DamageMarkers extends Module {
    private final BooleanSetting self = add(new BooleanSetting("SelfDamage", true));
    private final BooleanSetting nearby = add(new BooleanSetting("NearbyDamage", false));
    private final NumberSetting lifetime = add(new NumberSetting("LifetimeMs", 1000, 300, 2500, 100));
    private final ColorSetting color = add(new ColorSetting("Color", 0xFFFF6666));
    private final Map<LivingEntity, Float> health = new IdentityHashMap<>();
    private final ArrayDeque<Marker> markers = new ArrayDeque<>();
    private ClientWorld world;
    private LivingEntity attacked;
    private int attackTicks;
    private record Marker(Vec3d pos, String text, long born) { }

    public DamageMarkers() {
        super("DamageMarkers", Category.RENDER, "Shows floating numbers for observed health loss");
    }

    @Override
    public boolean usesEntitySnapshot() { return true; }

    @Override
    public boolean usesProjection() { return true; }

    @Override
    protected void onDisable() {
        health.clear();
        markers.clear();
        attacked = null;
        world = null;
        attackTicks = 0;
    }

    @Override
    public void onAttack(PlayerEntity player, Entity entity) {
        if (!(entity instanceof LivingEntity living)) return;
        if (world != BytePhase.mc.world) {
            onDisable();
            world = BytePhase.mc.world;
        }
        attacked = living;
        attackTicks = 40;
        health.putIfAbsent(living, living.getHealth());
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (world != mc.world) {
            onDisable();
            world = mc.world;
        }
        if (mc.player == null || world == null) return;
        if (attackTicks > 0) attackTicks--;
        if (attackTicks == 0 || attacked != null && attacked.isRemoved()) attacked = null;
        health.keySet().removeIf(entity -> entity.getEntityWorld() != world || entity.isRemoved()
                || entity != mc.player && entity != attacked && (!nearby.getValue()
                        || entity.squaredDistanceTo(mc.player) > 64));
        if (self.getValue()) observe(mc.player);
        else health.remove(mc.player);
        if (attacked != null && attacked.squaredDistanceTo(mc.player) <= 256) observe(attacked);
        if (nearby.getValue()) {
            int budget = 64;
            for (LivingEntity entity : EntitySnapshot.livingEntities()) {
                if (entity == mc.player || entity == attacked || entity.squaredDistanceTo(mc.player) > 64) continue;
                observe(entity);
                if (--budget == 0) break;
            }
        }
        long cutoff = System.nanoTime() - lifetime.asInt() * 1_000_000L;
        while (!markers.isEmpty() && markers.peekFirst().born() < cutoff) markers.removeFirst();
    }

    private void observe(LivingEntity entity) {
        if (health.size() >= 128 && !health.containsKey(entity)) return;
        float current = entity.getHealth();
        Float previous = health.put(entity, current);
        if (previous == null || previous <= current) return;
        float damage = previous - current;
        String text = "-" + String.format(Locale.ROOT, "%.1f", damage);
        markers.add(new Marker(entity.getEntityPos().add(0, entity.getHeight() + 0.2, 0), text, System.nanoTime()));
        while (markers.size() > 64) markers.removeFirst();
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var mc = BytePhase.mc;
        if (world == null || mc.world != world) return;
        long now = System.nanoTime();
        double duration = lifetime.getValue() * 1_000_000.0;
        for (Marker marker : markers) {
            double progress = (now - marker.born()) / duration;
            if (progress >= 1) continue;
            double[] projected = RenderProjection.project(marker.pos().add(0, progress * 0.8, 0));
            if (!RenderProjection.isOnScreen(projected, mc.getWindow().getScaledWidth(),
                    mc.getWindow().getScaledHeight())) continue;
            int width = mc.textRenderer.getWidth(marker.text());
            int x = (int) projected[0] - width / 2, y = (int) projected[1];
            if (!OverlayLabels.reserve(x - 1, y - 1, width + 2, 11)) continue;
            int alpha = Math.max(4, (int) (255 * (1 - progress)));
            context.drawTextWithShadow(mc.textRenderer, marker.text(), x, y, alpha << 24 | color.rgb());
        }
    }
}
