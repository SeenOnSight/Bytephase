package dev.bytephase.test;

import com.google.gson.JsonParser;
import dev.bytephase.BytePhase;
import dev.bytephase.config.ConfigManager;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.module.ModuleManager;
import dev.bytephase.module.render.BlockEsp;
import dev.bytephase.module.render.SusChunks;
import dev.bytephase.module.render.Watermark;
import dev.bytephase.module.world.BaseFinder;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.ui.ClickGuiScreen;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;

/** Storage failure, dispatch invalidation and resumable scanner checks. */
final class OptimizationRegressionChecks {
    static void run(MinecraftClient client) {
        try {
            for (Module module : BytePhase.modules.all()) {
                String key = "key.bytephase." + module.getName().toLowerCase(Locale.ROOT);
                require(!net.minecraft.text.Text.translatable(key).getString().equals(key),
                        "Missing module key-binding translation: " + key);
            }
            checkConfigRecovery();
            checkDisabledDispatch(client);
            checkBlockScan(client);
            checkScannerRenderCache(client, BytePhase.modules.get(BaseFinder.class), "renderBoxes", "marked",
                    "refreshMarkers");
            checkScannerRenderCache(client, BytePhase.modules.get(SusChunks.class), "boxes", "suspects",
                    "rebuildRenderCache");
        } catch (ReflectiveOperationException | java.io.IOException error) {
            throw new AssertionError("Optimization regression setup failed", error);
        }
    }

    private static void checkConfigRecovery() throws java.io.IOException, ReflectiveOperationException {
        var path = FabricLoader.getInstance().getConfigDir().resolve("bytephase.json");
        byte[] original = Files.exists(path) ? Files.readAllBytes(path) : null;
        var watermark = BytePhase.modules.get(Watermark.class);
        boolean enabled = watermark.isEnabled();
        var originalLayout = ClickGuiScreen.saveLayout();
        try {
            Files.deleteIfExists(path);
            Files.createDirectory(path);
            var blocker = path.resolve("write-blocker");
            Files.writeString(blocker, "Force a failed config replacement");
            ConfigManager config = new ConfigManager();
            config.requestSave();
            for (int tick = 0; tick < 20; tick++) config.tick();
            require((boolean) field(config, "dirty"), "Failed config write discarded pending settings");
            Files.delete(blocker);
            Files.delete(path);
            for (int tick = 0; tick < 20; tick++) config.tick();
            require(Files.isRegularFile(path), "Pending config save was not retried");
            require(!(boolean) field(config, "dirty"), "Successful config retry stayed pending");
            require(JsonParser.parseString(Files.readString(path)).getAsJsonObject().has("modules"),
                    "Config retry did not serialize modules");

            Files.writeString(path, "{ malformed", StandardCharsets.UTF_8);
            config.load();
            config.save();
            require(Files.readString(path).equals("{ malformed"), "Malformed config was overwritten");
            Files.writeString(path, "{\"configVersion\":9,\"clickGui\":{\"expanded\":[\"Watermark\"]}}");
            config.load();
            require(ClickGuiScreen.saveLayout().equals(originalLayout),
                    "Config without module data changed the GUI layout");
            watermark.setEnabled(true);
            Files.writeString(path, "{\"configVersion\":9,\"modules\":{\"Watermark\":{\"enabled\":false}}}");
            config.load();
            require(!watermark.isEnabled(), "Config reload ignored a disabled module state");
            config.requestSave();
            require((boolean) field(config, "dirty"), "Repaired config remained permanently read-only");
            config.save();
            require(JsonParser.parseString(Files.readString(path)).getAsJsonObject()
                    .getAsJsonObject("modules").size() > 1, "Repaired config could not be saved");
        } finally {
            if (Files.isDirectory(path)) {
                Files.deleteIfExists(path.resolve("write-blocker"));
                Files.delete(path);
            }
            watermark.setEnabled(enabled);
            ClickGuiScreen.loadLayout(originalLayout);
            if (original == null) Files.deleteIfExists(path);
            else Files.write(path, original);
        }
    }

