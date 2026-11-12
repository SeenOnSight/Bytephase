package dev.bytephase;

import dev.bytephase.config.ConfigManager;
import dev.bytephase.command.FakePlayerCommand;
import dev.bytephase.command.BytephaseCommand;
import dev.bytephase.config.ProfileManager;
import dev.bytephase.data.LocationStore;
import dev.bytephase.input.Keybinds;
import dev.bytephase.module.Module;
import dev.bytephase.module.ModuleManager;
import dev.bytephase.module.misc.PMSound;
import dev.bytephase.module.movement.InventoryMove;
import dev.bytephase.render.RenderBox;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;

public final class BytePhaseClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BytePhase.mc = MinecraftClient.getInstance();
        BytePhase.modules = new ModuleManager();
        BytePhase.modules.init();
        BytePhase.config = new ConfigManager();
        BytePhase.profiles = new ProfileManager();
        BytePhase.locations = new LocationStore();
        BytePhase.locations.load();
        Keybinds.init();
        FakePlayerCommand.register();
        BytephaseCommand.register();

        // Config load enables modules, and several modules read game options in
        // onEnable. Those options only exist once MinecraftClient has finished
        // constructing, so loading during onInitializeClient left Fullbright and
        // NoBob throwing a NullPointerException and silently disabling themselves.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> BytePhase.config.load());

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            InventoryMove inventoryMove = BytePhase.modules.get(InventoryMove.class);
            if (inventoryMove != null && inventoryMove.isEnabled()) inventoryMove.updateKeys();
            // Native combat runs before vanilla input/actions/movement, never
            // between a movement packet and its tick-end marker.
            BytePhase.modules.vanillaCombatTick();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Keybinds.tick(client);
            BytePhase.modules.tick();
            BytePhase.config.tick();
            BytePhase.locations.tick();
        });

        HudElementRegistry.addLast(Identifier.of("bytephase", "hud"), (context, counter) -> {
            if (BytePhase.mc.options.hudHidden) {
                RenderBox.clearFrame();
                return;
            }
            BytePhase.modules.render2D(context, counter == null ? 0.0f : counter.getTickProgress(false));
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) {
                return;
            }
            PMSound pmSound = BytePhase.modules.get(PMSound.class);
            if (pmSound != null && pmSound.isEnabled()) {
                pmSound.onGameMessage(message.getString());
            }
        });

        WorldRenderEvents.START_MAIN.register(context -> BytePhase.modules.renderSky());

        WorldRenderEvents.END_MAIN.register(context ->
                BytePhase.modules.render3D(context.matrices(), context.consumers(),
                        dev.bytephase.render.RenderProjection.tickDelta()));

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            for (Module module : BytePhase.modules.enabled()) {
                module.shutdown();
            }
            BytePhase.config.save();
            BytePhase.locations.save();
        });
    }
}
