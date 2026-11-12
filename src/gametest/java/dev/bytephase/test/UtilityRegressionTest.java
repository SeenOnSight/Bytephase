package dev.bytephase.test;

import dev.bytephase.BytePhase;
import dev.bytephase.input.Keybinds;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.module.player.AutoEat;
import dev.bytephase.module.render.Crosshair;
import dev.bytephase.module.render.ItemEsp;
import dev.bytephase.module.render.PlayerEsp;
import dev.bytephase.ui.ClickGuiScreen;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;

/** Current utility catalog, discovery, storage, input and render integration. */
public final class UtilityRegressionTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksDownload();
            world.getServer().runCommand("gamemode survival @a");
            context.waitTicks(5);
            context.runOnClient(client -> {
                Set<String> combat = Set.of("AimAssist", "AutoClicker", "AutoArmor", "AutoTotem",
                        "AutoWeapon", "WTap", "SprintReset", "JumpReset");
                Set<String> movement = Set.of("Sprint", "AutoWalk", "InventoryMove", "SafeWalk",
                        "AutoJump", "Parkour", "Sneak", "Yaw");
                for (Module module : BytePhase.modules.all()) {
                    if (module.getCategory() == Category.COMBAT) {
                        require(combat.contains(module.getName()), "Unsupported combat module is registered");
                    }
                    if (module.getCategory() == Category.MOVEMENT) {
                        require(movement.contains(module.getName()), "Unsupported movement module is registered");
                    }
                }
                for (String name : Set.of("Reach", "KillAura", "AutoCrystal", "Fly", "Speed", "Blink",
                        "PacketMine", "Nuker", "Scaffold", "XCarry", "NoSlow", "Velocity", "HitBoxes")) {
                    require(BytePhase.modules.getByName(name) == null, "Retired module is still available: " + name);
                }
                Module unsupported = new Module("Unsupported", Category.MISC, "Test fixture") {};
                unsupported.setEnabled(true);
                require(!unsupported.isEnabled(), "An unregistered module was activated");
            });
            context.runOnClient(OptimizationRegressionChecks::run);
            context.runOnClient(DiscoveryRegressionChecks::run);

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
                require(client.player.getInventory().getSelectedSlot() == 0, "AutoEat did not restore the slot");
                require(!client.options.useKey.isPressed(), "AutoEat left its input held");

                Crosshair crosshair = BytePhase.modules.get(Crosshair.class);
                Keybinds.set(crosshair, GLFW.GLFW_KEY_F8);
                KeyBinding.onKeyPressed(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_F8));
                Keybinds.tick(client);
                require(crosshair.isEnabled(), "Utility keybind did not activate");
                crosshair.setEnabled(false);
                Keybinds.set(crosshair, GLFW.GLFW_KEY_UNKNOWN);
                client.setScreen(new ClickGuiScreen());
            });
            context.waitTicks(3);
            context.takeScreenshot("utility-gui");
            context.runOnClient(client -> {
                require(client.currentScreen instanceof ClickGuiScreen, "Utility GUI did not open");
                client.setScreen(null);
                BytePhase.modules.get(PlayerEsp.class).setEnabled(true);
                BytePhase.modules.get(ItemEsp.class).setEnabled(true);
            });
            context.waitTicks(5);
            context.takeScreenshot("utility-render");
            context.runOnClient(client -> {
                try {
                    var field = BytePhase.modules.getClass().getDeclaredField("errorStates");
                    field.setAccessible(true);
                    var failures = (java.util.Map<?, ?>) field.get(BytePhase.modules);
                    require(!failures.containsKey(BytePhase.modules.get(PlayerEsp.class))
                                    && !failures.containsKey(BytePhase.modules.get(ItemEsp.class)),
                            "Utility render module failed during integration");
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                } finally {
                    BytePhase.modules.get(PlayerEsp.class).setEnabled(false);
                    BytePhase.modules.get(ItemEsp.class).setEnabled(false);
                }
            });
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