    private static void checkDisabledDispatch(MinecraftClient client) throws ReflectiveOperationException {
        ModuleManager original = BytePhase.modules;
        ConfigManager config = BytePhase.config;
        ModuleManager manager = new ModuleManager();
        Probe first = new Probe("First");
        Probe second = new Probe("Second");
        Method add = ModuleManager.class.getDeclaredMethod("add", Module.class);
        add.setAccessible(true);
        add.invoke(manager, first);
        add.invoke(manager, second);
        try {
            BytePhase.modules = manager;
            BytePhase.config = null;
            first.next = second;
            first.setEnabled(true);
            for (int phase = 0; phase < 5; phase++) {
                second.calls = 0;
                second.setEnabled(true);
                switch (phase) {
                    case 0 -> manager.packetSend(null);
                    case 1 -> manager.packetReceive(null);
                    case 2 -> manager.render2D(null, 0);
                    case 3 -> manager.beforeAttack(client.player, client.player);
                    default -> manager.attack(client.player, client.player);
                }
                require(second.calls == 0, "Disabled module was invoked from a stale dispatch list: " + phase);
            }
        } finally {
            first.setEnabled(false);
            second.setEnabled(false);
            BytePhase.modules = original;
            BytePhase.config = config;
        }
    }

    private static void checkBlockScan(MinecraftClient client) throws ReflectiveOperationException {
        BlockEsp module = BytePhase.modules.get(BlockEsp.class);
        var horizontal = (NumberSetting) module.getSettings().stream()
                .filter(setting -> setting.getName().equals("Range")).findFirst().orElseThrow();
        var vertical = (NumberSetting) module.getSettings().stream()
                .filter(setting -> setting.getName().equals("YRange")).findFirst().orElseThrow();
        double oldRange = horizontal.getValue();
        double oldVertical = vertical.getValue();
        ModeSetting mode = (ModeSetting) module.getSettings().stream()
                .filter(setting -> setting.getName().equals("Mode")).findFirst().orElseThrow();
        String oldMode = mode.getValue();
        boolean enabled = module.isEnabled();
        BlockPos center = client.player.getBlockPos();
        BlockPos evidence = center.up(12);
        var oldState = client.world.getBlockState(evidence);
        try {
            module.setEnabled(false);
            horizontal.setValue(8.0);
            vertical.setValue(16.0);
            mode.setValue("Spawner");
            client.world.setBlockState(evidence, Blocks.SPAWNER.getDefaultState(), 0);
            module.setEnabled(true);
            Method column = BlockEsp.class.getDeclaredMethod("scanColumn",
                    net.minecraft.client.world.ClientWorld.class, int.class, int.class, int.class,
                    Set.class, int.class, long.class);
            column.setAccessible(true);
            Field scanY = BlockEsp.class.getDeclaredField("scanY");
            scanY.setAccessible(true);
            int start = Math.floorDiv(center.getY(), 16) * 16;
            scanY.setInt(module, start);
            require(!(boolean) column.invoke(module, client.world, center.getX(), center.getZ(),
                    center.getY(), Set.of(Blocks.SPAWNER), 16, System.nanoTime() - 1),
                    "Expired scan deadline did not pause the column");
            require(scanY.getInt(module) == start, "Paused scan skipped unprocessed blocks");
            require((boolean) column.invoke(module, client.world, center.getX(), center.getZ(),
                    center.getY(), Set.of(Blocks.SPAWNER), 16, Long.MAX_VALUE),
                    "Paused scan did not resume");
            var found = (Set<?>) field(module, "found");
            require(found.contains(evidence), "Resumed column missed its spawner");
            require(found.stream().noneMatch(pos -> pos instanceof BlockPos.Mutable),
                    "Block scanner retained a mutable lookup position");

            // A complete small scan must visit each spiral column once and remain bounded.
            module.setEnabled(false);
            module.setEnabled(true);
            module.onTick(); // Capture the current world.
            int ticks = 0;
            while ((boolean) field(module, "scanning") && ticks++ < 200) module.onTick();
            require(!(boolean) field(module, "scanning"), "Small BlockESP scan did not finish");
            require(((Set<?>) field(module, "found")).contains(evidence),
                    "Time-budgeted scan missed evidence in its range");
            require((int) field(module, "scanRemaining") <= 0, "BlockESP finished before visiting all columns");
            BytePhase.LOGGER.info("BlockESP regression: 289 columns completed in {} scan slices (previous cap: 49)",
                    ticks);
            horizontal.setValue(64.0);
            vertical.setValue(48.0);
            module.setEnabled(false);
            module.setEnabled(true);
            module.onTick();
            ticks = 0;
            int previous = (int) field(module, "scanRemaining");
            while ((boolean) field(module, "scanning") && ticks++ < 2000) {
                module.onTick();
                int remaining = (int) field(module, "scanRemaining");
                require(previous - remaining <= 128, "BlockESP exceeded its per-tick column cap");
                previous = remaining;
            }
            require(!(boolean) field(module, "scanning"), "Maximum-range scan did not finish");
            BytePhase.LOGGER.info(
                    "BlockESP regression: 16641 columns completed in {} scan slices (previous cap: 2774)", ticks);
        } finally {
            module.setEnabled(false);
            client.world.setBlockState(evidence, oldState, 0);
            horizontal.setValue(oldRange);
            vertical.setValue(oldVertical);
            mode.setValue(oldMode);
            module.setEnabled(enabled);
        }
    }

