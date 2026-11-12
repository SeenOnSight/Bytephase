package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.EspRenderer;
import dev.bytephase.render.OverlayLabels;
import dev.bytephase.render.RenderBox;
import dev.bytephase.render.RenderProjection;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

/** Shared, bounded discovery markers with optional labels and tracers. */
public abstract class DiscoveryOverlay extends Module {
    protected final NumberSetting radius = add(new NumberSetting("ChunkRadius", 8, 1, 12, 1));
    private final ModeSetting style = add(new ModeSetting("Style", EspRenderer.STYLES, "Outline"));
    private final ColorSetting color = add(new ColorSetting("Color", 0xFF6466E9));
    private final BooleanSetting tracers = add(new BooleanSetting("Tracers", false));
    private final BooleanSetting labels = add(new BooleanSetting("Labels", false));
    protected final BooleanSetting alerts = add(new BooleanSetting("ChatAlerts", true));
    protected final LinkedHashMap<Long, Finding> findings = new LinkedHashMap<>();
    protected ClientWorld world;
    private final List<Box> boxes = new ArrayList<>();
    private final List<Vec3d> centers = new ArrayList<>();
    private final java.util.ArrayDeque<String> notifications = new java.util.ArrayDeque<>();
    private int alertCooldown;

    protected record Finding(BlockPos pos, String label) { }

    protected DiscoveryOverlay(String name, String description) {
        super(name, Category.RENDER, description);
    }

    @Override
    public boolean usesProjection() { return true; }

    @Override
    protected void onEnable() { reset(BytePhase.mc.world); }

    @Override
    protected void onDisable() { reset(null); }

    protected void reset(ClientWorld nextWorld) {
        world = nextWorld;
        findings.clear();
        boxes.clear();
        centers.clear();
        notifications.clear();
        alertCooldown = 0;
    }

    protected boolean ready() {
        var mc = BytePhase.mc;
        if (world != mc.world) reset(mc.world);
        return mc.world != null && mc.player != null;
    }

    protected boolean loaded(int x, int z) {
        if (world == null || BytePhase.mc.player == null) return false;
        ChunkPos player = BytePhase.mc.player.getChunkPos();
        return Math.abs((long) x - player.x) <= radius.asInt()
                && Math.abs((long) z - player.z) <= radius.asInt()
                && world.getChunkManager().getWorldChunk(x, z) != null;
    }

    protected void found(long key, BlockPos pos, String label) {
        if (findings.containsKey(key)) return;
        findings.put(key, new Finding(pos.toImmutable(), label));
        if (findings.size() > 256) findings.remove(findings.keySet().iterator().next());
        notifyFinding(pos, label);
    }

    protected void notifyFinding(BlockPos pos, String label) {
        if (!alerts.getValue()) return;
        if (notifications.size() >= 32) notifications.removeFirst();
        notifications.add("[Bytephase] " + getName() + ": " + label + " at "
                + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    @Override
    public void onTick() {
        if (!ready()) return;
        findings.values().removeIf(finding -> !loaded(finding.pos().getX() >> 4, finding.pos().getZ() >> 4));
        if (!alerts.getValue()) notifications.clear();
        if (alertCooldown > 0) alertCooldown--;
        if (alertCooldown == 0 && !notifications.isEmpty()) {
            BytePhase.mc.inGameHud.getChatHud().addMessage(Text.literal(notifications.removeFirst()));
            alertCooldown = 20;
        }
    }

    protected Box box(Finding finding) {
        ChunkPos chunk = new ChunkPos(finding.pos());
        int minY = Math.max(world.getBottomY(), finding.pos().getY() - 1);
        return new Box(chunk.getStartX(), minY, chunk.getStartZ(),
                chunk.getStartX() + 16, minY + 2, chunk.getStartZ() + 16);
    }

    @Override
    public void onRender3D(MatrixStack matrices, VertexConsumerProvider consumers, float tickDelta) {
        if (world == null || world != BytePhase.mc.world) return;
        boxes.clear();
        centers.clear();
        for (Finding finding : findings.values()) {
            if (!loaded(finding.pos().getX() >> 4, finding.pos().getZ() >> 4)) continue;
            Box bounds = box(finding);
            boxes.add(bounds);
            if (tracers.getValue()) centers.add(Vec3d.ofCenter(finding.pos()));
        }
        EspRenderer.drawBoxes(boxes, color.getValue(), style.getValue(), true);
        if (tracers.getValue()) RenderBox.drawLinesFrom(RenderBox.tracerOrigin(), centers,
                EspRenderer.lineColor(color.rgb(), 0x70), true);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var mc = BytePhase.mc;
        if (!labels.getValue() || world == null || mc.world != world) return;
        for (Finding finding : findings.values()) {
            double[] projected = RenderProjection.project(Vec3d.ofCenter(finding.pos()).add(0, 1, 0));
            if (!RenderProjection.isOnScreen(projected, mc.getWindow().getScaledWidth(),
                    mc.getWindow().getScaledHeight())) continue;
            String text = finding.label();
            int width = mc.textRenderer.getWidth(text);
            int x = (int) projected[0] - width / 2, y = (int) projected[1];
            if (!OverlayLabels.reserve(x - 2, y - 1, width + 4, 11)) continue;
            context.fill(x - 2, y - 1, x + width + 2, y + 10, 0x90000000);
            context.drawTextWithShadow(mc.textRenderer, text, x, y, color.getValue());
        }
    }

    @Override
    public String getHudText() { return getName() + " [" + findings.size() + "]"; }
}
