package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.EspRenderer;
import dev.bytephase.render.EntitySnapshot;
import dev.bytephase.render.RenderBox;
import dev.bytephase.render.RenderProjection;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class Tracers extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", 64.0, 8.0, 256.0, 1.0));
    private final ModeSetting targets = add(new ModeSetting("Targets",
            EspRenderer.TARGETS, "All"));
    private final BooleanSetting throughWalls = add(new BooleanSetting("ThroughWalls", true));
    private final ColorSetting playerColor = add(new ColorSetting("PlayerColor", 0xFF6466E9));
    private final ColorSetting monsterColor = add(new ColorSetting("MonsterColor", 0xFF6466E9));
    private final ColorSetting otherColor = add(new ColorSetting("OtherColor", 0xFF6466E9));

    private final List<Vec3d> playerTargets = new ArrayList<>();
    private final List<Vec3d> monsterTargets = new ArrayList<>();
    private final List<Vec3d> otherTargets = new ArrayList<>();
    private final List<Entity> renderTargets = new ArrayList<>();

    public Tracers() {
        super("Tracers", Category.RENDER, "Lines to nearby entities");
    }

    @Override
    public boolean usesEntitySnapshot() {
        return true;
    }

    @Override
    public void onTick() {
        renderTargets.clear();
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null) return;
        double rangeSq = range.getValue() * range.getValue();
        String targetMode = targets.getValue();
        for (Entity entity : EntitySnapshot.entities()) {
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
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        playerTargets.clear();
        monsterTargets.clear();
        otherTargets.clear();

        float delta = RenderProjection.tickDelta();
        for (Entity entity : renderTargets) {
            if (entity.isRemoved() || !entity.isAlive()) {
                continue;
            }
            Vec3d target = entity.getLerpedPos(delta).add(0, entity.getHeight() * 0.9, 0);
            if (entity instanceof PlayerEntity) {
                playerTargets.add(target);
            } else if (EspRenderer.isMonster(entity)) {
                monsterTargets.add(target);
            } else {
                otherTargets.add(target);
            }
        }

        // The origin unprojects screen center using this frame's full matrices.
        Vec3d origin = RenderBox.tracerOrigin();
        boolean walls = throughWalls.getValue();
        RenderBox.drawLinesFrom(origin, playerTargets, playerColor.getValue(), walls);
        RenderBox.drawLinesFrom(origin, monsterTargets, monsterColor.getValue(), walls);
        RenderBox.drawLinesFrom(origin, otherTargets, otherColor.getValue(), walls);
    }
}
