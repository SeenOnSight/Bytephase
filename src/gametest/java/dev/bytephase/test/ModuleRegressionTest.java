package dev.bytephase.test;

import dev.bytephase.BytePhase;
import dev.bytephase.module.combat.KeyPearl;
import dev.bytephase.module.movement.ElytraFly;
import dev.bytephase.module.misc.AutoReconnect;
import dev.bytephase.module.player.AutoEat;
import dev.bytephase.module.world.Nuker;
import dev.bytephase.module.render.TimeChanger;
import dev.bytephase.module.render.Crosshair;
import dev.bytephase.module.render.SkyShaders;
import dev.bytephase.module.render.PlayerEsp;
import dev.bytephase.module.render.BlockHighlight;
import dev.bytephase.module.render.ItemEsp;
import dev.bytephase.input.Keybinds;
import dev.bytephase.setting.ModeSetting;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.lwjgl.glfw.GLFW;
import dev.bytephase.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

public final class ModuleRegressionTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try {
            context.runOnClient(client -> {
            var path = FabricLoader.getInstance().getConfigDir().resolve("bytephase.json");
            byte[] original = Files.exists(path) ? Files.readAllBytes(path) : null;
            byte[] malformed = "{ invalid config".getBytes(StandardCharsets.UTF_8);
            try {
                Files.createDirectories(path.getParent());
                Files.write(path, malformed);
                ConfigManager config = new ConfigManager();
                config.load();
                config.save();
                require(java.util.Arrays.equals(malformed, Files.readAllBytes(path)),
                        "Malformed configuration was overwritten");
            } finally {
                if (original == null) Files.deleteIfExists(path);
                else Files.write(path, original);
            }
            });
        } catch (java.io.IOException e) {
            throw new AssertionError("Could not exercise config preservation", e);
        }
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksDownload();
            world.getServer().runCommand("gamemode survival @a");
            context.waitTicks(5);

            context.runOnClient(CompatibilityRegressionChecks::run);
            NativeCombatRegressionChecks.run(context, world);

            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                player.getInventory().setStack(0, ItemStack.EMPTY);
                player.getInventory().setStack(1, new ItemStack(Items.BREAD, 4));
                player.getHungerManager().setFoodLevel(8);
                player.getHungerManager().setSaturationLevel(0);
                player.currentScreenHandler.sendContentUpdates();
            });
            context.waitFor(client -> client.player.getHungerManager().getFoodLevel() == 8
                    && client.player.getInventory().getStack(1).isOf(Items.BREAD));
            context.runOnClient(client -> {
                client.player.getInventory().setSelectedSlot(0);
                BytePhase.modules.get(AutoEat.class).setEnabled(true);
            });
            context.waitFor(client -> client.player.getHungerManager().getFoodLevel() > 8, 100);
            context.runOnClient(client -> {
                BytePhase.modules.get(AutoEat.class).setEnabled(false);
                require(client.player.getInventory().getSelectedSlot() == 0, "AutoEat did not restore the hotbar slot");
                require(!client.options.useKey.isPressed(), "AutoEat left use key held");
            });

            context.runOnClient(client -> {
                client.player.getInventory().setStack(1, new ItemStack(Items.ENDER_PEARL));
                client.player.getInventory().setSelectedSlot(0);
                KeyPearl pearl = BytePhase.modules.get(KeyPearl.class);
                pearl.setEnabled(true);
                pearl.onTick();
                require(client.player.getInventory().getSelectedSlot() == 1, "KeyPearl did not select a pearl");
                client.player.getInventory().setStack(1, new ItemStack(Items.BREAD));
                pearl.onTick();
                require(!pearl.isEnabled(), "KeyPearl did not cancel after its item changed");
                require(client.player.getInventory().getSelectedSlot() == 0, "KeyPearl did not restore the slot");
                require(!client.player.isUsingItem(), "KeyPearl used a replacement item");
            });

            context.runOnClient(client -> {
                try {
                    AutoReconnect reconnect = BytePhase.modules.get(AutoReconnect.class);
                    AutoReconnect.suppressNextReconnect();
                    for (int i = 0; i < 25; i++) reconnect.onTick();
                    var suppressed = AutoReconnect.class.getDeclaredField("suppressNext");
                    suppressed.setAccessible(true);
                    require(suppressed.getBoolean(null), "AutoReconnect cleared suppression on the original connection");
                    suppressed.setBoolean(null, false);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });

            context.runOnClient(client -> {
                var player = client.player;
                var strafe = BytePhase.modules.get(dev.bytephase.module.movement.Strafe.class);
                var speed = BytePhase.modules.get(dev.bytephase.module.movement.Speed.class);
                var oldVelocity = player.getVelocity();
                var oldYaw = player.getYaw();
                boolean oldGround = player.isOnGround();
                boolean oldForward = client.options.forwardKey.isPressed();
                boolean oldBack = client.options.backKey.isPressed();
                boolean oldLeft = client.options.leftKey.isPressed();
                boolean oldRight = client.options.rightKey.isPressed();
                var control = (dev.bytephase.setting.NumberSetting) strafe.getSettings().stream()
                        .filter(s -> s.getName().equals("Control")).findFirst().orElseThrow();
                try {
                    player.setYaw(0);
                    player.setOnGround(false);
                    client.options.forwardKey.setPressed(false);
                    client.options.backKey.setPressed(false);
                    client.options.leftKey.setPressed(false);
                    client.options.rightKey.setPressed(true);
                    player.setVelocityClient(new net.minecraft.util.math.Vec3d(0.4, 0.2, 0));
                    strafe.onTick();
                    require(player.getVelocity().x < -0.399 && Math.abs(player.getVelocity().z) < 0.001,
                            "Strafe did not immediately reverse rightward movement");
                    require(Math.abs(player.getVelocity().y - 0.2) < 0.001, "Strafe changed vertical velocity");
                    control.setValue(50.0);
                    player.setVelocityClient(new net.minecraft.util.math.Vec3d(0.4, 0.2, 0));
                    strafe.onTick();
                    require(Math.abs(player.getVelocity().horizontalLength() - 0.4) < 0.0001
                                    && Math.abs(player.getVelocity().x) < 0.001,
                            "Partial Strafe lost speed during a reverse turn");
                    control.setValue(100.0);
                    player.setOnGround(true);
                    var unchanged = new net.minecraft.util.math.Vec3d(0.4, 0.2, 0);
                    player.setVelocityClient(unchanged);
                    strafe.onTick();
                    require(player.getVelocity().equals(unchanged), "Strafe changed grounded movement");
                    player.setOnGround(false);
                    client.options.rightKey.setPressed(false);
                    strafe.onTick();
                    require(player.getVelocity().equals(unchanged), "Strafe changed momentum without input");
                    client.options.forwardKey.setPressed(true);
                    client.options.rightKey.setPressed(true);
                    strafe.onTick();
                    require(Math.abs(player.getVelocity().x + 0.4 / Math.sqrt(2)) < 0.001
                                    && Math.abs(player.getVelocity().z - 0.4 / Math.sqrt(2)) < 0.001,
                            "Strafe diagonal movement was incorrect");
                    client.options.forwardKey.setPressed(false);
                    player.setVelocityClient(new net.minecraft.util.math.Vec3d(0.2, -0.1, 0));
                    speed.setEnabled(true);
                    strafe.setEnabled(true);
                    BytePhase.modules.tick();
                    require(player.getVelocity().x < -0.21 && Math.abs(player.getVelocity().y + 0.1) < 0.001,
                            "Strafe did not redirect Speed's accelerated momentum");
                    var fly = BytePhase.modules.get(dev.bytephase.module.movement.Fly.class);
                    fly.setEnabled(true);
                    player.setVelocityClient(unchanged);
                    strafe.onTick();
                    require(player.getVelocity().equals(unchanged), "Strafe interfered with Fly");
                    fly.setEnabled(false);
                } finally {
                    strafe.setEnabled(false);
                    speed.setEnabled(false);
                    control.setValue(100.0);
                    player.setVelocityClient(oldVelocity);
                    player.setYaw(oldYaw);
                    player.setOnGround(oldGround);
                    client.options.forwardKey.setPressed(oldForward);
                    client.options.backKey.setPressed(oldBack);
                    client.options.leftKey.setPressed(oldLeft);
                    client.options.rightKey.setPressed(oldRight);
                }
            });

            context.runOnClient(client -> {
                var player = client.player;
                var original = player.getVelocity();
                var knockback = new net.minecraft.util.math.Vec3d(0.4, 0.5, 0.6);
                var velocityModule = BytePhase.modules.get(dev.bytephase.module.combat.Velocity.class);
                velocityModule.setEnabled(false);
                client.getNetworkHandler().onEntityVelocityUpdate(
                        new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player.getId(), knockback));
                require(player.getVelocity().squaredDistanceTo(knockback) < 0.0001, "Vanilla knockback changed while Velocity disabled");
                player.setVelocityClient(original);
                velocityModule.setEnabled(true);
                client.getNetworkHandler().onEntityVelocityUpdate(
                        new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player.getId(), knockback));
                require(player.getVelocity().equals(original), "Velocity erased existing movement");
                var explosion = new net.minecraft.network.packet.s2c.play.ExplosionS2CPacket(
                        player.getEntityPos(), 0, 0, java.util.Optional.of(knockback),
                        net.minecraft.particle.ParticleTypes.EXPLOSION,
                        net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE,
                        net.minecraft.util.collection.WeightedPool.empty());
                client.getNetworkHandler().onExplosion(explosion);
                require(player.getVelocity().equals(original), "Velocity did not filter explosion knockback");
                velocityModule.setEnabled(false);
                client.getNetworkHandler().onExplosion(explosion);
                require(player.getVelocity().squaredDistanceTo(original.add(knockback)) < 0.0001,
                        "Explosion knockback changed while Velocity disabled");
                player.setVelocityClient(original);
            });

            context.runOnClient(client -> {
                var player = client.player;
                var velocity = player.getVelocity();
                boolean onGround = player.isOnGround();
                double fallDistance = player.fallDistance;
                boolean jump = client.options.jumpKey.isPressed();
                player.setOnGround(false);
                player.fallDistance = 1.0;
                player.setVelocityClient(new net.minecraft.util.math.Vec3d(0, -0.2, 0));
                client.options.jumpKey.setPressed(true);
                BytePhase.modules.get(ElytraFly.class).onTick();
                require(player.getVelocity().y < 0, "ElytraFly propelled a player without an elytra");
                player.setVelocityClient(velocity);
                player.setOnGround(onGround);
                player.fallDistance = fallDistance;
                client.options.jumpKey.setPressed(jump);
            });

            context.runOnClient(client -> {
                var module = BytePhase.modules.get(Crosshair.class);
                Keybinds.set(module, GLFW.GLFW_KEY_F8);
                var key = InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_F8);
                KeyBinding.onKeyPressed(key);
                Keybinds.tick(client);
                require(module.isEnabled(), "new module keybind did not toggle the module");
                module.setEnabled(false);
                Keybinds.set(module, GLFW.GLFW_KEY_UNKNOWN);
            });

            context.runOnClient(client -> {
                var surround = BytePhase.modules.get(dev.bytephase.module.combat.Surround.class);
                var keyMode = (dev.bytephase.setting.BooleanSetting) surround.getSettings().stream()
                        .filter(s -> s.getName().equals("KeyActivate")).findFirst().orElseThrow();
                keyMode.setValue(true);
                surround.setEnabled(true);
                Keybinds.set(surround, GLFW.GLFW_KEY_F7);
                boolean oldGround = client.player.isOnGround();
                client.player.setOnGround(true);
                try {
                    require(getObjectField(surround, "activationFeet") == null, "Surround activated without a key press");
                    KeyBinding.onKeyPressed(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_F7));
                    Keybinds.tick(client);
                    require(surround.isEnabled(), "Activation key disabled Surround");
                    require(client.player.getBlockPos().equals(getObjectField(surround, "activationFeet")),
                            "Surround key did not start an activation");
                    surround.setEnabled(false);
                    require(getObjectField(surround, "activationFeet") == null, "Surround retained a stale activation");
                    KeyBinding.onKeyPressed(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_F7));
                    Keybinds.tick(client);
                    require(!surround.isEnabled(), "Activation key enabled a disabled Surround");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                } finally {
                    keyMode.setValue(false);
                    client.player.setOnGround(oldGround);
                    Keybinds.set(surround, GLFW.GLFW_KEY_UNKNOWN);
                }
            });

            context.runOnClient(client -> {
                // Drag across a frame that changes the hit-list ordering, as happens
                // when the active panel is promoted above the other panels.
                var screen = new dev.bytephase.ui.ClickGuiScreen();
                client.setScreen(screen);
                try {
                    var expandedField = screen.getClass().getDeclaredField("expanded");
                    expandedField.setAccessible(true);
                    ((java.util.Set<String>) expandedField.get(screen)).add("AimAssist");
                    var renderState = new net.minecraft.client.gui.render.state.GuiRenderState();
                    var draw = new net.minecraft.client.gui.DrawContext(client, renderState, 0, 0);
                    screen.render(draw, 0, 0, 0);
                    var hitsField = screen.getClass().getDeclaredField("hits");
                    hitsField.setAccessible(true);
                    var countField = screen.getClass().getDeclaredField("hitCount");
                    countField.setAccessible(true);
                    var hits = (java.util.List<?>) hitsField.get(screen);
                    Object target = null;
                    for (int i = 0; i < countField.getInt(screen); i++) {
                        Object hit = hits.get(i);
                        var setting = hit.getClass().getDeclaredField("setting");
                        setting.setAccessible(true);
                        if (setting.get(hit) instanceof dev.bytephase.setting.NumberSetting number
                                && getObjectField(hit, "module") == BytePhase.modules.get(dev.bytephase.module.combat.AimAssist.class)) {
                            target = hit;
                            break;
                        }
                    }
                    require(target != null, "AimAssist slider not visible in test");
                    int x = (int) getObjectField(target, "x"), y = (int) getObjectField(target, "y");
                    var mouse = new net.minecraft.client.input.MouseInput(0, 0);
                    screen.mouseClicked(new net.minecraft.client.gui.Click(x + 2, y + 2, mouse), false);
                    screen.render(draw, 0, 0, 0);
                    screen.mouseDragged(new net.minecraft.client.gui.Click(x + 30, y + 2, mouse), 28, 0);
                    screen.mouseReleased(new net.minecraft.client.gui.Click(x + 30, y + 2, mouse));
                    int searchX = Math.max(0, (client.getWindow().getScaledWidth() - 160) / 2);
                    int searchY = Math.max(0, client.getWindow().getScaledHeight() - 24);
                    screen.mouseClicked(new net.minecraft.client.gui.Click(searchX + 4, searchY + 4, mouse), false);
                    for (char c : "crystal".toCharArray()) screen.charTyped(new net.minecraft.client.input.CharInput(c, 0));
                    var modulesMethod = screen.getClass().getDeclaredMethod("modules", dev.bytephase.module.Category.class);
                    modulesMethod.setAccessible(true);
                    var matches = (java.util.List<?>) modulesMethod.invoke(screen, dev.bytephase.module.Category.COMBAT);
                    require(matches.size() == 1 && matches.getFirst() == BytePhase.modules.get(dev.bytephase.module.combat.AutoCrystal.class),
                            "ClickGUI search did not filter modules");
                    screen.keyPressed(new net.minecraft.client.input.KeyInput(GLFW.GLFW_KEY_BACKSPACE, 0, 0));
                    require(getObjectField(screen, "search").equals("crysta"), "Search backspace failed");
                    screen.keyPressed(new net.minecraft.client.input.KeyInput(GLFW.GLFW_KEY_BACKSPACE, 0, GLFW.GLFW_MOD_CONTROL));
                    require(getObjectField(screen, "search").equals(""), "Ctrl+Backspace did not clear search");
                    require(BytePhase.modules.byCategory(dev.bytephase.module.Category.UI).contains(
                                    BytePhase.modules.get(dev.bytephase.module.render.UiSettings.class)),
                            "UI appearance settings are missing from the UI category");
                    screen.keyPressed(new net.minecraft.client.input.KeyInput(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                    require(client.currentScreen == screen, "Escape closed GUI while search was focused");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                } finally {
                    client.setScreen(null);
                }
            });

            context.runOnClient(client -> {
                var movement = BytePhase.modules.get(dev.bytephase.module.movement.InventoryMove.class);
                var key = client.options.forwardKey;
                movement.setEnabled(true);
                try {
                    client.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(client.player));
                    movement.updateKeys();
                    dev.bytephase.input.KeyOverride.set(movement, key, true);
                    require(key.isPressed(), "InventoryMove failed to claim a movement key");
                    client.setScreen(new net.minecraft.client.gui.screen.ChatScreen("", false));
                    movement.updateKeys();
                    require(!key.isPressed(), "InventoryMove left a movement key held in chat");
                } finally {
                    movement.setEnabled(false);
                    client.setScreen(null);
                }
            });

            context.runOnClient(client -> {
                var crosshair = BytePhase.modules.get(Crosshair.class);
                var style = (ModeSetting) crosshair.getSettings().stream()
                        .filter(setting -> setting.getName().equals("Style")).findFirst().orElseThrow();
                crosshair.setEnabled(true);
                try {
                    style.setValue("Vanilla");
                    require(!crosshair.hideVanilla(), "Vanilla crosshair style hides the vanilla crosshair");
                    crosshair.onRender2D(new net.minecraft.client.gui.DrawContext(client,
                            new net.minecraft.client.gui.render.state.GuiRenderState(), 0, 0), 0);
                } finally {
                    style.setValue("Cross");
                    crosshair.setEnabled(false);
                }
            });

            context.runOnClient(client -> {
                TimeChanger module = BytePhase.modules.get(TimeChanger.class);
                module.setEnabled(true);
                client.getNetworkHandler().onWorldTimeUpdate(new WorldTimeUpdateS2CPacket(54321, 18000, false));
                require(client.world.getTime() == 54321, "TimeChanger dropped the server world age");
                require(client.world.getLevelProperties().getTimeOfDay() == 6000,
                        "TimeChanger did not preserve its chosen time");
                module.setEnabled(false);
                require(client.world.getLevelProperties().getTimeOfDay() == 18000,
                        "TimeChanger did not restore the latest server time");
            });

            context.runOnClient(client -> {
                Nuker nuker = BytePhase.modules.get(Nuker.class);
                try {
                    var find = Nuker.class.getDeclaredMethod("findBlock");
                    find.setAccessible(true);
                    BlockPos selected = (BlockPos) find.invoke(nuker);
                    require(selected != null, "Nuker found no nearby block");
                    require(!(selected instanceof BlockPos.Mutable), "Nuker retained a mutable scan position");
                    require(!client.world.getBlockState(selected).isAir(), "Nuker selected air");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });

            context.runOnClient(client -> {
                var player = client.player;
                var previous = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
                var oldVelocity = player.getVelocity();
                float oldPitch = player.getPitch();
                boolean oldGround = player.isOnGround();
                var bounce = BytePhase.modules.get(dev.bytephase.module.movement.ElytraBounce.class);
                player.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
                bounce.setEnabled(true);
                player.setOnGround(false);
                player.startGliding();
                bounce.onTick();
                require(player.getPitch() == 75.0f, "ElytraBounce default angle is not 75 degrees down");
                var angle = (dev.bytephase.setting.NumberSetting) bounce.getSettings().stream()
                        .filter(s -> s.getName().equals("Angle")).findFirst().orElseThrow();
                angle.setValue(35.0);
                bounce.onTick();
                require(player.getPitch() == 35.0f, "ElytraBounce did not use its configured angle");
                angle.setValue(75.0);
                player.setOnGround(true);
                bounce.onTick();
                require(player.getVelocity().y > 0, "ElytraBounce did not jump with a stale glide flag");
                bounce.setEnabled(false);
                player.stopGliding();
                player.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, previous);
                player.setVelocityClient(oldVelocity);
                player.setOnGround(oldGround);
                player.setPitch(oldPitch);
            });

            context.runOnClient(client -> {
                var player = client.player;
                var oldPos = player.getEntityPos();
                float oldYaw = player.getYaw();
                BlockPos origin = player.getBlockPos().up(4);
                BlockPos wall = origin.add(1, 1, 0);
                BlockPos behind = wall.east();
                var oldWall = client.world.getBlockState(wall);
                var oldBehind = client.world.getBlockState(behind);
                var oldBehindDown = client.world.getBlockState(behind.down());
                var oldSlot = player.getInventory().getStack(1);
                int selected = player.getInventory().getSelectedSlot();
                try {
                    player.setPosition(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
                    player.setYaw(-90);
                    client.world.setBlockState(wall, net.minecraft.block.Blocks.OBSIDIAN.getDefaultState());
                    client.world.setBlockState(behind, net.minecraft.block.Blocks.AIR.getDefaultState());
                    client.world.setBlockState(behind.down(), net.minecraft.block.Blocks.AIR.getDefaultState());
                    var crystal = BytePhase.modules.get(dev.bytephase.module.combat.AutoCrystal.class);
                    var range = crystal.getClass().getDeclaredMethod("withinInteractionRange", net.minecraft.util.math.Vec3d.class,
                            BlockPos.class, double.class, double.class);
                    range.setAccessible(true);
                    var walls = (dev.bytephase.setting.BooleanSetting) crystal.getSettings().stream()
                            .filter(setting -> setting.getName().equals("ThroughWalls")).findFirst().orElseThrow();
                    walls.setValue(false);
                    require(!(boolean) range.invoke(crystal, behind.toCenterPos(), behind, 6.0, 6.0),
                            "AutoCrystal ignored disabled ThroughWalls");
                    walls.setValue(true);
                    require((boolean) range.invoke(crystal, behind.toCenterPos(), behind, 6.0, 6.0),
                            "AutoCrystal could not target through wall");
                    // The wall no longer needs free space behind it for the throw.
                    client.world.setBlockState(behind, net.minecraft.block.Blocks.OBSIDIAN.getDefaultState());
                    client.world.setBlockState(behind.down(), net.minecraft.block.Blocks.OBSIDIAN.getDefaultState());
                    player.getInventory().setStack(1, new ItemStack(Items.ENDER_PEARL));
                    player.getInventory().setSelectedSlot(0);
                    var phase = BytePhase.modules.get(dev.bytephase.module.movement.PearlPhase.class);
                    phase.setEnabled(true);
                    phase.onTick();
                    require(phase.isEnabled() && player.getInventory().getSelectedSlot() == 1,
                            "PearlPhase did not queue a wall throw");
                    phase.setEnabled(false);
                    require(player.getInventory().getSelectedSlot() == 0, "PearlPhase did not restore slot");
                    var noRender = BytePhase.modules.get(dev.bytephase.module.render.NoRender.class);
                    noRender.setEnabled(true);
                    require(noRender.blockOverlay(), "NoRender did not expose the in-wall block overlay toggle");
                    noRender.setEnabled(false);
                    require(!noRender.blockOverlay(), "NoRender block overlay stayed active after disabling");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                } finally {
                    client.world.setBlockState(wall, oldWall);
                    client.world.setBlockState(behind, oldBehind);
                    client.world.setBlockState(behind.down(), oldBehindDown);
                    player.getInventory().setStack(1, oldSlot);
                    player.getInventory().setSelectedSlot(selected);
                    player.setPosition(oldPos);
                    player.setYaw(oldYaw);
                }
            });

            context.runOnClient(client -> {
                var player = client.player;
                BlockPos feet = player.getBlockPos().add(2, 0, 0);
                BlockPos obsidian = feet.west();
                var old = client.world.getBlockState(obsidian);
                client.world.setBlockState(obsidian, net.minecraft.block.Blocks.OBSIDIAN.getDefaultState());
                try {
                    var miner = BytePhase.modules.get(dev.bytephase.module.combat.AutoMine.class);
                    var find = miner.getClass().getDeclaredMethod("findBlock", BlockPos.class);
                    find.setAccessible(true);
                    require(obsidian.equals(find.invoke(miner, feet)), "AutoMine did not choose adjacent obsidian");
                    client.world.setBlockState(obsidian, net.minecraft.block.Blocks.STONE.getDefaultState());
                    require(find.invoke(miner, feet) == null, "AutoMine chose non-obsidian with ObsidianOnly enabled");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                } finally {
                    client.world.setBlockState(obsidian, old);
                }
            });

            world.getServer().runCommand("gamemode creative @a");
            context.waitFor(client -> client.player.isCreative());
            context.runOnClient(client -> {
                var player = client.player;
                var oldPos = player.getEntityPos();
                var oldVelocity = player.getVelocity();
                var oldOffhand = player.getOffHandStack();
                BlockPos base = player.getBlockPos().up(8).east(2);
                BlockPos roof = base.up(2);
                BlockPos secondBase = base.north(2);
                var oldSecondBase = client.world.getBlockState(secondBase);
                var oldSecondAir = client.world.getBlockState(secondBase.up());
                var oldBase = client.world.getBlockState(base);
                var oldAir = client.world.getBlockState(base.up());
                var oldRoof = client.world.getBlockState(roof);
                var crystalModule = BytePhase.modules.get(dev.bytephase.module.combat.AutoCrystal.class);
                boolean oldFocus = booleanSetting(crystalModule, "Focus").getValue();
                try {
                    // This scenario asserts reservation-based, non-Focus retries.
                    // The separate Focus scenario intentionally exercises rapid retries.
                    booleanSetting(crystalModule, "Focus").setValue(false);
                    player.setPosition(base.getX() - 1.5, base.getY() + 1, base.getZ() + 0.5);
                    player.setVelocityClient(net.minecraft.util.math.Vec3d.ZERO);
                    player.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.END_CRYSTAL, 64));
                    client.world.setBlockState(base, net.minecraft.block.Blocks.OBSIDIAN.getDefaultState());
                    client.world.setBlockState(base.up(), net.minecraft.block.Blocks.AIR.getDefaultState());
                    client.world.setBlockState(roof, net.minecraft.block.Blocks.STONE.getDefaultState());
                    var fake = new net.minecraft.client.network.OtherClientPlayerEntity(client.world,
                            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "CrystalTest"));
                    fake.setPosition(base.getX() + 2.5, base.getY() + 1, base.getZ() + 0.5);
                    client.world.addEntity(fake);
                    crystalModule.setEnabled(true);
                    var occupied = new net.minecraft.entity.decoration.EndCrystalEntity(net.minecraft.entity.EntityType.END_CRYSTAL, client.world);
                    occupied.setPosition(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
                    client.world.addEntity(occupied);
                    ((dev.bytephase.setting.BooleanSetting) crystalModule.getSettings().stream()
                            .filter(s -> s.getName().equals("Break")).findFirst().orElseThrow()).setValue(false);
                    crystalModule.onTick();
                    require(crystalModule.getHudText().contains("P 0/s"), "AutoCrystal placed inside an entity");
                    occupied.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                    ((dev.bytephase.setting.BooleanSetting) crystalModule.getSettings().stream()
                            .filter(s -> s.getName().equals("Break")).findFirst().orElseThrow()).setValue(true);
                    crystalModule.onTick();
                    require(crystalModule.getHudText().contains("P 1/s"),
                            "AutoCrystal did not place below a solid upper block: " + crystalModule.getHudText());
                    crystalModule.onTick();
                    require(crystalModule.getHudText().contains("P 1/s"), "AutoCrystal spammed an unconfirmed placement");
                    var crystal = new net.minecraft.entity.decoration.EndCrystalEntity(net.minecraft.entity.EntityType.END_CRYSTAL, client.world);
                    crystal.setPosition(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
                    client.world.addEntity(crystal);
                    client.world.setBlockState(secondBase, net.minecraft.block.Blocks.OBSIDIAN.getDefaultState());
                    client.world.setBlockState(secondBase.up(), net.minecraft.block.Blocks.AIR.getDefaultState());
                    crystalModule.onTick();
                    require(crystalModule.getHudText().contains("P 2/s"),
                            "AutoCrystal failed to place on a second base in the same tick as a break");
                    require(crystalModule.getHudText().contains("B 1/s"), "AutoCrystal did not attack the crystal: " + crystalModule.getHudText());
                    var arrayList = BytePhase.modules.get(dev.bytephase.module.render.ArrayListView.class);
                    arrayList.setEnabled(true);
                    var gui = new net.minecraft.client.gui.DrawContext(client,
                            new net.minecraft.client.gui.render.state.GuiRenderState(), 0, 0);
                    arrayList.onRender2D(gui, 0);
                    try {
                        var entries = (java.util.List<?>) getObjectField(arrayList, "cachedEntries");
                        require(entries.stream().anyMatch(entry -> entry.toString().contains("P 2/s B 1/s")),
                                "ArrayList did not show crystal rates");
                    } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                    arrayList.setEnabled(false);
                    crystalModule.onTick();
                    require(crystalModule.getHudText().contains("B 1/s"), "AutoCrystal attacked the same crystal repeatedly");
                    try {
                        var profileMethod = crystalModule.getClass().getDeclaredMethod("damageProfile", net.minecraft.entity.LivingEntity.class);
                        profileMethod.setAccessible(true);
                        Object profile = profileMethod.invoke(null, player);
                        var calc = crystalModule.getClass().getDeclaredMethod("damageForImpact", double.class, profile.getClass());
                        calc.setAccessible(true);
                        var properties = client.world.getLevelProperties();
                        var oldDifficulty = properties.getDifficulty();
                        try {
                            properties.setDifficulty(net.minecraft.world.Difficulty.NORMAL);
                            double normal = (double) calc.invoke(null, 0.7, profile);
                            properties.setDifficulty(net.minecraft.world.Difficulty.HARD);
                            double hard = (double) calc.invoke(null, 0.7, profile);
                            require(hard > normal * 1.4, "Hard difficulty self damage was not scaled");
                        } finally {
                            properties.setDifficulty(oldDifficulty);
                        }
                    } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                    var explosion = new net.minecraft.network.packet.s2c.play.ExplosionS2CPacket(
                            crystal.getEntityPos(), 6, 0, java.util.Optional.empty(),
                            net.minecraft.particle.ParticleTypes.EXPLOSION,
                            net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE,
                            net.minecraft.util.collection.WeightedPool.empty());
                    client.getNetworkHandler().onExplosion(explosion);
                    require(crystalModule.getHudText().contains("X 1/s"), "Explosion rate was not counted");
                    client.getNetworkHandler().onExplosion(explosion);
                    require(crystalModule.getHudText().contains("X 1/s"), "Explosion counted twice");
                    fake.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                    crystal.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                } finally {
                    crystalModule.setEnabled(false);
                    booleanSetting(crystalModule, "Focus").setValue(oldFocus);
                    player.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, oldOffhand);
                    player.setPosition(oldPos);
                    player.setVelocityClient(oldVelocity);
                    client.world.setBlockState(secondBase, oldSecondBase);
                    client.world.setBlockState(secondBase.up(), oldSecondAir);
                    client.world.setBlockState(base, oldBase);
                    client.world.setBlockState(base.up(), oldAir);
                    client.world.setBlockState(roof, oldRoof);
                }
            });

            context.runOnClient(client -> {
                var player = client.player;
                var oldPos = player.getEntityPos();
                var oldOffhand = player.getOffHandStack();
                var origin = player.getBlockPos().up(10);
                var bases = java.util.List.of(origin.add(-2, -1, -2), origin.add(-2, -1, 2),
                        origin.add(2, -1, -2), origin.add(2, -1, 2), origin.add(2, -1, 1));
                var blocks = new java.util.HashMap<BlockPos, net.minecraft.block.BlockState>();
                var entities = new java.util.ArrayList<net.minecraft.entity.Entity>();
                var module = BytePhase.modules.get(dev.bytephase.module.combat.AutoCrystal.class);
                var batch = (dev.bytephase.setting.NumberSetting) module.getSettings().stream()
                        .filter(s -> s.getName().equals("CrystalsPerTick")).findFirst().orElseThrow();
                var place = (dev.bytephase.setting.BooleanSetting) module.getSettings().stream()
                        .filter(s -> s.getName().equals("Place")).findFirst().orElseThrow();
                var breakSetting = (dev.bytephase.setting.BooleanSetting) module.getSettings().stream()
                        .filter(s -> s.getName().equals("Break")).findFirst().orElseThrow();
                // This scenario checks the spread-out (non-focus) behavior: n crystals on n distinct bases.
                var focusSetting = (dev.bytephase.setting.BooleanSetting) module.getSettings().stream()
                        .filter(s -> s.getName().equals("Focus")).findFirst().orElseThrow();
                try {
                    focusSetting.setValue(false);
                    player.setPosition(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
                    player.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.END_CRYSTAL, 64));
                    for (var base : bases) {
                        for (var p : java.util.List.of(base, base.up(), base.up(2))) blocks.put(p, client.world.getBlockState(p));
                        client.world.setBlockState(base, net.minecraft.block.Blocks.OBSIDIAN.getDefaultState());
                        client.world.setBlockState(base.up(), net.minecraft.block.Blocks.AIR.getDefaultState());
                        client.world.setBlockState(base.up(2), net.minecraft.block.Blocks.AIR.getDefaultState());
                    }
                    var fake = new net.minecraft.client.network.OtherClientPlayerEntity(client.world,
                            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "BatchTarget"));
                    fake.setPosition(origin.getX() + 4.5, origin.getY(), origin.getZ() + 0.5);
                    client.world.addEntity(fake);
                    entities.add(fake);
                    for (int n = 1; n <= 4; n++) {
                        batch.setValue((double) n);
                        place.setValue(true);
                        breakSetting.setValue(false);
                        module.setEnabled(true);
                        module.onTick();
                        var pending = (java.util.Map<?, ?>) getObjectField(module, "pendingPlaces");
                        require(pending.size() == n, "Expected " + n + " distinct placements, got " + pending.size());
                        for (Object first : pending.keySet()) {
                            for (Object second : pending.keySet()) {
                                if (first.equals(second)) continue;
                                var a = (BlockPos) first;
                                var b = (BlockPos) second;
                                require(Math.abs(a.getX() - b.getX()) > 1 || Math.abs(a.getZ() - b.getZ()) > 1,
                                        "Single-scan batch placed overlapping crystals");
                            }
                        }
                        require(module.getHudText().contains("P " + n + "/s"), "Placement rate missing batch count");
                        module.setEnabled(false);
                    }
                    // In survival, compare the action count against the same
                    // per-crystal damage budget used by the implementation.
                    var entry = client.getNetworkHandler().getPlayerListEntry(player.getUuid());
                    var gameMode = entry.getClass().getDeclaredField("gameMode");
                    gameMode.setAccessible(true);
                    var previousMode = gameMode.get(entry);
                    gameMode.set(entry, net.minecraft.world.GameMode.SURVIVAL);
                    try {
                        player.setHealth(20.0f);
                        var find = module.getClass().getDeclaredMethod("damageProfile", net.minecraft.entity.LivingEntity.class);
                        find.setAccessible(true);
                        Object profile = find.invoke(null, player);
                        var selfDamage = module.getClass().getDeclaredMethod("selfDamageAt", net.minecraft.util.math.Vec3d.class, profile.getClass());
                        selfDamage.setAccessible(true);
                        double minimum = Double.POSITIVE_INFINITY;
                        for (var base : bases) {
                            double damage = (double) selfDamage.invoke(null,
                                    new net.minecraft.util.math.Vec3d(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5), profile);
                            minimum = Math.min(minimum, damage);
                        }
                        require(minimum > 0, "Survival test unexpectedly has zero self damage");
                        var maxDamage = (dev.bytephase.setting.NumberSetting) module.getSettings().stream()
                                .filter(s -> s.getName().equals("MaxSelfDamage")).findFirst().orElseThrow();
                        maxDamage.setValue(84.0);
                        batch.setValue(4.0);
                        module.setEnabled(true);
                        module.onTick();
                        int count = ((java.util.Map<?, ?>) getObjectField(module, "pendingPlaces")).size();
                        require(count * minimum <= 19.0001 && count < 4,
                                "Batch exceeded survivable combined self damage: " + count + " at least " + minimum);
                        module.setEnabled(false);

                        // A batch of one still has two possible action paths.
                        // Resistance makes individual explosions survivable;
                        // the combined break/place estimate must stay in budget.
                        var oldResistance = player.getStatusEffect(net.minecraft.entity.effect.StatusEffects.RESISTANCE);
                        var mixedCrystal = new net.minecraft.entity.decoration.EndCrystalEntity(
                                net.minecraft.entity.EntityType.END_CRYSTAL, client.world);
                        var mixedBase = bases.getFirst();
                        mixedCrystal.setPosition(mixedBase.getX() + 0.5, mixedBase.getY() + 1,
                                mixedBase.getZ() + 0.5);
                        try {
                            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                    net.minecraft.entity.effect.StatusEffects.RESISTANCE, 200, 3));
                            client.world.addEntity(mixedCrystal);
                            batch.setValue(1.0);
                            place.setValue(true);
                            breakSetting.setValue(true);
                            profile = find.invoke(null, player);
                            module.setEnabled(true);
                            module.onTick();
                            var attacks = (java.util.Map<?, ?>) getObjectField(module, "pendingAttacks");
                            var placements = (java.util.Map<?, ?>) getObjectField(module, "pendingPlaces");
                            require(attacks.size() == 1, "Mixed budget test did not attack a survivable crystal");
                            double reserved = (double) selfDamage.invoke(null, mixedCrystal.getEntityPos(), profile);
                            for (Object key : placements.keySet()) {
                                var p = (BlockPos) key;
                                reserved += (double) selfDamage.invoke(null,
                                        new net.minecraft.util.math.Vec3d(p.getX() + 0.5, p.getY() + 1,
                                                p.getZ() + 0.5), profile);
                            }
                            require(reserved <= 19.0001, "Single-crystal break/place exceeded combined budget: " + reserved);
                        } finally {
                            module.setEnabled(false);
                            mixedCrystal.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                            player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.RESISTANCE);
                            if (oldResistance != null) player.addStatusEffect(oldResistance);
                        }
                        maxDamage.setValue(30.0);
                    } finally {
                        gameMode.set(entry, previousMode);
                    }
                    for (var base : bases) {
                        var crystal = new net.minecraft.entity.decoration.EndCrystalEntity(net.minecraft.entity.EntityType.END_CRYSTAL, client.world);
                        crystal.setPosition(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
                        client.world.addEntity(crystal);
                        entities.add(crystal);
                    }
                    for (int n = 1; n <= 4; n++) {
                        batch.setValue((double) n);
                        place.setValue(false);
                        breakSetting.setValue(true);
                        module.setEnabled(true);
                        module.onTick();
                        var pending = (java.util.Map<?, ?>) getObjectField(module, "pendingAttacks");
                        require(pending.size() == n, "Expected " + n + " distinct attacks, got " + pending.size());
                        require(module.getHudText().contains("B " + n + "/s"), "Break rate missing batch count");
                        module.setEnabled(false);
                    }
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                } finally {
                    module.setEnabled(false);
                    batch.setValue(1.0);
                    focusSetting.setValue(true);
                    place.setValue(true);
                    breakSetting.setValue(true);
                    for (var entity : entities) entity.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                    blocks.forEach((pos, state) -> client.world.setBlockState(pos, state));
                    player.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, oldOffhand);
                    player.setPosition(oldPos);
                }
            });

            // Focus mode: every crystal of a cycle goes on the single best base.
            context.runOnClient(client -> {
                var player = client.player;
                var oldPos = player.getEntityPos();
                var oldOffhand = player.getOffHandStack();
                var origin = player.getBlockPos().up(10);
                var bases = java.util.List.of(origin.add(-2, -1, -2), origin.add(-2, -1, 2),
                        origin.add(2, -1, -2), origin.add(2, -1, 2));
                var blocks = new java.util.HashMap<BlockPos, net.minecraft.block.BlockState>();
                var entities = new java.util.ArrayList<net.minecraft.entity.Entity>();
                var module = BytePhase.modules.get(dev.bytephase.module.combat.AutoCrystal.class);
                var batch = (dev.bytephase.setting.NumberSetting) module.getSettings().stream()
                        .filter(s -> s.getName().equals("CrystalsPerTick")).findFirst().orElseThrow();
                var breakSetting = (dev.bytephase.setting.BooleanSetting) module.getSettings().stream()
                        .filter(s -> s.getName().equals("Break")).findFirst().orElseThrow();
                double oldSpeed = numberSetting(module, "RotationSpeed").getValue();
                double oldSmoothing = numberSetting(module, "Smoothing").getValue();
                boolean oldConservative = booleanSetting(module, "Conservative").getValue();
                try {
                    player.setPosition(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
                    player.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.END_CRYSTAL, 64));
                    for (var base : bases) {
                        for (var p : java.util.List.of(base, base.up(), base.up(2))) blocks.put(p, client.world.getBlockState(p));
                        client.world.setBlockState(base, net.minecraft.block.Blocks.OBSIDIAN.getDefaultState());
                        client.world.setBlockState(base.up(), net.minecraft.block.Blocks.AIR.getDefaultState());
                        client.world.setBlockState(base.up(2), net.minecraft.block.Blocks.AIR.getDefaultState());
                    }
                    var fake = new net.minecraft.client.network.OtherClientPlayerEntity(client.world,
                            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "FocusTarget"));
                    fake.setPosition(origin.getX() + 4.5, origin.getY(), origin.getZ() + 0.5);
                    client.world.addEntity(fake);
                    entities.add(fake);
                    batch.setValue(4.0);
                    breakSetting.setValue(false);
                    module.setEnabled(true);
                    module.onTick();
                    var pending = (java.util.Map<?, ?>) getObjectField(module, "pendingPlaces");
                    require(pending.size() == 1, "Focus should use one base for all crystals, got " + pending.size());
                    require(module.getHudText().contains("P 4/s"), "Focus should still send every crystal of the cycle");
                    module.setEnabled(false);
                    booleanSetting(module, "Conservative").setValue(true);
                    numberSetting(module, "RotationSpeed").setValue(360.0);
                    numberSetting(module, "Smoothing").setValue(0.0);
                    // Isolate the retry check to one base. A different legal base
                    // on a later cycle is allowed and is not a repeated placement.
                    for (var base : bases.subList(1, bases.size())) {
                        client.world.setBlockState(base, net.minecraft.block.Blocks.AIR.getDefaultState());
                    }
                    module.setEnabled(true);
                    module.onTick();
                    require(module.getHudText().contains("P 1/s"), "Conservative mode did not cap focus placements");
                    module.onTick();
                    module.onTick();
                    require(module.getHudText().contains("P 1/s"), "Conservative mode repeated an unconfirmed placement");
                    module.setEnabled(false);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                } finally {
                    module.setEnabled(false);
                    booleanSetting(module, "Conservative").setValue(oldConservative);
                    numberSetting(module, "RotationSpeed").setValue(oldSpeed);
                    numberSetting(module, "Smoothing").setValue(oldSmoothing);
                    batch.setValue(1.0);
                    breakSetting.setValue(true);
                    for (var entity : entities) entity.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                    blocks.forEach((pos, state) -> client.world.setBlockState(pos, state));
                    player.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, oldOffhand);
                    player.setPosition(oldPos);
                }
            });

            var miningTarget = new BlockPos[1];
            context.runOnClient(client -> {
                var overlay = BytePhase.modules.get(dev.bytephase.module.render.MiningProgress.class);
                var miner = new net.minecraft.client.network.OtherClientPlayerEntity(client.world,
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "MiningTester"));
                miner.setPosition(client.player.getX() + 2, client.player.getY(), client.player.getZ());
                client.world.addEntity(miner);
                BlockPos pos = client.player.getBlockPos().add(0, 2, 3);
                miningTarget[0] = pos;
                var oldState = client.world.getBlockState(pos);
                try {
                    client.world.setBlockState(pos, net.minecraft.block.Blocks.OBSIDIAN.getDefaultState());
                    overlay.setEnabled(true);
                    client.getNetworkHandler().onBlockBreakingProgress(
                            new net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket(miner.getId(), pos, 4));
                    var displays = overlay.getClass().getDeclaredMethod("displays");
                    displays.setAccessible(true);
                    var entries = (java.util.List<?>) displays.invoke(overlay);
                    require(entries.size() == 1 && getObjectField(entries.getFirst(), "label").equals("MiningTester ~")
                                    && Math.abs((float) getObjectField(entries.getFirst(), "progress") - 0.4f) < 0.001,
                            "Remote mining stage or name was not displayed");
                    client.getNetworkHandler().onBlockBreakingProgress(
                            new net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket(miner.getId(), pos, 8));
                    entries = (java.util.List<?>) displays.invoke(overlay);
                    require(Math.abs((float) getObjectField(entries.getFirst(), "progress") - 0.8f) < 0.001,
                            "Remote mining progress did not update");
                    client.getNetworkHandler().onBlockBreakingProgress(
                            new net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket(miner.getId(), pos, -1));
                    require(((java.util.List<?>) displays.invoke(overlay)).isEmpty(), "Cancelled mining stayed visible");
                    var packetMine = BytePhase.modules.get(dev.bytephase.module.world.PacketMine.class);
                    packetMine.setEnabled(true);
                    var field = packetMine.getClass().getDeclaredField("mining"); field.setAccessible(true); field.set(packetMine, pos);
                    var progress = packetMine.getClass().getDeclaredField("progress"); progress.setAccessible(true); progress.setFloat(packetMine, 0.65f);
                    entries = (java.util.List<?>) displays.invoke(overlay);
                    require(entries.size() == 1 && getObjectField(entries.getFirst(), "label").equals("You")
                                    && Math.abs((float) getObjectField(entries.getFirst(), "progress") - 0.65f) < 0.001,
                            "PacketMine progress was not displayed");
                    packetMine.setEnabled(false);
                    var manager = client.interactionManager;
                    var managerType = net.minecraft.client.network.ClientPlayerInteractionManager.class;
                    var vanillaPos = managerType.getDeclaredField("currentBreakingPos"); vanillaPos.setAccessible(true);
                    var vanillaProgress = managerType.getDeclaredField("currentBreakingProgress"); vanillaProgress.setAccessible(true);
                    var vanillaBreaking = managerType.getDeclaredField("breakingBlock"); vanillaBreaking.setAccessible(true);
                    var oldVanillaPos = vanillaPos.get(manager);
                    float oldVanillaProgress = vanillaProgress.getFloat(manager);
                    boolean oldVanillaBreaking = vanillaBreaking.getBoolean(manager);
                    try {
                        vanillaPos.set(manager, pos);
                        vanillaProgress.setFloat(manager, 0.37f);
                        vanillaBreaking.setBoolean(manager, true);
                        entries = (java.util.List<?>) displays.invoke(overlay);
                        require(entries.size() == 1 && getObjectField(entries.getFirst(), "label").equals("You")
                                        && Math.abs((float) getObjectField(entries.getFirst(), "progress") - 0.37f) < 0.001,
                                "Vanilla mining progress was not displayed");
                    } finally {
                        vanillaPos.set(manager, oldVanillaPos);
                        vanillaProgress.setFloat(manager, oldVanillaProgress);
                        vanillaBreaking.setBoolean(manager, oldVanillaBreaking);
                    }
                    client.getNetworkHandler().onBlockBreakingProgress(
                            new net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket(miner.getId(), pos, 2));
                    client.world.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState());
                    overlay.onTick();
                    require(((java.util.List<?>) displays.invoke(overlay)).isEmpty(), "Broken block stayed visible");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                } finally {
                    overlay.setEnabled(false);
                    client.world.setBlockState(pos, oldState);
                    miner.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                }
            });
            context.runOnClient(client -> {
                var overlay = BytePhase.modules.get(dev.bytephase.module.render.MiningProgress.class);
                var fake = new net.minecraft.client.network.OtherClientPlayerEntity(client.world,
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "OverlayMiner"));
                fake.setPosition(client.player.getX() + 2, client.player.getY(), client.player.getZ());
                client.world.addEntity(fake);
                var pos = client.player.getBlockPos().add(0, 1, 3);
                miningTarget[0] = pos;
                client.player.setYaw(0);
                client.player.setPitch(0);
                client.world.setBlockState(pos, net.minecraft.block.Blocks.OBSIDIAN.getDefaultState());
                overlay.setEnabled(true);
                client.getNetworkHandler().onBlockBreakingProgress(
                        new net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket(fake.getId(), pos, 4));
            });
            context.waitTicks(4);
            context.runOnClient(client -> {
                var overlay = BytePhase.modules.get(dev.bytephase.module.render.MiningProgress.class);
                var state = new net.minecraft.client.gui.render.state.GuiRenderState();
                overlay.onRender2D(new net.minecraft.client.gui.DrawContext(client, state, 0, 0), 0);
                var texts = new java.util.ArrayList<String>();
                state.forEachTextElement(text -> {
                    var value = new StringBuilder();
                    text.orderedText.accept((index, style, cp) -> { value.appendCodePoint(cp); return true; });
                    texts.add(value.toString());
                });
                require(texts.contains("OverlayMiner ~ 40%"), "Mining progress label was not rendered: " + texts);
                overlay.setEnabled(false);
                client.world.setBlockState(miningTarget[0], net.minecraft.block.Blocks.AIR.getDefaultState());
                client.world.getPlayers().stream().filter(p -> p.getName().getString().equals("OverlayMiner"))
                        .findFirst().ifPresent(p -> p.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED));
            });

            var combatOriginalPosition = new net.minecraft.util.math.Vec3d[1];
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                combatOriginalPosition[0] = player.getEntityPos();
                var pos = player.getBlockPos().up(5);
                player.teleport(server.getOverworld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                        java.util.Set.of(), -90.0f, 0.0f, false);
                player.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.END_CRYSTAL, 64));
                player.getInventory().markDirty();
                var base = pos.east(2).down();
                server.getOverworld().setBlockState(pos.down(), net.minecraft.block.Blocks.STONE.getDefaultState());
                server.getOverworld().setBlockState(base, net.minecraft.block.Blocks.OBSIDIAN.getDefaultState());
                server.getOverworld().setBlockState(base.up(), net.minecraft.block.Blocks.AIR.getDefaultState());
                server.getOverworld().setBlockState(base.up(2), net.minecraft.block.Blocks.AIR.getDefaultState());
                server.getOverworld().setBlockState(base.up(3), net.minecraft.block.Blocks.AIR.getDefaultState());
            });
            context.waitTicks(8);
            context.runOnClient(client -> {
                var fake = new net.minecraft.client.network.OtherClientPlayerEntity(client.world,
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "RealCrystalTarget"));
                fake.setPosition(client.player.getX() + 4.0, client.player.getY(), client.player.getZ());
                client.world.addEntity(fake);
                BytePhase.modules.get(dev.bytephase.module.combat.AutoCrystal.class).setEnabled(true);
            });
            context.waitFor(client -> {
                String text = BytePhase.modules.get(dev.bytephase.module.combat.AutoCrystal.class).getHudText();
                return !text.contains("P 0/s") && !text.contains("B 0/s") && !text.contains("X 0/s");
            }, 80);
            context.waitFor(client -> {
                String text = BytePhase.modules.get(dev.bytephase.module.combat.AutoCrystal.class).getHudText();
                int start = text.indexOf("X ") + 2;
                return Integer.parseInt(text.substring(start, text.indexOf("/s", start))) >= 2;
            }, 80);
            context.runOnClient(client -> {
                var module = BytePhase.modules.get(dev.bytephase.module.combat.AutoCrystal.class);
                System.out.println("CRYSTAL LIVE RATES " + module.getHudText());
                module.setEnabled(false);
                client.world.getPlayers().stream().filter(p -> p.getName().getString().equals("RealCrystalTarget"))
                        .findFirst().ifPresent(p -> p.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED));
            });
            world.getServer().runOnServer(server -> {
                for (var entity : server.getOverworld().getEntitiesByType(net.minecraft.entity.EntityType.END_CRYSTAL, e -> true)) {
                    entity.discard();
                }
                var player = server.getPlayerManager().getPlayerList().getFirst();
                var pos = combatOriginalPosition[0];
                player.teleport(server.getOverworld(), pos.x, pos.y, pos.z, java.util.Set.of(), 0, 0, false);
                player.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
            });
            world.getServer().runCommand("gamemode survival @a");
            context.waitFor(client -> !client.player.isCreative());
            context.waitTicks(5);
            context.runOnClient(client -> {
                var esp = BytePhase.modules.get(PlayerEsp.class);
                var fake = new net.minecraft.client.network.OtherClientPlayerEntity(client.world,
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "HiddenEspTest"));
                var camera = client.gameRenderer.getCamera();
                var targetPos = camera.getCameraPos().add(net.minecraft.util.math.Vec3d.fromPolar(
                        client.player.getPitch(), client.player.getYaw()).multiply(5));
                fake.setPosition(targetPos.x, targetPos.y, targetPos.z);
                client.world.addEntity(fake);
                var walls = (dev.bytephase.setting.BooleanSetting) esp.getSettings().stream()
                        .filter(s -> s.getName().equals("ThroughWalls")).findFirst().orElseThrow();
                try {
                    mode(esp, "Targets").setValue("Players");
                    walls.setValue(true);
                    esp.setEnabled(true);
                    for (String style : new String[]{"Model Filled", "Model Glow", "Model Chams", "Model Wireframe", "Model Hologram"}) {
                        mode(esp, "Style").setValue(style);
                        require(esp.shouldRenderHiddenModel(fake), style + " did not retain hidden target");
                    }
                    var renderer = client.worldRenderer;
                    var chunksField = renderer.getClass().getDeclaredField("chunks");
                    chunksField.setAccessible(true);
                    var chunks = (net.minecraft.client.render.BuiltChunkStorage) chunksField.get(renderer);
                    var getChunk = chunks.getClass().getDeclaredMethod("getRenderedChunk", BlockPos.class);
                    getChunk.setAccessible(true);
                    var chunk = (net.minecraft.client.render.chunk.ChunkBuilder.BuiltChunk) getChunk.invoke(chunks, fake.getBlockPos());
                    require(chunk != null, "No render section for hidden ESP test");
                    var previous = chunk.currentRenderData.get();
                    var fill = renderer.getClass().getDeclaredMethod("fillEntityRenderStates", net.minecraft.client.render.Camera.class,
                            net.minecraft.client.render.Frustum.class, net.minecraft.client.render.RenderTickCounter.class,
                            net.minecraft.client.render.state.WorldRenderState.class);
                    fill.setAccessible(true);
                    var cameraPos = camera.getCameraPos();
                    var view = new org.joml.Matrix4f().rotation(camera.getRotation().conjugate(new org.joml.Quaternionf()));
                    var frustum = new net.minecraft.client.render.Frustum(view,
                            new org.joml.Matrix4f().perspective((float) Math.toRadians(170), 1, 0.05f, 512));
                    frustum.setPosition(cameraPos.x, cameraPos.y, cameraPos.z);
                    try {
                        chunk.currentRenderData.set(net.minecraft.client.render.chunk.ChunkRenderData.HIDDEN);
                        for (boolean enabled : new boolean[]{true, false}) {
                            walls.setValue(enabled);
                            var state = new net.minecraft.client.render.state.WorldRenderState();
                            fill.invoke(renderer, camera, frustum, client.getRenderTickCounter(), state);
                            boolean submitted = state.entityRenderStates.stream().anyMatch(s ->
                                    s instanceof net.minecraft.client.render.entity.state.PlayerEntityRenderState p && p.id == fake.getId());
                            require(submitted == enabled, "Hidden render section submitted=" + submitted + " ThroughWalls=" + enabled);
                        }
                    } finally {
                        chunk.currentRenderData.set(previous);
                    }
                    var surface = dev.bytephase.render.ModelWireframeLayer.throughWallSurface(net.minecraft.util.Identifier.of("minecraft", "textures/entity/steve.png"));
                    require(surface.getRenderPipeline().getDepthTestFunction() == com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST,
                            "Chams surface still depth-tests through walls");
                    walls.setValue(false);
                    require(!esp.shouldRenderHiddenModel(fake), "Hidden target bypassed disabled ThroughWalls");
                    walls.setValue(true);
                    mode(esp, "Targets").setValue("Monsters");
                    require(!esp.shouldRenderHiddenModel(fake), "Hidden target bypassed target filter");
                    mode(esp, "Targets").setValue("Players");
                    mode(esp, "Style").setValue("Box");
                    require(!esp.shouldRenderHiddenModel(fake), "Box ESP changed vanilla model culling");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                } finally {
                    fake.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                    esp.setEnabled(false);
                    walls.setValue(true);
                }

                var blockEsp = BytePhase.modules.get(dev.bytephase.module.render.BlockEsp.class);
                try {
                    blockEsp.setEnabled(true);
                    blockEsp.onTick();
                    var boxes = (java.util.List<net.minecraft.util.math.Box>) getObjectField(blockEsp, "boxes");
                    var marker = new net.minecraft.util.math.Box(client.player.getBlockPos());
                    boxes.add(marker);
                    var scanning = blockEsp.getClass().getDeclaredField("scanning");
                    scanning.setAccessible(true);
                    scanning.setBoolean(blockEsp, false);
                    var cooldown = blockEsp.getClass().getDeclaredField("restartCooldown");
                    cooldown.setAccessible(true);
                    cooldown.setInt(blockEsp, 0);
                    blockEsp.onTick();
                    require(boxes.contains(marker), "Block ESP cleared completed results during rescan");
                    var found = (java.util.Set<BlockPos>) getObjectField(blockEsp, "found");
                    require(found.stream().noneMatch(pos -> pos instanceof BlockPos.Mutable), "Block ESP retained mutable scan positions");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                } finally {
                    blockEsp.setEnabled(false);
                }
            });
            context.runOnClient(client -> {
                // Screenshot comparisons require a stationary, repeatable view.
                // Earlier input/movement scenarios can otherwise carry us past
                // the fake player before the armor comparison is captured.
                KeyBinding.unpressAll();
                client.player.setVelocityClient(net.minecraft.util.math.Vec3d.ZERO);
                client.player.setYaw(0.0f);
                client.player.setPitch(0.0f);
                var esp = BytePhase.modules.get(PlayerEsp.class);
                mode(esp, "Style").setValue("Model Filled");
                mode(esp, "Targets").setValue("Players");
                ((dev.bytephase.setting.ColorSetting) esp.getSettings().stream()
                        .filter(s -> s.getName().equals("PlayerColor")).findFirst().orElseThrow()).setValue(0xFFFF00FF);
                ((dev.bytephase.setting.NumberSetting) esp.getSettings().stream()
                        .filter(s -> s.getName().equals("ModelOpacity")).findFirst().orElseThrow()).setValue(100.0);
                esp.setEnabled(true);
                try {
                    var spawn = dev.bytephase.command.FakePlayerCommand.class.getDeclaredMethod("spawn", String.class);
                    spawn.setAccessible(true);
                    require((int) spawn.invoke(null, "EspTest") == 1, "Could not spawn ESP test player");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            context.waitTicks(8);
            context.runOnClient(client -> {
                var fake = client.world.getPlayers().stream().filter(p -> p.getName().getString().equals("EspTest"))
                        .findFirst().orElseThrow();
                fake.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                fake.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                fake.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                fake.equipStack(net.minecraft.entity.EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
            });
            context.waitTicks(5);
            var visibleArmor = context.takeScreenshot("armor-visible");
            context.runOnClient(client -> BytePhase.modules.get(dev.bytephase.module.render.HideArmor.class).setEnabled(true));
            context.waitTicks(5);
            var hiddenArmor = context.takeScreenshot("armor-hidden");
            try {
                var before = javax.imageio.ImageIO.read(visibleArmor.toFile());
                var after = javax.imageio.ImageIO.read(hiddenArmor.toFile());
                int changed = 0;
                for (int y = before.getHeight() / 3; y < before.getHeight() * 2 / 3; y++) {
                    for (int x = before.getWidth() / 3; x < before.getWidth() * 2 / 3; x++) {
                        int a = before.getRGB(x, y), b = after.getRGB(x, y);
                        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
                        int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
                        if (ag > ar * 1.3 && ab > ar * 1.3 && br > bg * 2 && bb > bg * 2) changed++;
                    }
                }
                require(changed > 100, "HideArmor did not hide diamond armor: " + changed);
            } catch (java.io.IOException e) { throw new AssertionError(e); }
            context.runOnClient(client -> {
                var fake = client.world.getPlayers().stream().filter(p -> p.getName().getString().equals("EspTest"))
                        .findFirst().orElseThrow();
                var names = BytePhase.modules.get(dev.bytephase.module.render.Nametags.class);
                ((dev.bytephase.setting.BooleanSetting) names.getSettings().stream()
                        .filter(s -> s.getName().equals("ArmorIcons")).findFirst().orElseThrow()).setValue(true);
                names.setEnabled(true);
                require(BytePhase.modules.getByName("HealthESP") == null, "Separate HealthESP module still registered");
                fake.setHealth(13.5f);
                fake.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_ABSORPTION).setBaseValue(4.0);
                fake.setAbsorptionAmount(4.0f);
                dev.bytephase.render.EntitySnapshot.refresh();
                names.onTick();
                var state = new net.minecraft.client.gui.render.state.GuiRenderState();
                var draw = new net.minecraft.client.gui.DrawContext(client, state, 0, 0);
                names.onRender2D(draw, 0);
                var itemNames = new java.util.ArrayList<String>();
                state.forEachItemElement(item -> itemNames.add(item.name()));
                require(itemNames.size() == 4, "Nametags did not draw four armor icons: " + itemNames);
                var texts = new java.util.ArrayList<String>();
                state.forEachTextElement(text -> {
                    var value = new StringBuilder();
                    text.orderedText.accept((index, style, codepoint) -> { value.appendCodePoint(codepoint); return true; });
                    texts.add(value.toString());
                });
                require(texts.stream().anyMatch(text -> text.contains("EspTest") && text.contains("13.5 HP +4.0")),
                        "Nametag did not render player health and absorption in the same label: " + texts);
                fake.setHealth(7.0f);
                var updated = new net.minecraft.client.gui.render.state.GuiRenderState();
                names.onRender2D(new net.minecraft.client.gui.DrawContext(client, updated, 0, 0), 0);
                var changedTexts = new java.util.ArrayList<String>();
                updated.forEachTextElement(text -> {
                    var value = new StringBuilder();
                    text.orderedText.accept((index, style, cp) -> { value.appendCodePoint(cp); return true; });
                    changedTexts.add(value.toString());
                });
                require(changedTexts.stream().anyMatch(text -> text.contains("EspTest") && text.contains("7.0 HP")),
                        "Nametag health did not update live: " + changedTexts);
                fake.setCustomName(net.minecraft.text.Text.literal("VanillaName"));
                fake.setCustomNameVisible(true);
                var renderer = client.getEntityRenderDispatcher();
                require(renderer.getAndUpdateRenderState(fake, 1.0f).displayName == null,
                        "Nametags did not suppress the vanilla player label");
                mode(names, "Targets").setValue("Monsters");
                require(renderer.getAndUpdateRenderState(fake, 1.0f).displayName != null,
                        "Nametags hid a player excluded by target settings");
                mode(names, "Targets").setValue("Players");
                require(!fake.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).isEmpty(), "HideArmor changed equipment");
                names.setEnabled(false);
                require(renderer.getAndUpdateRenderState(fake, 1.0f).displayName != null,
                        "Vanilla player label did not return after disabling Nametags");
                fake.setCustomName(null);
                fake.setCustomNameVisible(false);
                fake.setHealth(20.0f);
                fake.setAbsorptionAmount(0.0f);
                BytePhase.modules.get(dev.bytephase.module.render.HideArmor.class).setEnabled(false);
                for (var slot : new net.minecraft.entity.EquipmentSlot[]{net.minecraft.entity.EquipmentSlot.HEAD,
                        net.minecraft.entity.EquipmentSlot.CHEST, net.minecraft.entity.EquipmentSlot.LEGS,
                        net.minecraft.entity.EquipmentSlot.FEET}) fake.equipStack(slot, ItemStack.EMPTY);
            });
            var filledScreenshot = context.takeScreenshot("model-filled");
            try {
                var image = javax.imageio.ImageIO.read(filledScreenshot.toFile());
                int magenta = 0;
                for (int y = image.getHeight() / 3; y < image.getHeight() * 2 / 3; y++) {
                    for (int x = image.getWidth() / 3; x < image.getWidth() * 2 / 3; x++) {
                        int rgb = image.getRGB(x, y);
                        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
                        if (r > 80 && b > 80 && r > g * 2 && b > g * 2) magenta++;
                    }
                }
                require(magenta > 100, "Model Filled did not render a solid tinted player: " + magenta);
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }

            // The same model, one block behind a solid wall: the through-wall
            // model layer must still submit and draw it.
            var espWallBlocks = new java.util.LinkedHashMap<BlockPos, net.minecraft.block.BlockState>();
            context.runOnClient(client -> {
                var player = client.player;
                var direction = net.minecraft.util.math.Direction.fromHorizontalDegrees(player.getYaw());
                var base = player.getBlockPos().offset(direction);
                var side = direction.rotateYClockwise();
                for (int along = -1; along <= 1; along++) {
                    for (int up = 0; up <= 2; up++) {
                        var pos = base.offset(side, along).up(up);
                        espWallBlocks.put(pos.toImmutable(), client.world.getBlockState(pos));
                        client.world.setBlockState(pos, net.minecraft.block.Blocks.OBSIDIAN.getDefaultState());
                    }
                }
                booleanSetting(BytePhase.modules.get(PlayerEsp.class), "ThroughWalls").setValue(true);
            });
            var wallScreenshots = new java.util.LinkedHashMap<String, java.nio.file.Path>();
            for (String style : new String[]{"Model Filled", "Model Glow", "Model Chams",
                    "Model Wireframe", "Model Hologram"}) {
                context.runOnClient(client -> mode(BytePhase.modules.get(PlayerEsp.class), "Style")
                        .setValue(style));
                context.waitTicks(6);
                wallScreenshots.put(style,
                        context.takeScreenshot("model-through-wall-" + style.replace(' ', '-')));
            }
            context.runOnClient(client -> {
                espWallBlocks.forEach((pos, state) -> client.world.setBlockState(pos, state));
                espWallBlocks.clear();
            });
            for (var entry : wallScreenshots.entrySet()) {
                try {
                    var image = javax.imageio.ImageIO.read(entry.getValue().toFile());
                    int magenta = 0;
                    for (int y = image.getHeight() / 3; y < image.getHeight() * 2 / 3; y++) {
                        for (int x = image.getWidth() / 3; x < image.getWidth() * 2 / 3; x++) {
                            int rgb = image.getRGB(x, y);
                            int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
                            if (r > 80 && b > 80 && r > g * 2 && b > g * 2) magenta++;
                        }
                    }
                    require(magenta > 20, entry.getKey() + " disappeared behind a wall: " + magenta);
                } catch (java.io.IOException e) {
                    throw new AssertionError(e);
                }
            }
            context.runOnClient(client -> mode(BytePhase.modules.get(PlayerEsp.class), "Style")
                    .setValue("Model Filled"));
            context.runOnClient(client -> {
                var esp = BytePhase.modules.get(PlayerEsp.class);
                ((dev.bytephase.setting.NumberSetting) esp.getSettings().stream()
                        .filter(s -> s.getName().equals("ModelOpacity")).findFirst().orElseThrow()).setValue(35.0);
            });
            context.waitTicks(5);
            var transparentScreenshot = context.takeScreenshot("model-filled-transparent");
            try {
                var opaque = javax.imageio.ImageIO.read(filledScreenshot.toFile());
                var transparent = javax.imageio.ImageIO.read(transparentScreenshot.toFile());
                int faded = 0;
                for (int y = opaque.getHeight() / 3; y < opaque.getHeight() * 2 / 3; y++) {
                    for (int x = opaque.getWidth() / 3; x < opaque.getWidth() * 2 / 3; x++) {
                        int a = opaque.getRGB(x, y), b = transparent.getRGB(x, y);
                        int r = a >> 16 & 255, g = a >> 8 & 255, blue = a & 255;
                        if (r > 100 && blue > 100 && r > g * 2 && blue > g * 2
                                && (b >> 16 & 255) < r - 20 && (b & 255) < blue - 20) faded++;
                    }
                }
                require(faded > 100, "ModelOpacity did not make the model translucent: " + faded);
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
            context.runOnClient(client -> {
                var esp = BytePhase.modules.get(PlayerEsp.class);
                ((dev.bytephase.setting.NumberSetting) esp.getSettings().stream()
                        .filter(s -> s.getName().equals("ModelOpacity")).findFirst().orElseThrow()).setValue(100.0);
                ((dev.bytephase.setting.BooleanSetting) esp.getSettings().stream()
                        .filter(s -> s.getName().equals("FilledHeldItems")).findFirst().orElseThrow()).setValue(true);
                client.player.getInventory().setStack(client.player.getInventory().getSelectedSlot(), new ItemStack(Items.DIAMOND_SWORD));
                client.player.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.DIAMOND_SWORD));
            });
            context.waitTicks(15);
            var heldScreenshot = context.takeScreenshot("model-filled-held");
            try {
                var image = javax.imageio.ImageIO.read(heldScreenshot.toFile());
                int left = 0, right = 0;
                for (int y = image.getHeight() / 2; y < image.getHeight() - 30; y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int rgb = image.getRGB(x, y);
                        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
                        if (r > 80 && b > 80 && r > g * 2 && b > g * 2) {
                            if (x < image.getWidth() / 3) left++;
                            if (x > image.getWidth() * 2 / 3) right++;
                        }
                    }
                }
                require(left > 30 && right > 30, "First-person held item tint missing: " + left + ", " + right);
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
            context.runOnClient(client -> {
                var esp = BytePhase.modules.get(PlayerEsp.class);
                ((dev.bytephase.setting.BooleanSetting) esp.getSettings().stream()
                        .filter(s -> s.getName().equals("FilledHeldItems")).findFirst().orElseThrow()).setValue(false);
                client.player.getInventory().setStack(client.player.getInventory().getSelectedSlot(), ItemStack.EMPTY);
                client.player.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                var fake = client.world.getPlayers().stream().filter(p -> p.getName().getString().equals("EspTest"))
                        .findFirst().orElseThrow();
                fake.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                fake.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.DIAMOND_SWORD));
            });
            context.waitTicks(8);
            var otherItemsNormal = context.takeScreenshot("other-held-normal");
            context.runOnClient(client -> {
                var esp = BytePhase.modules.get(PlayerEsp.class);
                ((dev.bytephase.setting.BooleanSetting) esp.getSettings().stream()
                        .filter(s -> s.getName().equals("FilledHeldItems")).findFirst().orElseThrow()).setValue(true);
            });
            context.waitTicks(5);
            var otherItemsFilled = context.takeScreenshot("other-held-filled");
            try {
                var before = javax.imageio.ImageIO.read(otherItemsNormal.toFile());
                var after = javax.imageio.ImageIO.read(otherItemsFilled.toFile());
                int changed = 0;
                for (int y = 0; y < after.getHeight(); y++) {
                    for (int x = after.getWidth() / 4; x < after.getWidth() * 3 / 4; x++) {
                        int a = before.getRGB(x, y), b = after.getRGB(x, y);
                        int r = b >> 16 & 255, g = b >> 8 & 255, blue = b & 255;
                        if (r > 80 && blue > 80 && r > g * 2 && blue > g * 2
                                && (a >> 16 & 255) < r - 30) changed++;
                    }
                }
                require(changed > 20, "Other player's held items did not receive filled tint: " + changed);
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
            context.runOnClient(client -> {
                var esp = BytePhase.modules.get(PlayerEsp.class);
                ((dev.bytephase.setting.BooleanSetting) esp.getSettings().stream()
                        .filter(s -> s.getName().equals("FilledHeldItems")).findFirst().orElseThrow()).setValue(false);
                ((dev.bytephase.setting.BooleanSetting) esp.getSettings().stream()
                        .filter(s -> s.getName().equals("FilledCrystals")).findFirst().orElseThrow()).setValue(true);
                ((dev.bytephase.setting.ColorSetting) esp.getSettings().stream()
                        .filter(s -> s.getName().equals("OtherColor")).findFirst().orElseThrow()).setValue(0xFF00FF00);
                var crystal = new net.minecraft.entity.decoration.EndCrystalEntity(net.minecraft.entity.EntityType.END_CRYSTAL, client.world);
                var pos = client.player.getEntityPos().add(net.minecraft.util.math.Vec3d.fromPolar(0, client.player.getYaw()).multiply(3));
                crystal.setPosition(pos.x, pos.y, pos.z);
                client.world.addEntity(crystal);
            });
            context.waitTicks(5);
            var crystalScreenshot = context.takeScreenshot("model-filled-crystal");
            try {
                var image = javax.imageio.ImageIO.read(crystalScreenshot.toFile());
                // The crystal sits at the player's feet, so part of its model
                // projects below the middle of the screen; scan the whole frame
                // or camera bob decides whether the count passes.
                int green = 0;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int rgb = image.getRGB(x, y);
                        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
                        if (g > 100 && g > r * 3 && g > b * 3) green++;
                    }
                }
                require(green > 100, "Crystal filled model missing: " + green);
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
            context.runOnClient(client -> BytePhase.modules.get(PlayerEsp.class).setEnabled(false));

            world.getServer().runCommand("summon item ~ ~1 ~ {Item:{id:\"minecraft:stone\",count:1}}");
            context.waitTicks(3);
            context.runOnClient(client -> {
                BytePhase.modules.get(Crosshair.class).setEnabled(true);
                BytePhase.modules.get(SkyShaders.class).setEnabled(true);
                var esp = BytePhase.modules.get(PlayerEsp.class);
                mode(esp, "Style").setValue("Shader Glow");
                esp.setEnabled(true);
                var highlight = BytePhase.modules.get(BlockHighlight.class);
                mode(highlight, "Style").setValue("Shader Glow");
                highlight.setEnabled(true);
                var items = BytePhase.modules.get(ItemEsp.class);
                ((dev.bytephase.setting.BooleanSetting) items.getSettings().stream()
                        .filter(setting -> setting.getName().equals("Names")).findFirst().orElseThrow()).setValue(false);
                items.setEnabled(true);
            });
            context.waitTicks(10);
            context.runOnClient(client -> {
                var items = BytePhase.modules.get(ItemEsp.class);
                ((dev.bytephase.setting.BooleanSetting) items.getSettings().stream()
                        .filter(setting -> setting.getName().equals("Names")).findFirst().orElseThrow()).setValue(true);
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                require(!getBooleanField(dev.bytephase.render.ShaderCrosshairRenderer.class, "failed"),
                        "Crosshair shader failed");
                require(!getBooleanField(dev.bytephase.module.render.Crosshair.class,
                        BytePhase.modules.get(Crosshair.class), "shaderFailed"), "Crosshair used the fallback");
                BytePhase.modules.get(Crosshair.class).setEnabled(false);
                BytePhase.modules.get(SkyShaders.class).setEnabled(false);
                BytePhase.modules.get(PlayerEsp.class).setEnabled(false);
                BytePhase.modules.get(BlockHighlight.class).setEnabled(false);
                BytePhase.modules.get(ItemEsp.class).setEnabled(false);
            });

            context.runOnClient(client -> {
                // HoleFill: build a valid two-high hole with a solid ring and
                // check the predicate and the bounded scan both find it.
                var holeFill = BytePhase.modules.get(dev.bytephase.module.combat.HoleFill.class);
                BlockPos hole = client.player.getBlockPos().add(2, 0, 2);
                var sides = new net.minecraft.util.math.Direction[]{
                        net.minecraft.util.math.Direction.NORTH,
                        net.minecraft.util.math.Direction.SOUTH,
                        net.minecraft.util.math.Direction.EAST,
                        net.minecraft.util.math.Direction.WEST
                };
                var saved = new java.util.HashMap<BlockPos, net.minecraft.block.BlockState>();
                var affected = new java.util.ArrayList<BlockPos>();
                affected.add(hole);
                affected.add(hole.up());
                affected.add(hole.down());
                for (var side : sides) {
                    affected.add(hole.offset(side));
                    affected.add(hole.offset(side).up());
                }
                for (BlockPos pos : affected) saved.put(pos, client.world.getBlockState(pos));
                try {
                    var air = net.minecraft.block.Blocks.AIR.getDefaultState();
                    var solid = net.minecraft.block.Blocks.OBSIDIAN.getDefaultState();
                    client.world.setBlockState(hole, air);
                    client.world.setBlockState(hole.up(), air);
                    client.world.setBlockState(hole.down(), solid);
                    for (var side : sides) {
                        client.world.setBlockState(hole.offset(side), solid);
                        client.world.setBlockState(hole.offset(side).up(), solid);
                    }
                    var isHole = holeFill.getClass().getDeclaredMethod("isHole", BlockPos.class);
                    isHole.setAccessible(true);
                    require((boolean) isHole.invoke(holeFill, hole), "HoleFill rejected a valid hole");
                    var rescan = holeFill.getClass().getDeclaredMethod("rescan", BlockPos.class);
                    rescan.setAccessible(true);
                    rescan.invoke(holeFill, client.player.getBlockPos());
                    var found = (java.util.List<?>) getObjectField(holeFill, "holes");
                    require(found.contains(hole), "HoleFill scan missed a valid hole");
                    client.world.setBlockState(hole.down(), air);
                    require(!(boolean) isHole.invoke(holeFill, hole),
                            "HoleFill accepted a hole without a floor");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                } finally {
                    saved.forEach((pos, state) -> client.world.setBlockState(pos, state));
                }
            });

            context.runOnClient(client -> {
                var player = client.player;
                int oldFood = player.getHungerManager().getFoodLevel();
                boolean oldForward = client.options.forwardKey.isPressed();
                boolean oldSprint = player.isSprinting();
                try {
                    var keepSprint = BytePhase.modules.get(dev.bytephase.module.combat.KeepSprint.class);
                    player.getHungerManager().setFoodLevel(20);
                    client.options.forwardKey.setPressed(true);
                    player.setSprinting(false);
                    keepSprint.setEnabled(true);
                    keepSprint.onAttack(player, player);
                    keepSprint.onTick();
                    require(player.isSprinting(), "KeepSprint did not restore sprint after a hit");
                    keepSprint.setEnabled(false);

                    var reset = BytePhase.modules.get(dev.bytephase.module.combat.SprintReset.class);
                    ((dev.bytephase.setting.BooleanSetting) reset.getSettings().stream()
                            .filter(s -> s.getName().equals("PlayersOnly")).findFirst().orElseThrow())
                            .setValue(false);
                    player.setSprinting(true);
                    reset.onAttack(player, player);
                    require(!player.isSprinting(), "SprintReset did not clear sprint after a hit");
                    reset.setEnabled(false);
                } finally {
                    client.options.forwardKey.setPressed(oldForward);
                    player.getHungerManager().setFoodLevel(oldFood);
                    player.setSprinting(oldSprint);
                }
            });

            context.runOnClient(client -> {
                BytePhase.modules.get(dev.bytephase.module.combat.NoHitDelay.class).setEnabled(true);
                setIntField(net.minecraft.client.MinecraftClient.class, client, "attackCooldown", 10);
            });
            context.waitTicks(1);
            context.runOnClient(client -> {
                require(getIntField(net.minecraft.client.MinecraftClient.class, client, "attackCooldown") == 0,
                        "NoHitDelay did not clear the missed-attack cooldown");
                BytePhase.modules.get(dev.bytephase.module.combat.NoHitDelay.class).setEnabled(false);
            });

            context.runOnClient(client -> {
                // ThrowPot must refuse to stay on when nothing can be thrown.
                var throwPot = BytePhase.modules.get(dev.bytephase.module.combat.ThrowPot.class);
                throwPot.setEnabled(true);
                require(!throwPot.isEnabled(), "ThrowPot stayed enabled without a potion");
            });

            world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:splash_potion"
                    + "[minecraft:potion_contents={potion:\"minecraft:healing\"}] 1");
            context.waitFor(client -> client.player.getInventory().getStack(0).isOf(Items.SPLASH_POTION));
            context.runOnClient(client -> {
                require(dev.bytephase.util.PotionUtil.isHealingSplash(
                                client.player.getInventory().getStack(0)),
                        "PotionUtil did not recognise a healing splash potion");
                client.player.getInventory().setSelectedSlot(0);
                BytePhase.modules.get(dev.bytephase.module.combat.ThrowPot.class).setEnabled(true);
            });
            context.waitTicks(4);
            context.runOnClient(client -> {
                var throwPot = BytePhase.modules.get(dev.bytephase.module.combat.ThrowPot.class);
                throwPot.setEnabled(false);
                require(client.player.getInventory().getStack(0).isEmpty(),
                        "ThrowPot did not throw its healing potion: "
                                + client.player.getInventory().getStack(0));
            });
            world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:air");

            context.runOnClient(client -> {
                var player = client.player;
                var soup = BytePhase.modules.get(dev.bytephase.module.combat.AutoSoup.class);
                int selected = player.getInventory().getSelectedSlot();
                var previous = player.getInventory().getStack(2);
                float oldHealth = player.getHealth();
                try {
                    player.getInventory().setStack(2, new ItemStack(Items.MUSHROOM_STEW, 4));
                    player.getInventory().setSelectedSlot(0);
                    player.setHealth(10.0f);
                    soup.setEnabled(true);
                    soup.onTick();
                    require(player.getInventory().getSelectedSlot() == 2,
                            "AutoSoup did not select the soup slot");
                    require(player.isUsingItem() && player.getActiveItem().isOf(Items.MUSHROOM_STEW),
                            "AutoSoup did not start eating");
                    soup.setEnabled(false);
                    require(player.getInventory().getSelectedSlot() == 0,
                            "AutoSoup did not restore the hotbar slot");
                    require(!player.isUsingItem(), "AutoSoup left the player eating");
                } finally {
                    player.getInventory().setStack(2, previous);
                    player.getInventory().setSelectedSlot(selected);
                    player.setHealth(oldHealth);
                }
            });

            world.getServer().runCommand("gamemode creative @a");
            context.waitFor(client -> client.player.isCreative());
            context.waitFor(client -> client.player.isOnGround());

            var trapSides = new net.minecraft.util.math.Direction[]{
                    net.minecraft.util.math.Direction.NORTH,
                    net.minecraft.util.math.Direction.SOUTH,
                    net.minecraft.util.math.Direction.EAST,
                    net.minecraft.util.math.Direction.WEST
            };

            // Placement round-trips through the integrated server, so the
            // block state has to be checked on a later client tick.
            BlockPos[] trapFeet = new BlockPos[1];

            // Inventory setup goes through the server; editing the client
            // inventory directly desyncs the container synchronizer.
            world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:obsidian 64");
            context.waitFor(client -> client.player.getInventory().getStack(0).isOf(Items.OBSIDIAN));
            context.runOnClient(client -> {
                var player = client.player;
                trapFeet[0] = player.getBlockPos();
                player.getInventory().setSelectedSlot(0);
                BytePhase.modules.get(dev.bytephase.module.combat.FeetTrap.class).setEnabled(true);
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                var feetTrap = BytePhase.modules.get(dev.bytephase.module.combat.FeetTrap.class);
                feetTrap.setEnabled(false);
                for (var side : trapSides) {
                    BlockPos pos = trapFeet[0].offset(side);
                    require(client.world.getBlockState(pos).isOf(net.minecraft.block.Blocks.OBSIDIAN),
                            "FeetTrap missed the foot ring at " + side + ": "
                                    + client.world.getBlockState(pos));
                }

            });
            world.getServer().runCommand(clearArea(trapFeet[0]));
            world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:air");
            // Server commands are queued on the server thread; wait for the
            // clear to reach the client before the next module places here.
            context.waitFor(client -> client.world.getBlockState(trapFeet[0].north()).isAir()
                    && client.world.getBlockState(trapFeet[0].south()).isAir()
                    && client.world.getBlockState(trapFeet[0].east()).isAir()
                    && client.world.getBlockState(trapFeet[0].west()).isAir());

            context.waitFor(client -> client.player.isOnGround());
            BlockPos[] fillFeet = new BlockPos[1];
            world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:obsidian 64");
            context.waitFor(client -> client.player.getInventory().getStack(0).isOf(Items.OBSIDIAN));
            context.runOnClient(client -> {
                var player = client.player;
                fillFeet[0] = player.getBlockPos();
                player.getInventory().setSelectedSlot(0);
                BytePhase.modules.get(dev.bytephase.module.combat.SelfFill.class).setEnabled(true);
            });
            context.waitTicks(6);
            context.runOnClient(client -> {
                var selfFill = BytePhase.modules.get(dev.bytephase.module.combat.SelfFill.class);
                boolean filled = false;
                for (var side : trapSides) {
                    if (client.world.getBlockState(fillFeet[0].offset(side))
                            .isOf(net.minecraft.block.Blocks.OBSIDIAN)) {
                        filled = true;
                    }
                }
                require(filled, "SelfFill placed nothing next to the player: enabled="
                        + selfFill.isEnabled() + " feet=" + fillFeet[0]
                        + " player=" + client.player.getBlockPos()
                        + " n=" + client.world.getBlockState(fillFeet[0].north())
                        + " s=" + client.world.getBlockState(fillFeet[0].south())
                        + " e=" + client.world.getBlockState(fillFeet[0].east())
                        + " w=" + client.world.getBlockState(fillFeet[0].west())
                        + " slot=" + dev.bytephase.util.BlockPlacementUtil.findHotbarBlock(
                                client, net.minecraft.block.Blocks.OBSIDIAN));
                require(!selfFill.isEnabled(), "SelfFill did not disable after one placement");
            });
            world.getServer().runCommand(clearArea(fillFeet[0]));
            world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:air");
            context.waitFor(client -> client.world.getBlockState(fillFeet[0].north()).isAir()
                    && client.world.getBlockState(fillFeet[0].south()).isAir()
                    && client.world.getBlockState(fillFeet[0].east()).isAir()
                    && client.world.getBlockState(fillFeet[0].west()).isAir());

            context.waitFor(client -> client.player.isOnGround());
            BlockPos[] ringFeet = new BlockPos[1];
            var ringSides = new net.minecraft.util.math.Direction[]{
                    net.minecraft.util.math.Direction.NORTH,
                    net.minecraft.util.math.Direction.SOUTH,
                    net.minecraft.util.math.Direction.EAST,
                    net.minecraft.util.math.Direction.WEST
            };
            world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:obsidian 64");
            context.waitFor(client -> client.player.getInventory().getStack(0).isOf(Items.OBSIDIAN));
            context.runOnClient(client -> {
                var player = client.player;
                ringFeet[0] = player.getBlockPos();
                player.getInventory().setSelectedSlot(0);
                var selfTrap = BytePhase.modules.get(dev.bytephase.module.combat.SelfTrap.class);
                ((dev.bytephase.setting.NumberSetting) selfTrap.getSettings().stream()
                        .filter(s -> s.getName().equals("BlocksPerTick")).findFirst().orElseThrow())
                        .setValue(4.0);
                selfTrap.setEnabled(true);
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                var selfTrap = BytePhase.modules.get(dev.bytephase.module.combat.SelfTrap.class);
                selfTrap.setEnabled(false);
                for (var side : ringSides) {
                    require(client.world.getBlockState(ringFeet[0].offset(side))
                                    .isOf(net.minecraft.block.Blocks.OBSIDIAN),
                            "SelfTrap missed the foot ring at " + side + ": "
                                    + client.world.getBlockState(ringFeet[0].offset(side)));
                }
            });
            world.getServer().runCommand(clearArea(ringFeet[0]));
            world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:air");
            context.waitTicks(2);

            context.runOnClient(client -> {
                var breadCrumbs = BytePhase.modules.get(dev.bytephase.module.render.BreadCrumbs.class);
                breadCrumbs.setEnabled(true);
            });
            world.getServer().runCommand("tp @a ~4 ~ ~4");
            context.waitTicks(20);
            context.runOnClient(client -> {
                var breadCrumbs = BytePhase.modules.get(dev.bytephase.module.render.BreadCrumbs.class);
                try {
                    int points = ((java.util.List<?>) getObjectField(breadCrumbs, "points")).size();
                    require(points >= 2, "BreadCrumbs recorded " + points + " path points for a 4 block walk");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
                breadCrumbs.setEnabled(false);
                try {
                    require(((java.util.List<?>) getObjectField(breadCrumbs, "points")).isEmpty(),
                            "BreadCrumbs kept its path after being disabled");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });

            context.runOnClient(client -> {
                var skeleton = BytePhase.modules.get(dev.bytephase.module.render.Skeleton.class);
                try {
                    var segmentsFor = skeleton.getClass().getDeclaredMethod("segmentsFor",
                            net.minecraft.entity.player.PlayerEntity.class);
                    segmentsFor.setAccessible(true);
                    var segments = (java.util.List<?>) segmentsFor.invoke(null, client.player);
                    require(segments.size() == 4, "Skeleton built " + segments.size() + " segments");
                    var hips = (net.minecraft.util.math.Vec3d) segments.get(0);
                    var shoulders = (net.minecraft.util.math.Vec3d) segments.get(1);
                    require(shoulders.y > hips.y, "Skeleton shoulders are not above the hips");
                    require(hips.y > client.player.getY(), "Skeleton was not relative to the player");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
                // The draw pass reads the frame matrices; with no frame open it
                // must stay a no-op rather than throw.
                skeleton.setEnabled(true);
                skeleton.onRender3D(null, null, 0.0f);
                skeleton.setEnabled(false);
            });

            context.runOnClient(client -> {
                var noWeather = BytePhase.modules.get(dev.bytephase.module.render.NoWeather.class);
                client.world.setRainGradient(1.0f);
                client.world.setThunderGradient(1.0f);
                require(client.world.getRainGradient(1.0f) == 1.0f, "NoWeather test could not start the rain");
                noWeather.setEnabled(true);
                require(client.world.getRainGradient(1.0f) == 0.0f, "NoWeather left the rain gradient running");
                require(client.world.getThunderGradient(1.0f) == 0.0f, "NoWeather left the thunder gradient running");
                noWeather.setEnabled(false);
                require(client.world.getRainGradient(1.0f) == 1.0f, "NoWeather did not restore the rain");
                client.world.setRainGradient(0.0f);
                client.world.setThunderGradient(0.0f);
            });

            context.runOnClient(client -> {
                var oldPotions = BytePhase.modules.get(dev.bytephase.module.render.OldPotions.class);
                var contents = new net.minecraft.component.type.PotionContentsComponent(
                        java.util.Optional.of(net.minecraft.potion.Potions.SWIFTNESS),
                        java.util.Optional.of(0xFF00FF),
                        java.util.List.of(),
                        java.util.Optional.empty());
                int vanilla = contents.getColor();
                require(vanilla == 0xFF00FF, "OldPotions baseline colour was " + Integer.toHexString(vanilla));
                oldPotions.setEnabled(true);
                int legacy = contents.getColor();
                int expected = net.minecraft.component.type.PotionContentsComponent
                        .mixColors(contents.getEffects())
                        .orElse(net.minecraft.component.type.PotionContentsComponent.EFFECTLESS_COLOR);
                require(legacy == expected, "OldPotions used " + Integer.toHexString(legacy)
                        + " instead of the effect blend " + Integer.toHexString(expected));
                require(legacy != vanilla, "OldPotions kept the custom colour");
                oldPotions.setEnabled(false);
                require(contents.getColor() == vanilla, "OldPotions did not restore the vanilla colour");
            });

            context.runOnClient(client -> {
                var viewClip = BytePhase.modules.get(dev.bytephase.module.render.ViewClip.class);
                require(viewClip.resolve(4.0f, 4.0f) == 4.0f, "ViewClip changed the default distance");
                require(viewClip.resolve(4.0f, 1.5f) == 1.5f, "ViewClip replaced the clipped distance");
                require(!booleanSetting(viewClip, "IgnoreBlocks").getValue(),
                        "ViewClip clips into blocks by default");
                viewClip.setEnabled(true);
                booleanSetting(viewClip, "IgnoreBlocks").setValue(true);
                require(viewClip.resolve(4.0f, 1.5f) == 4.0f, "ViewClip did not bypass the block clip");
                booleanSetting(viewClip, "IgnoreBlocks").setValue(false);
                numberSetting(viewClip, "Distance").setValue(2.0);
                require(viewClip.resolve(4.0f, 3.5f) == 2.0f, "ViewClip ignored the distance cap");
                numberSetting(viewClip, "Distance").setValue(4.0);
                viewClip.setEnabled(false);
                require(viewClip.resolve(4.0f, 1.5f) == 1.5f, "ViewClip kept applying after being disabled");
            });

            // A shield in the offhand makes the first-person pass run the
            // offhand branch every frame while SmallShield is on.
            world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:shield");
            context.waitFor(client -> client.player.getOffHandStack().isOf(Items.SHIELD));
            context.runOnClient(client -> {
                var smallShield = BytePhase.modules.get(dev.bytephase.module.render.SmallShield.class);
                var viewModel = BytePhase.modules.get(dev.bytephase.module.render.ViewModel.class);
                require(smallShield.scale() < 1.0f, "SmallShield does not shrink the offhand");
                require(viewModel.scale() == 1.0f && viewModel.offsetX() == 0.0f
                        && viewModel.offsetY() == 0.0f && viewModel.offsetZ() == 0.0f,
                        "ViewModel defaults are not neutral");
                smallShield.setEnabled(true);
                viewModel.setEnabled(true);
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                var smallShield = BytePhase.modules.get(dev.bytephase.module.render.SmallShield.class);
                var viewModel = BytePhase.modules.get(dev.bytephase.module.render.ViewModel.class);
                require(smallShield.isEnabled() && viewModel.isEnabled(),
                        "A first-person transform disabled itself while rendering");
                require(client.player.getOffHandStack().isOf(Items.SHIELD),
                        "SmallShield changed the equipped offhand item");
                smallShield.setEnabled(false);
                viewModel.setEnabled(false);
            });
            world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:air");
            context.waitTicks(2);

            context.runOnClient(client -> {
                var extraTab = BytePhase.modules.get(dev.bytephase.module.render.ExtraTab.class);
                var playerListHud = client.inGameHud.getPlayerListHud();
                try {
                    var collect = playerListHud.getClass().getDeclaredMethod("collectPlayerEntries");
                    collect.setAccessible(true);
                    var entries = (java.util.List<?>) collect.invoke(playerListHud);
                    require(!entries.isEmpty(), "ExtraTab emptied the player list");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
                extraTab.setEnabled(true);
                require(extraTab.limit() == 200L, "ExtraTab default limit was " + extraTab.limit());
                extraTab.setEnabled(false);
            });

            context.runOnClient(client -> {
                var info = BytePhase.modules.get(dev.bytephase.module.render.InventoryInfo.class);
                var shulker = new ItemStack(Items.SHULKER_BOX);
                shulker.set(net.minecraft.component.DataComponentTypes.CONTAINER,
                        net.minecraft.component.type.ContainerComponent.fromStacks(java.util.List.of(
                                new ItemStack(Items.DIAMOND, 3),
                                new ItemStack(Items.GOLD_INGOT, 5))));
                var lines = new java.util.ArrayList<net.minecraft.text.Text>();
                info.append(shulker, lines::add);
                require(lines.isEmpty(), "InventoryInfo listed contents while disabled");
                info.setEnabled(true);
                info.append(shulker, lines::add);
                require(lines.size() == 3, "InventoryInfo added " + lines.size() + " lines for two stacks");
                require(lines.get(0).getString().contains("8"),
                        "InventoryInfo miscounted the contents: " + lines.get(0).getString());
                require(lines.get(1).getString().contains("3")
                                && lines.get(1).getString().toLowerCase(java.util.Locale.ROOT).contains("diamond"),
                        "InventoryInfo missed the diamonds: " + lines.get(1).getString());

                int before = lines.size();
                info.append(new ItemStack(Items.SHULKER_BOX), lines::add);
                require(lines.size() == before, "InventoryInfo listed an empty shulker box");

                var book = new ItemStack(Items.ENCHANTED_BOOK);
                var enchantments = client.world.getRegistryManager()
                        .getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
                net.minecraft.enchantment.EnchantmentHelper.apply(book, builder -> builder.add(
                        enchantments.getEntry(net.minecraft.enchantment.Enchantments.SHARPNESS.getValue())
                                .orElseThrow(), 5));
                info.append(book, lines::add);
                require(lines.size() > before, "InventoryInfo ignored a stored enchantment");
                require(lines.getLast().getString().contains("Sharpness")
                                || lines.getLast().getString().contains("sharpness"),
                        "InventoryInfo did not name the enchantment: " + lines.getLast().getString());

                // The same lines must reach a real tooltip, which is the path
                // the mixin injects into.
                var tooltip = shulker.getTooltip(net.minecraft.item.Item.TooltipContext.create(client.world),
                        client.player, net.minecraft.item.tooltip.TooltipType.BASIC);
                boolean contentsListed = tooltip.stream()
                        .anyMatch(text -> text.getString().contains("Contents:"));
                require(contentsListed, "InventoryInfo did not reach the item tooltip: " + tooltip);
                info.setEnabled(false);
            });

            context.runOnClient(client -> {
                var betterChat = BytePhase.modules.get(dev.bytephase.module.render.BetterChat.class);
                var chatHud = client.inGameHud.getChatHud();
                try {
                    var messages = (java.util.List<?>) getObjectField(chatHud, "messages");
                    betterChat.setEnabled(true);
                    chatHud.clear(false);
                    for (int i = 0; i < 150; i++) {
                        chatHud.addMessage(net.minecraft.text.Text.literal("line " + i));
                    }
                    require(messages.size() == 150,
                            "BetterChat kept " + messages.size() + " of 150 messages (limit "
                                    + betterChat.historyLimit() + ")");
                    betterChat.setEnabled(false);
                    chatHud.clear(false);
                    for (int i = 0; i < 150; i++) {
                        chatHud.addMessage(net.minecraft.text.Text.literal("line " + i));
                    }
                    require(messages.size() == 100,
                            "Disabling BetterChat left the vanilla limit at " + messages.size());
                    chatHud.clear(false);

                    betterChat.setEnabled(true);
                    client.setScreen(new net.minecraft.client.gui.screen.ChatScreen("", false));
                    Object chatField = getObjectField(client.currentScreen, "chatField");
                    int maxLength = getIntFieldInHierarchy(chatField, "maxLength");
                    require(maxLength == betterChat.messageLengthLimit(),
                            "BetterChat set the chat field to " + maxLength);
                    client.setScreen(null);
                    betterChat.setEnabled(false);
                    client.setScreen(new net.minecraft.client.gui.screen.ChatScreen("", false));
                    chatField = getObjectField(client.currentScreen, "chatField");
                    require(getIntFieldInHierarchy(chatField, "maxLength") == 256,
                            "Disabling BetterChat left the chat field length raised");
                    client.setScreen(null);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });

            context.runOnClient(client -> {
                var player = client.player;
                var antiHunger = BytePhase.modules.get(dev.bytephase.module.movement.AntiHunger.class);
                var startSprint = new net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket(
                        player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_SPRINTING);
                var stopSprint = new net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket(
                        player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.STOP_SPRINTING);
                antiHunger.setEnabled(true);
                require(antiHunger.onPacketSend(startSprint), "AntiHunger let the sprint start packet through");
                require(!antiHunger.onPacketSend(stopSprint), "AntiHunger dropped the sprint stop packet");
                antiHunger.setEnabled(false);
                require(!antiHunger.onPacketSend(startSprint), "AntiHunger kept dropping packets after being disabled");

                var antiLevitation = BytePhase.modules.get(dev.bytephase.module.movement.AntiLevitation.class);
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.LEVITATION, 200));
                require(player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.LEVITATION),
                        "AntiLevitation test could not apply levitation");
                antiLevitation.setEnabled(true);
                require(!player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.LEVITATION),
                        "AntiLevitation kept an already-active levitation effect");
                var levitationPacket = new net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket(
                        player.getId(), new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.LEVITATION, 200), true);
                var speedPacket = new net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket(
                        player.getId(), new net.minecraft.entity.effect.StatusEffectInstance(
                                net.minecraft.entity.effect.StatusEffects.SPEED, 200), true);
                require(antiLevitation.onPacketReceive(levitationPacket),
                        "AntiLevitation let a levitation packet through");
                require(!antiLevitation.onPacketReceive(speedPacket),
                        "AntiLevitation dropped an unrelated effect packet");
                antiLevitation.setEnabled(false);

                var noSlow = BytePhase.modules.get(dev.bytephase.module.movement.NoSlow.class);
                boolean soulSandSlow = net.minecraft.block.Blocks.SOUL_SAND.getVelocityMultiplier() < 1.0f;
                require(soulSandSlow, "Soul sand has no slowdown to remove");
                boolean wasSneaking = player.isSneaking();
                var oldPose = player.getPose();
                player.setSneaking(true);
                player.setPose(net.minecraft.entity.EntityPose.CROUCHING);
                try {
                    var sneakingPose = player.getClass().getDeclaredField("inSneakingPose");
                    sneakingPose.setAccessible(true);
                    sneakingPose.setBoolean(player, true);
                    require(player.shouldSlowDown(), "Sneak test did not start from a slowed state");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
                noSlow.setEnabled(true);
                require(net.minecraft.block.Blocks.SOUL_SAND.getVelocityMultiplier() == 1.0f,
                        "NoSlow kept the soul sand velocity multiplier");
                require(!player.shouldSlowDown(), "NoSlow kept the sneak slowdown");
                noSlow.setEnabled(false);
                require(net.minecraft.block.Blocks.SOUL_SAND.getVelocityMultiplier() < 1.0f,
                        "Disabling NoSlow left soul sand speeded up");
                require(player.shouldSlowDown(), "Disabling NoSlow left the sneak slowdown removed");
                try {
                    var sneakingPose = player.getClass().getDeclaredField("inSneakingPose");
                    sneakingPose.setAccessible(true);
                    sneakingPose.setBoolean(player, false);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
                player.setSneaking(wasSneaking);
                player.setPose(oldPose);

                var iceSpeed = BytePhase.modules.get(dev.bytephase.module.movement.IceSpeed.class);
                float iceFriction = net.minecraft.block.Blocks.ICE.getSlipperiness();
                require(iceFriction > 0.9f, "Vanilla ice friction was " + iceFriction);
                iceSpeed.setEnabled(true);
                require(net.minecraft.block.Blocks.ICE.getSlipperiness() == iceSpeed.friction(),
                        "IceSpeed did not change ice friction");
                require(net.minecraft.block.Blocks.PACKED_ICE.getSlipperiness() == iceSpeed.friction()
                                && net.minecraft.block.Blocks.BLUE_ICE.getSlipperiness() == iceSpeed.friction(),
                        "IceSpeed missed packed or blue ice");
                require(net.minecraft.block.Blocks.STONE.getSlipperiness() == 0.6f,
                        "IceSpeed changed the friction of a non-ice block");
                iceSpeed.setEnabled(false);
                require(net.minecraft.block.Blocks.ICE.getSlipperiness() == iceFriction,
                        "Disabling IceSpeed left ice friction changed");
            });

            context.runOnClient(client -> {
                var player = client.player;
                var tickShift = BytePhase.modules.get(dev.bytephase.module.movement.TickShift.class);
                var fastAccel = BytePhase.modules.get(dev.bytephase.module.movement.FastAccel.class);
                var yawModule = BytePhase.modules.get(dev.bytephase.module.movement.Yaw.class);
                var velocity = player.getVelocity();
                boolean onGround = player.isOnGround();
                float yaw = player.getYaw();
                boolean forward = client.options.forwardKey.isPressed();
                client.options.forwardKey.setPressed(false);
                try {
                    player.setOnGround(true);
                    player.setVelocityClient(net.minecraft.util.math.Vec3d.ZERO);
                    tickShift.setEnabled(true);
                    tickShift.onTick();
                    tickShift.onTick();
                    require(tickShift.storedTicks() == 2,
                            "TickShift banked " + tickShift.storedTicks() + " ticks while standing still");
                    client.options.forwardKey.setPressed(true);
                    player.setVelocityClient(new net.minecraft.util.math.Vec3d(0.05, 0, 0));
                    tickShift.onTick();
                    require(tickShift.storedTicks() == 1, "TickShift did not spend a banked tick");
                    require(player.getVelocity().horizontalLength() > 0.05,
                            "TickShift did not add speed when spending charge");
                    tickShift.setEnabled(false);
                    require(tickShift.storedTicks() == 0, "TickShift kept its charge after being disabled");

                    fastAccel.setEnabled(true);
                    player.setVelocityClient(new net.minecraft.util.math.Vec3d(0.05, 0, 0));
                    fastAccel.onTick();
                    require(Math.abs(player.getVelocity().horizontalLength() - 0.13) < 0.001,
                            "FastAccel moved to " + player.getVelocity().horizontalLength()
                                    + " instead of 0.13");
                    fastAccel.setEnabled(false);
                    player.setVelocityClient(new net.minecraft.util.math.Vec3d(0.05, 0, 0));
                    fastAccel.onTick();
                    require(Math.abs(player.getVelocity().horizontalLength() - 0.05) < 0.001,
                            "FastAccel kept accelerating after being disabled");

                    yawModule.setEnabled(true);
                    require(player.getYaw() == 0.0f, "Yaw did not apply its default angle");
                    numberSetting(yawModule, "Yaw").setValue(135.0);
                    yawModule.onTick();
                    require(player.getYaw() == 135.0f, "Yaw did not track its setting");
                    yawModule.setEnabled(false);
                    player.setYaw(45.0f);
                    yawModule.onTick();
                    require(player.getYaw() == 45.0f, "Yaw kept locking after being disabled");
                } finally {
                    tickShift.setEnabled(false);
                    fastAccel.setEnabled(false);
                    yawModule.setEnabled(false);
                    numberSetting(yawModule, "Yaw").setValue(0.0);
                    player.setVelocityClient(velocity);
                    player.setOnGround(onGround);
                    player.setYaw(yaw);
                    client.options.forwardKey.setPressed(forward);
                }
            });

            context.runOnClient(client -> {
                var player = client.player;
                boolean forward = client.options.forwardKey.isPressed();
                client.options.forwardKey.setPressed(true);
                try {
                    var boat = new net.minecraft.entity.vehicle.BoatEntity(
                            net.minecraft.entity.EntityType.OAK_BOAT, client.world, () -> Items.OAK_BOAT);
                    boat.setPosition(player.getX(), player.getY(), player.getZ());
                    client.world.addEntity(boat);
                    require(player.startRiding(boat, true, true), "EntitySpeed test could not mount the boat");
                    require(player.getVehicle() == boat, "EntitySpeed test has no vehicle");
                    var entitySpeed = BytePhase.modules.get(dev.bytephase.module.movement.EntitySpeed.class);
                    boat.setVelocityClient(net.minecraft.util.math.Vec3d.ZERO);
                    player.setYaw(0.0f);
                    boat.setYaw(0.0f);
                    entitySpeed.setEnabled(true);
                    entitySpeed.onTick();
                    require(Math.abs(boat.getVelocity().horizontalLength() - 0.5) < 0.001,
                            "EntitySpeed moved the boat to " + boat.getVelocity().horizontalLength());
                    require(boat.getVelocity().z > 0.49, "EntitySpeed used the wrong direction");
                    entitySpeed.setEnabled(false);
                    boat.setVelocityClient(net.minecraft.util.math.Vec3d.ZERO);
                    entitySpeed.onTick();
                    require(boat.getVelocity().horizontalLength() == 0.0,
                            "EntitySpeed kept driving the boat after being disabled");
                    player.stopRiding();
                    boat.discard();

                    var horse = new net.minecraft.entity.passive.HorseEntity(
                            net.minecraft.entity.EntityType.HORSE, client.world);
                    horse.setPosition(player.getX(), player.getY(), player.getZ());
                    client.world.addEntity(horse);
                    require(player.startRiding(horse, true, true), "EntityControl test could not mount the horse");
                    var entityControl = BytePhase.modules.get(dev.bytephase.module.movement.EntityControl.class);
                    require(horse.getControllingPassenger() == null,
                            "An unsaddled horse already had a controlling passenger");
                    entityControl.setEnabled(true);
                    require(horse.getControllingPassenger() == player,
                            "EntityControl did not make the unsaddled horse controllable");
                    entityControl.setEnabled(false);
                    require(horse.getControllingPassenger() == null,
                            "EntityControl kept the horse controllable after being disabled");
                    player.stopRiding();
                    horse.discard();

                    var pig = new net.minecraft.entity.passive.PigEntity(
                            net.minecraft.entity.EntityType.PIG, client.world);
                    pig.setPosition(player.getX(), player.getY(), player.getZ());
                    pig.equipStack(net.minecraft.entity.EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
                    client.world.addEntity(pig);
                    require(player.startRiding(pig, true, true), "EntityControl test could not mount the pig");
                    require(pig.getControllingPassenger() == null,
                            "A saddled pig was controllable without a carrot on a stick");
                    entityControl.setEnabled(true);
                    require(pig.getControllingPassenger() == player,
                            "EntityControl did not remove the pig's carrot requirement");
                    entityControl.setEnabled(false);
                    require(pig.getControllingPassenger() == null,
                            "EntityControl kept the pig controllable after being disabled");
                    player.stopRiding();
                    pig.discard();

                    var strider = new net.minecraft.entity.passive.StriderEntity(
                            net.minecraft.entity.EntityType.STRIDER, client.world);
                    strider.setPosition(player.getX(), player.getY(), player.getZ());
                    strider.equipStack(net.minecraft.entity.EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
                    client.world.addEntity(strider);
                    require(player.startRiding(strider, true, true),
                            "EntityControl test could not mount the strider");
                    require(strider.getControllingPassenger() == null,
                            "A saddled strider was controllable without a warped fungus on a stick");
                    entityControl.setEnabled(true);
                    require(strider.getControllingPassenger() == player,
                            "EntityControl did not remove the strider's item requirement");
                    entityControl.setEnabled(false);
                    require(strider.getControllingPassenger() == null,
                            "EntityControl kept the strider controllable after being disabled");
                    player.stopRiding();
                    strider.discard();
                } finally {
                    client.options.forwardKey.setPressed(forward);
                }
            });

            context.runOnClient(client -> {
                var smoother = new dev.bytephase.util.AimSmoother();
                smoother.reset(0.0f, 0.0f);
                smoother.tick(90.0f, 0.0f, 60.0f, 0.5f, false);
                float first = smoother.yaw();
                require(first > 0.0f && first < 30.0f,
                        "AimSmoother snapped toward the target on its first step: " + first);
                float previous = first;
                boolean monotonic = true;
                for (int i = 0; i < 30; i++) {
                    smoother.tick(90.0f, 0.0f, 60.0f, 0.5f, false);
                    if (smoother.yaw() < previous) monotonic = false;
                    previous = smoother.yaw();
                }
                require(monotonic, "AimSmoother reversed direction while settling");
                require(smoother.yaw() <= 90.0f, "AimSmoother overshot the target");
                require(90.0f - smoother.yaw() < 0.5f,
                        "AimSmoother did not settle on the target: " + smoother.yaw());
            });

            context.runOnClient(client -> {
                var player = client.player;
                float oldYaw = player.getYaw();
                float oldPitch = player.getPitch();
                try {
                    dev.bytephase.util.CameraAim.release();
                    player.setYaw(0.0f);
                    player.setPitch(0.0f);
                    dev.bytephase.util.CameraAim.aim(90.0f, 10.0f, 20.0f, 0.5f, false);
                    require(dev.bytephase.util.CameraAim.isActive(), "CameraAim did not start steering");
                    dev.bytephase.util.CameraAim.frame(player, 0.05);
                    require(player.getYaw() > 0.0f && player.getYaw() < 90.0f,
                            "CameraAim jumped straight onto the target: " + player.getYaw());
                    require(player.getPitch() > 0.0f && player.getPitch() < 10.0f,
                            "CameraAim did not ease the pitch: " + player.getPitch());
                    float previous = player.getYaw();
                    boolean monotonic = true;
                    for (int i = 0; i < 40; i++) {
                        dev.bytephase.util.CameraAim.frame(player, 0.01);
                        if (player.getYaw() < previous) monotonic = false;
                        previous = player.getYaw();
                    }
                    require(monotonic, "CameraAim reversed direction while settling");
                    require(player.getYaw() <= 90.0f + 0.5f,
                            "CameraAim overshot its target: " + player.getYaw());
                    require(90.0f - player.getYaw() < 5.0f,
                            "CameraAim did not settle on the target: " + player.getYaw());

                    // One long frame and ten short frames cover the same time,
                    // so the damped result must match: the motion is frame-rate
                    // independent.
                    player.setYaw(0.0f);
                    dev.bytephase.util.CameraAim.aim(90.0f, 0.0f, 1000.0f, 0.5f, false);
                    dev.bytephase.util.CameraAim.frame(player, 0.1);
                    float longFrame = player.getYaw();
                    player.setYaw(0.0f);
                    for (int i = 0; i < 10; i++) {
                        dev.bytephase.util.CameraAim.frame(player, 0.01);
                    }
                    require(Math.abs(longFrame - player.getYaw()) < 0.5f,
                            "CameraAim depends on the frame rate: " + longFrame + " vs " + player.getYaw());
                    dev.bytephase.util.CameraAim.release();
                    require(!dev.bytephase.util.CameraAim.isActive(),
                            "CameraAim kept steering after being released");
                } finally {
                    dev.bytephase.util.CameraAim.release();
                    player.setYaw(oldYaw);
                    player.setPitch(oldPitch);
                }
            });

            context.runOnClient(client -> {
                var player = client.player;
                var aimAssist = BytePhase.modules.get(dev.bytephase.module.combat.AimAssist.class);
                var fake = new net.minecraft.client.network.OtherClientPlayerEntity(client.world,
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "AssistTarget"));
                fake.setPosition(player.getX() + 2.5, player.getY(), player.getZ());
                client.world.addEntity(fake);
                float oldYaw = player.getYaw();
                float oldPitch = player.getPitch();
                double oldFov = numberSetting(aimAssist, "Fov").getValue();
                try {
                    numberSetting(aimAssist, "Fov").setValue(180.0);
                    player.setYaw(0.0f);
                    player.setPitch(0.0f);
                    dev.bytephase.util.CameraAim.release();
                    aimAssist.setEnabled(true);
                    aimAssist.onTick();
                    require(dev.bytephase.util.CameraAim.isActive(),
                            "AimAssist did not hand its aim to the frame controller");
                    var neck = dev.bytephase.util.RotationUtil.aimPoint(fake, player.getY());
                    var body = fake.getBoundingBox();
                    require(Math.abs((neck.y - body.minY) / body.getLengthY() - 0.80) < 0.01,
                            "AimAssist did not aim at the neck: " + neck.y);
                    require(neck.y < fake.getEyeY(),
                            "AimAssist aimed at or above the target's head: " + neck.y);
                    fake.setPosition(fake.getX(), fake.getY() + 1.0, fake.getZ());
                    var raised = fake.getBoundingBox();
                    var chest = dev.bytephase.util.RotationUtil.aimPoint(fake, player.getY());
                    require(Math.abs((chest.y - raised.minY) / raised.getLengthY() - 0.60) < 0.01,
                            "AimAssist did not drop to the chest for an elevated target: " + chest.y);
                    fake.setPosition(fake.getX(), fake.getY() - 1.0, fake.getZ());
                    require(player.getYaw() == 0.0f,
                            "AimAssist moved the camera inside the tick instead of per frame");
                    dev.bytephase.util.CameraAim.frame(player, 0.05);
                    require(player.getYaw() < 0.0f && player.getYaw() > -89.0f,
                            "AimAssist did not ease toward the target: " + player.getYaw());
                    aimAssist.setEnabled(false);
                    require(!dev.bytephase.util.CameraAim.isActive(),
                            "AimAssist kept steering after being disabled");
                } finally {
                    aimAssist.setEnabled(false);
                    dev.bytephase.util.CameraAim.release();
                    numberSetting(aimAssist, "Fov").setValue(oldFov);
                    fake.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                    player.setYaw(oldYaw);
                    player.setPitch(oldPitch);
                }
            });

            context.runOnClient(client -> {
                var player = client.player;
                var aura = BytePhase.modules.get(dev.bytephase.module.combat.KillAura.class);
                var oldYaw = player.getYaw();
                var oldPitch = player.getPitch();
                var fake = new net.minecraft.client.network.OtherClientPlayerEntity(client.world,
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "AimTarget"));
                fake.setPosition(player.getX() + 2.5, player.getY(), player.getZ());
                client.world.addEntity(fake);
                boolean oldThroughWalls = booleanSetting(aura, "ThroughWalls").getValue();
                boolean oldHumanize = booleanSetting(aura, "Humanize").getValue();
                double oldRotationSpeed = numberSetting(aura, "RotationSpeed").getValue();
                double oldSmoothing = numberSetting(aura, "Smoothing").getValue();
                try {
                    booleanSetting(aura, "ThroughWalls").setValue(true);
                    booleanSetting(aura, "Humanize").setValue(false);
                    numberSetting(aura, "RotationSpeed").setValue(60.0);
                    numberSetting(aura, "Smoothing").setValue(50.0);
                    dev.bytephase.util.SilentRotation.clear();
                    player.setYaw(0.0f);
                    player.setPitch(0.0f);
                    aura.setEnabled(true);
                    aura.onTick();
                    require(player.getYaw() == 0.0f && player.getPitch() == 0.0f,
                            "KillAura turned the camera instead of only the server rotation");
                    require(dev.bytephase.util.SilentRotation.isActive(),
                            "KillAura did not rotate for the server");
                    float step = dev.bytephase.util.SilentRotation.yaw();
                    require(step < 0.0f && step > -89.0f,
                            "KillAura did not ease into the turn: " + step);
                    for (int i = 0; i < 14; i++) {
                        aura.onTick();
                    }
                    // Compare against the live geometry: the target and the
                    // player can drift by a fraction of a block between ticks.
                    var eye = player.getEyePos();
                    var center = fake.getBoundingBox().getCenter();
                    float expected = (float) Math.toDegrees(Math.atan2(center.z - eye.z, center.x - eye.x))
                            - 90.0f;
                    require(Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(
                                    dev.bytephase.util.SilentRotation.yaw() - expected)) < 1.5f,
                            "KillAura did not settle on the target yaw: "
                                    + dev.bytephase.util.SilentRotation.yaw() + " expected " + expected);
                    require(player.getYaw() == 0.0f, "KillAura turned the camera while aiming");
                    aura.setEnabled(false);
                    require(!dev.bytephase.util.SilentRotation.isActive(),
                            "KillAura kept the server rotation after being disabled");
                    require(player.getYaw() == 0.0f, "KillAura left the camera turned");
                } finally {
                    aura.setEnabled(false);
                    booleanSetting(aura, "ThroughWalls").setValue(oldThroughWalls);
                    booleanSetting(aura, "Humanize").setValue(oldHumanize);
                    numberSetting(aura, "RotationSpeed").setValue(oldRotationSpeed);
                    numberSetting(aura, "Smoothing").setValue(oldSmoothing);
                    dev.bytephase.util.SilentRotation.clear();
                    fake.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                    player.setYaw(oldYaw);
                    player.setPitch(oldPitch);
                }
            });

            context.runOnClient(client -> {
                var player = client.player;
                var crystalModule = BytePhase.modules.get(dev.bytephase.module.combat.AutoCrystal.class);
                var oldYaw = player.getYaw();
                var oldPitch = player.getPitch();
                try {
                    var face = crystalModule.getClass().getDeclaredMethod(
                            "face", net.minecraft.util.math.Vec3d.class);
                    face.setAccessible(true);
                    dev.bytephase.util.SilentRotation.clear();
                    player.setYaw(0.0f);
                    player.setPitch(0.0f);
                    var target = player.getEyePos().add(4.0, 0.0, 0.0);
                    require(!(boolean) face.invoke(crystalModule, target),
                            "AutoCrystal snapped onto the target in a single tick");
                    require(player.getYaw() == 0.0f && player.getPitch() == 0.0f,
                            "AutoCrystal turned the camera");
                    require(dev.bytephase.util.SilentRotation.isActive(),
                            "AutoCrystal did not set a server rotation");
                    float step = dev.bytephase.util.SilentRotation.yaw();
                    require(step < 0.0f && step > -89.0f,
                            "AutoCrystal did not ease into the turn: " + step);
                    boolean aimed = false;
                    for (int i = 0; i < 10 && !aimed; i++) {
                        aimed = (boolean) face.invoke(crystalModule, target);
                    }
                    require(aimed, "AutoCrystal never finished its rotation: "
                            + dev.bytephase.util.SilentRotation.yaw());
                    require(Math.abs(dev.bytephase.util.SilentRotation.yaw() + 90.0f) < 8.5f,
                            "AutoCrystal aimed past its tolerance: "
                                    + dev.bytephase.util.SilentRotation.yaw());
                    require(player.getYaw() == 0.0f, "AutoCrystal turned the camera while aiming");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                } finally {
                    dev.bytephase.util.SilentRotation.clear();
                    player.setYaw(oldYaw);
                    player.setPitch(oldPitch);
                }
            });

            context.runOnClient(client -> {
                var viewModel = BytePhase.modules.get(dev.bytephase.module.render.ViewModel.class);
                double previousOffset = numberSetting(viewModel, "OffsetX").getValue();
                try {
                    numberSetting(viewModel, "OffsetX").setValue(0.4);
                    require(Math.abs(viewModel.offsetX(net.minecraft.util.Arm.RIGHT) - 0.4f) < 1.0e-6,
                            "ViewModel did not apply its X offset to the right arm");
                    require(Math.abs(viewModel.offsetX(net.minecraft.util.Arm.LEFT) + 0.4f) < 1.0e-6,
                            "ViewModel did not mirror its X offset on the left arm: "
                                    + viewModel.offsetX(net.minecraft.util.Arm.LEFT));
                } finally {
                    numberSetting(viewModel, "OffsetX").setValue(previousOffset);
                }
            });

            context.runOnClient(client -> {
                var player = client.player;
                var clickTp = BytePhase.modules.get(dev.bytephase.module.movement.ClickTP.class);
                var hit = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ZERO,
                        net.minecraft.util.math.Direction.UP, new BlockPos(4, 5, 6), false);
                var target = dev.bytephase.module.movement.ClickTP.teleportTarget(hit);
                require(target.equals(new net.minecraft.util.math.Vec3d(4.5, 6.0, 6.5)),
                        "ClickTP targeted " + target);
                var start = player.getEntityPos();
                dev.bytephase.module.movement.ClickTP.sendTeleport(player, start.add(2.0, 0.0, 0.0));
                require(player.getEntityPos().distanceTo(start.add(2.0, 0.0, 0.0)) < 1.0e-6,
                        "ClickTP did not move the player client-side");
                dev.bytephase.module.movement.ClickTP.sendTeleport(player, start);
                require(player.getEntityPos().distanceTo(start) < 1.0e-6,
                        "ClickTP could not restore the test position");
                clickTp.setEnabled(true);
                clickTp.onTick();
                require(player.getEntityPos().distanceTo(start) < 1.0e-6,
                        "ClickTP teleported without a click");
                clickTp.setEnabled(false);
            });
        }
    }

    private static dev.bytephase.setting.BooleanSetting booleanSetting(
            dev.bytephase.module.Module module, String name) {
        return (dev.bytephase.setting.BooleanSetting) module.getSettings().stream()
                .filter(setting -> setting.getName().equals(name)).findFirst().orElseThrow();
    }

    private static dev.bytephase.setting.NumberSetting numberSetting(
            dev.bytephase.module.Module module, String name) {
        return (dev.bytephase.setting.NumberSetting) module.getSettings().stream()
                .filter(setting -> setting.getName().equals(name)).findFirst().orElseThrow();
    }

    /** Clears the placement test area on the server so both sides stay in sync. */
    private static String clearArea(BlockPos feet) {
        return "fill " + (feet.getX() - 1) + " " + feet.getY() + " " + (feet.getZ() - 1)
                + " " + (feet.getX() + 1) + " " + (feet.getY() + 2) + " " + (feet.getZ() + 1) + " air";
    }

    private static ModeSetting mode(dev.bytephase.module.Module module, String name) {
        return (ModeSetting) module.getSettings().stream().filter(setting -> setting.getName().equals(name))
                .findFirst().orElseThrow();
    }

    private static Object getObjectField(Object instance, String name) throws ReflectiveOperationException {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static int getIntFieldInHierarchy(Object instance, String name) {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.getInt(instance);
            } catch (NoSuchFieldException ignored) {
                // Walk on to the superclass.
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
        throw new AssertionError(instance.getClass().getName() + " has no " + name + " field");
    }

    private static int getIntField(Class<?> type, Object instance, String name) {
        try {
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(instance);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setIntField(Class<?> type, Object instance, String name, int value) {
        try {
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(instance, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean getBooleanField(Class<?> type, String name) {
        return getBooleanField(type, null, name);
    }

    private static boolean getBooleanField(Class<?> type, Object instance, String name) {
        try {
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.getBoolean(instance);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