    private static void checkScannerRenderCache(MinecraftClient client, Module module, String cacheName,
            String resultsName, String rebuildName) throws ReflectiveOperationException {
        boolean enabled = module.isEnabled();
        Field cacheField = module.getClass().getDeclaredField(cacheName);
        cacheField.setAccessible(true);
        Object originalCache = cacheField.get(module);
        CountingBoxes cache = new CountingBoxes();
        try {
            module.setEnabled(false);
            module.setEnabled(true);
            module.onTick();
            ((Map<?, ?>) field(module, resultsName)).clear();
            cacheField.set(module, cache);
            if (module instanceof BaseFinder) {
                Method rebuild = module.getClass().getDeclaredMethod(rebuildName);
                rebuild.setAccessible(true);
                rebuild.invoke(module);
            } else {
                Method rebuild = module.getClass().getDeclaredMethod(rebuildName, long.class);
                rebuild.setAccessible(true);
                rebuild.invoke(module, System.nanoTime());
            }
            cache.clears = 0;
            for (int frame = 0; frame < 20; frame++) module.onRender3D(null, null, 0);
            require(cache.clears == 0, module.getName() + " rebuilt unchanged marker lists on every frame");
            var pos = client.player.getBlockPos();
            module.onPacketReceive(new BlockUpdateS2CPacket(pos, client.world.getBlockState(pos)));
            module.onRender3D(null, null, 0);
            require(cache.clears == 1, module.getName() + " did not invalidate its render cache on a block update");
            module.onRender3D(null, null, 0);
            require(cache.clears == 1, module.getName() + " repeated the same cache invalidation");
        } finally {
            module.setEnabled(false);
            cacheField.set(module, originalCache);
            module.setEnabled(enabled);
        }
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Probe extends Module {
        Probe next;
        int calls;

        Probe(String name) {
            super(name, Category.MISC, "Dispatch test fixture");
        }

        private void called() {
            calls++;
            if (next != null) next.setEnabled(false);
        }

        @Override public boolean onPacketSend(Packet<?> packet) { called(); return false; }
        @Override public boolean onPacketReceive(Packet<?> packet) { called(); return false; }
        @Override public void onRender2D(DrawContext context, float delta) { called(); }
        @Override public void onBeforeAttack(PlayerEntity self, Entity target) { called(); }
        @Override public void onAttack(PlayerEntity self, Entity target) { called(); }
    }

    private static final class CountingBoxes extends ArrayList<Box> {
        int clears;

        @Override
        public void clear() {
            clears++;
            super.clear();
        }
    }
}
