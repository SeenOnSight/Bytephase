package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.pvp.api.CombatProvider;
import dev.bytephase.pvp.api.CombatServices;
import dev.bytephase.render.RenderProjection;
import dev.bytephase.render.RenderBox;
import java.util.List;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.loader.api.FabricLoader;

public final class CombatIntegration {
    private static List<CombatProvider> providers = List.of();

    private CombatIntegration() { }

    public static List<ImportedCombat> modules() {
        providers = List.copyOf(FabricLoader.getInstance().getEntrypoints("bytephase:pvp", CombatProvider.class));
        CombatServices services = new CombatServices() {
            public boolean isCleanCapture() { return dev.bytephase.render.capture.StreamProof.isCleanFrame(); }
            public boolean isEating() {
                var player = BytePhase.mc.player;
                return player != null && player.isUsingItem()
                        && player.getActiveItem().get(net.minecraft.component.DataComponentTypes.FOOD) != null;
            }
            public boolean isFreecam() {
                return BytePhase.mc.player != null && BytePhase.mc.getCameraEntity() != BytePhase.mc.player;
            }
            public boolean sprintHeld() { return BytePhase.mc.options != null && BytePhase.mc.options.sprintKey.isPressed(); }
            public double[] project(double x, double y, double z) { return RenderProjection.project(x, y, z); }
            public boolean reserveLabel(int x, int y, int width, int height) {
                return dev.bytephase.render.OverlayLabels.reserve(x, y, width, height);
            }
            public boolean supportsWorldOverlays() { return true; }
            public void drawWorldBox(double minX, double minY, double minZ,
                    double maxX, double maxY, double maxZ, int fill, int outline, boolean throughWalls) {
                if (isCleanCapture()) return;
                Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);
                if ((fill >>> 24) != 0) RenderBox.drawFilledBoxes(List.of(box), fill, throughWalls);
                if ((outline >>> 24) != 0) RenderBox.drawOutlineBox(box, outline, throughWalls);
            }
            public void drawWorldLine(double fromX, double fromY, double fromZ,
                    double toX, double toY, double toZ, int color, boolean throughWalls) {
                if (!isCleanCapture()) {
                    RenderBox.drawLine(new Vec3d(fromX, fromY, fromZ), new Vec3d(toX, toY, toZ), color, throughWalls);
                }
            }
        };
        for (CombatProvider provider : providers) provider.initialize(services);
        return providers.stream().flatMap(provider -> provider.modules().stream()).map(ImportedCombat::new).toList();
    }

    public static void beginTick() { for (CombatProvider provider : providers) provider.beginTick(); }
    public static void endTick() { for (CombatProvider provider : providers) provider.endTick(); }
    public static boolean sprintSuppressed() {
        return providers.stream().anyMatch(CombatProvider::sprintSuppressed);
    }
    public static List<String> friends() {
        return providers.stream().flatMap(provider -> provider.friends().stream()).distinct().sorted().toList();
    }
    public static boolean addFriend(String name) {
        boolean changed = false;
        for (CombatProvider provider : providers) changed |= provider.addFriend(name);
        return changed;
    }
    public static boolean removeFriend(String name) {
        boolean changed = false;
        for (CombatProvider provider : providers) changed |= provider.removeFriend(name);
        return changed;
    }
    public static void renderHud(Object context, float tickDelta) {
        for (CombatProvider provider : providers) provider.renderHud(context, tickDelta);
    }
    public static void renderWorld(float tickDelta) {
        for (CombatProvider provider : providers) provider.renderWorld(tickDelta);
    }
    public static void renderWorldOverlays(MatrixStack matrices, VertexConsumerProvider consumers, float tickDelta) {
        if (dev.bytephase.render.capture.StreamProof.isCleanFrame() || providers.isEmpty()) return;
        RenderBox.beginFrame(matrices, consumers);
        try {
            for (CombatProvider provider : providers) provider.renderWorldOverlays(tickDelta);
        } finally {
            RenderBox.endFrame();
        }
    }

    public static boolean enabled(String name) {
        var module = BytePhase.modules == null ? null : BytePhase.modules.getByName(name);
        return module instanceof ImportedCombat && module.isEnabled();
    }
}
