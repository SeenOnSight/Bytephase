package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.mixin.ClientPlayerInteractionManagerAccessor;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.render.RenderBox;
import dev.bytephase.render.RenderProjection;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.NumberSetting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Displays client mining progress and the server's coarse remote break stages. */
public final class MiningProgress extends Module {
    private static final int TIMEOUT_TICKS = 400;
    private static final int MAX_REMOTE_MINERS = 512;
    private final BooleanSetting showSelf = add(new BooleanSetting("ShowSelf", true));
    private final BooleanSetting showOthers = add(new BooleanSetting("ShowOthers", true));
    private final BooleanSetting throughWalls = add(new BooleanSetting("ThroughWalls", true));
    private final NumberSetting range = add(new NumberSetting("Range", 64, 4, 256, 1));
    private final ColorSetting color = add(new ColorSetting("Color", 0xFF6466E9));
    private final Map<Integer, RemoteBreak> remote = new HashMap<>();
    private ClientWorld world;
    private int ticks;
    // Built once per frame in the world pass and reused by the HUD pass.
    private List<Display> frameDisplays = List.of();

    private record RemoteBreak(BlockPos pos, int stage, int updated) { }
    private record Display(BlockPos pos, String label, float progress) { }

    public MiningProgress() {
        super("MiningProgress", Category.RENDER, "Displays block mining progress and miner names");
    }

    @Override
    public boolean usesProjection() { return true; }

    @Override
    protected void onEnable() { reset(); }

    @Override
    protected void onDisable() { reset(); }

    private void reset() {
        frameDisplays = List.of();
        remote.clear();
        world = null;
        ticks = 0;
    }

    /** Called on the client thread after vanilla receives a block break stage. */
    public void updateRemote(ClientWorld packetWorld, int id, BlockPos pos, int stage) {
        if (!isEnabled() || packetWorld == null || BytePhase.mc == null || packetWorld != BytePhase.mc.world) return;
        if (world != packetWorld) {
            reset();
            world = packetWorld;
        }
        if (stage < 0 || stage > 9) {
            remote.remove(id);
        } else if (remote.containsKey(id) || remote.size() < MAX_REMOTE_MINERS) {
            remote.put(id, new RemoteBreak(pos.toImmutable(), stage, ticks));
        }
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc == null || mc.world != world) {
            reset();
            if (mc != null) world = mc.world;
        }
        if (world == null) return;
        ticks++;
        remote.entrySet().removeIf(entry -> ticks - entry.getValue().updated() > TIMEOUT_TICKS
                || world.getBlockState(entry.getValue().pos()).isAir());
    }

    private List<Display> displays() {
        var mc = BytePhase.mc;
        List<Display> result = new ArrayList<>();
        if (mc == null || mc.player == null || mc.world == null || mc.world != world) return result;
        double rangeSq = range.getValue() * range.getValue();
        if (showSelf.getValue() && mc.interactionManager != null) {
            var accessor = (ClientPlayerInteractionManagerAccessor) mc.interactionManager;
            BlockPos pos = accessor.bytephase$isBreaking() ? accessor.bytephase$breakingPos() : null;
            float progress = accessor.bytephase$breakingProgress();
            if (pos != null && !mc.world.getBlockState(pos).isAir()
                    && pos.toCenterPos().squaredDistanceTo(mc.player.getEntityPos()) <= rangeSq) {
                result.add(new Display(pos, "You", clamp(progress)));
            }
        }
        if (showOthers.getValue()) {
            for (var entry : remote.entrySet()) {
                if (!(mc.world.getEntityById(entry.getKey()) instanceof PlayerEntity player) || player == mc.player) continue;
                RemoteBreak value = entry.getValue();
                if (mc.world.getBlockState(value.pos()).isAir()
                        || value.pos().toCenterPos().squaredDistanceTo(mc.player.getEntityPos()) > rangeSq) continue;
                // Packet stages 0..9 describe ten percent intervals, not exact progress.
                result.add(new Display(value.pos(), player.getName().getString() + " ~", value.stage() / 10.0f));
            }
        }
        result.sort(java.util.Comparator.comparing((Display d) -> d.pos().asLong()).thenComparing(Display::label));
        return result;
    }

    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }

    @Override
    public void onRender3D(MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider consumers, float delta) {
        frameDisplays = displays();
        for (Display display : frameDisplays) {
            Box box = new Box(display.pos()).expand(0.004);
            int rgb = color.rgb();
            RenderBox.drawFilledBoxes(List.of(box), 0x30000000 | rgb, throughWalls.getValue());
            RenderBox.drawOutlineBoxes(List.of(box), 0xFF000000 | rgb, throughWalls.getValue());
        }
    }

    @Override
    public void onRender2D(DrawContext context, float delta) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) return;
        if (frameDisplays.isEmpty()) return;
        Map<BlockPos, Integer> rows = new HashMap<>();
        for (Display display : frameDisplays) {
            double[] point = RenderProjection.project(display.pos().toCenterPos());
            if (!RenderProjection.isOnScreen(point, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight())) continue;
            if (!throughWalls.getValue()) {
                var hit = mc.world.raycast(new net.minecraft.world.RaycastContext(
                        RenderProjection.cameraPos(), display.pos().toCenterPos(),
                        net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                        net.minecraft.world.RaycastContext.FluidHandling.NONE, mc.player));
                if (hit.getType() != net.minecraft.util.hit.HitResult.Type.MISS
                        && !hit.getBlockPos().equals(display.pos())) continue;
            }
            int row = rows.merge(display.pos(), 1, Integer::sum) - 1;
            String text = display.label() + " " + Math.round(display.progress() * 100) + "%";
            int width = mc.textRenderer.getWidth(text);
            int x = (int) Math.round(point[0]) - width / 2;
            int y = (int) Math.round(point[1]) + row * 14 - 6;
            context.fill(x - 3, y - 2, x + width + 3, y + 12, 0xB0000000);
            context.drawTextWithShadow(mc.textRenderer, text, x, y, 0xFFFFFFFF);
            context.fill(x - 2, y + 9, x + width + 2, y + 11, 0x55444444);
            context.fill(x - 2, y + 9, x - 2 + Math.round((width + 4) * display.progress()), y + 11,
                    0xFF000000 | color.rgb());
        }
    }
}
