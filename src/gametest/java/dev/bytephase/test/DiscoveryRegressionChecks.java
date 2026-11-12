package dev.bytephase.test;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Module;
import dev.bytephase.module.render.SusChunks;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.setting.Setting;
import dev.bytephase.util.DiscoverySignals;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

final class DiscoveryRegressionChecks {
    static void run(MinecraftClient client) {
        var none = new DiscoverySignals.Options(false, false, false, false, false, false, false);
        var all = new DiscoverySignals.Options(true, true, true, true, true, true, true);
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState mature = Blocks.COCOA.getDefaultState().with(Properties.AGE_2, 2);
        require(DiscoverySignals.activity(mature, air, air, none) == 0, "Disabled signal was counted");
        require(DiscoverySignals.activity(mature, air, air, all) == 1, "Mature cocoa was missed");
        require(DiscoverySignals.activity(Blocks.COCOA.getDefaultState(), air, air, all) == 0,
                "Unripe cocoa was counted");
        require(DiscoverySignals.activity(Blocks.BEE_NEST.getDefaultState().with(Properties.HONEY_LEVEL, 5),
                null, null, all) == 1, "Full bee nest was missed");
        require(DiscoverySignals.activity(Blocks.DEEPSLATE.getDefaultState(), null, null, all) == 0,
                "Natural deepslate axis was counted");
        require(DiscoverySignals.activity(Blocks.DEEPSLATE.getDefaultState().with(Properties.AXIS, Direction.Axis.X),
                null, null, all) == 1, "Rotated deepslate was missed");
        require(DiscoverySignals.growthWeight(Blocks.SMALL_AMETHYST_BUD.getDefaultState()) == 1
                        && DiscoverySignals.growthWeight(Blocks.MEDIUM_AMETHYST_BUD.getDefaultState()) == 2
                        && DiscoverySignals.growthWeight(Blocks.LARGE_AMETHYST_BUD.getDefaultState()) == 3
                        && DiscoverySignals.growthWeight(Blocks.AMETHYST_CLUSTER.getDefaultState()) == 4,
                "Amethyst maturity weights are incorrect");

        SusChunks scanner = BytePhase.modules.get(SusChunks.class);
        boolean enabled = scanner.isEnabled();
        Map<Setting<?>, com.google.gson.JsonElement> settings = new LinkedHashMap<>();
        scanner.getSettings().forEach(setting -> settings.put(setting, setting.toJson()));
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        BlockPos base = new BlockPos(client.player.getChunkPos().getStartX() + 8, 200,
                client.player.getChunkPos().getStartZ() + 8);
        try {
            scanner.setEnabled(false);
            mode(scanner, "Signal").setValue("Activity");
            number(scanner, "Range").setValue(1.0);
            number(scanner, "MinActivity").setValue(2.0);
            bool(scanner, "LogFinds").setValue(false);
            bool(scanner, "ChatAnnounce").setValue(false);
            bool(scanner, "Cocoa").setValue(true);
            bool(scanner, "RotatedDeepslate").setValue(true);
            set(client, blocks, base, mature);
            set(client, blocks, base.east(), Blocks.DEEPSLATE.getDefaultState()
                    .with(Properties.AXIS, Direction.Axis.X));
            scanner.setEnabled(true);
            scan(scanner);
            require(suspects(scanner) > 0, "Activity scan missed combined growth and placement evidence");
            client.world.setBlockState(base, air, 0);
            client.world.setBlockState(base.east(), air, 0);
            scanner.onPacketReceive(new BlockUpdateS2CPacket(base, air));
            scanner.onRender3D(null, null, 0);
            require(suspects(scanner) == 0, "Changed activity evidence remained visible");

            scanner.setEnabled(false);
            bool(scanner, "Cocoa").setValue(false);
            bool(scanner, "RotatedDeepslate").setValue(false);
            bool(scanner, "VerticalTunnels").setValue(true);
            number(scanner, "MinActivity").setValue(1.0);
            number(scanner, "MinTunnelLength").setValue(7.0);
            for (int y = 0; y < 9; y++) {
                BlockPos cell = base.up(y);
                set(client, blocks, cell, air);
                for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                    set(client, blocks, cell.offset(side), Blocks.STONE.getDefaultState());
                }
            }
            scanner.setEnabled(true);
            scan(scanner);
            require(suspects(scanner) > 0, "Enclosed vertical shaft was missed");
            scanner.setEnabled(false);
            // Opening one wall in the middle splits both runs below the threshold.
            client.world.setBlockState(base.up(4).north(), air, 0);
            scanner.setEnabled(true);
            scan(scanner);
            require(suspects(scanner) == 0, "Open cave wall was mistaken for an enclosed shaft");
        } finally {
            scanner.setEnabled(false);
            blocks.forEach((pos, state) -> client.world.setBlockState(pos, state, 0));
            settings.forEach(Setting::fromJson);
            scanner.setEnabled(enabled);
        }
    }

    private static void set(MinecraftClient client, Map<BlockPos, BlockState> original, BlockPos pos, BlockState state) {
        original.putIfAbsent(pos, client.world.getBlockState(pos));
        client.world.setBlockState(pos, state, 0);
    }

    private static void scan(SusChunks scanner) {
        scanner.onTick();
        try {
            var queue = SusChunks.class.getDeclaredField("queue");
            queue.setAccessible(true);
            int ticks = 0;
            while (!((java.util.Deque<?>) queue.get(scanner)).isEmpty() && ticks++ < 2000) scanner.onTick();
            require(((java.util.Deque<?>) queue.get(scanner)).isEmpty(), "Discovery scan exceeded its bounded fixture");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static int suspects(SusChunks scanner) {
        try {
            var field = SusChunks.class.getDeclaredField("suspects");
            field.setAccessible(true);
            return ((Map<?, ?>) field.get(scanner)).size();
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static Setting<?> setting(Module module, String name) {
        return module.getSettings().stream().filter(setting -> setting.getName().equals(name)).findFirst().orElseThrow();
    }

    private static ModeSetting mode(Module module, String name) { return (ModeSetting) setting(module, name); }
    private static NumberSetting number(Module module, String name) { return (NumberSetting) setting(module, name); }
    private static BooleanSetting bool(Module module, String name) { return (BooleanSetting) setting(module, name); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
