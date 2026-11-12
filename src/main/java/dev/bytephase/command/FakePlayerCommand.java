package dev.bytephase.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.bytephase.BytePhase;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Creates local-only player entities for testing player-targeting features. */
public final class FakePlayerCommand {
    private static final int MAX_FAKE_PLAYERS = 32;
    private static final List<OtherClientPlayerEntity> FAKE_PLAYERS = new ArrayList<>();
    private static ClientWorld trackedWorld;

    private FakePlayerCommand() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(FakePlayerCommand::tick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("fakeplayer")
                        .executes(context -> spawn("FakePlayer"))
                        .then(literal("spawn")
                                .executes(context -> spawn("FakePlayer"))
                                .then(argument("name", StringArgumentType.word())
                                        .executes(context -> spawn(
                                                StringArgumentType.getString(context, "name")))))
                        .then(literal("remove").executes(context -> removeLast()))
                        .then(literal("clear").executes(context -> clear()))));
    }

    private static void tick(MinecraftClient client) {
        if (client.world != trackedWorld) {
            for (OtherClientPlayerEntity fake : FAKE_PLAYERS) {
                fake.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            }
            FAKE_PLAYERS.clear();
            trackedWorld = client.world;
            return;
        }
        FAKE_PLAYERS.removeIf(OtherClientPlayerEntity::isRemoved);
    }

    private static int spawn(String name) {
        var client = BytePhase.mc;
        if (client == null || client.player == null || client.world == null) {
            return 0;
        }
        // A command may be run on the first tick after joining or changing
        // dimensions, before END_CLIENT_TICK has synchronized trackedWorld.
        // Clear old-world placeholders here so this newly spawned entity is
        // not mistaken for stale state and removed by the next tick callback.
        if (client.world != trackedWorld) {
            for (OtherClientPlayerEntity oldFake : FAKE_PLAYERS) {
                oldFake.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            }
            FAKE_PLAYERS.clear();
            trackedWorld = client.world;
        }
        if (FAKE_PLAYERS.size() >= MAX_FAKE_PLAYERS) {
            client.inGameHud.getChatHud().addMessage(Text.literal(
                    "You can have at most " + MAX_FAKE_PLAYERS + " local fake players."));
            return 0;
        }
        if (name.length() > 16) {
            client.inGameHud.getChatHud().addMessage(Text.literal("Fake player names must be 16 characters or fewer."));
            return 0;
        }

        OtherClientPlayerEntity fake = new OtherClientPlayerEntity(
                client.world, new GameProfile(UUID.randomUUID(), name));
        Vec3d forward = Vec3d.fromPolar(0.0f, client.player.getYaw()).multiply(2.0);
        fake.refreshPositionAndAngles(client.player.getX() + forward.x, client.player.getY(),
                client.player.getZ() + forward.z, client.player.getYaw(), client.player.getPitch());
        fake.setHeadYaw(client.player.getYaw());
        fake.setBodyYaw(client.player.getYaw());
        client.world.addEntity(fake);
        FAKE_PLAYERS.add(fake);
        client.inGameHud.getChatHud().addMessage(Text.literal("Spawned local fake player: " + name));
        return 1;
    }

    private static int removeLast() {
        FAKE_PLAYERS.removeIf(OtherClientPlayerEntity::isRemoved);
        if (FAKE_PLAYERS.isEmpty()) {
            var client = BytePhase.mc;
            if (client != null && client.inGameHud != null) {
                client.inGameHud.getChatHud().addMessage(Text.literal("No fake players to remove."));
            }
            return 0;
        }
        OtherClientPlayerEntity fake = FAKE_PLAYERS.remove(FAKE_PLAYERS.size() - 1);
        fake.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
        BytePhase.mc.inGameHud.getChatHud().addMessage(Text.literal("Removed fake player: " + fake.getName().getString()));
        return 1;
    }

    private static int clear() {
        FAKE_PLAYERS.removeIf(OtherClientPlayerEntity::isRemoved);
        int count = FAKE_PLAYERS.size();
        for (OtherClientPlayerEntity fake : FAKE_PLAYERS) {
            fake.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
        }
        FAKE_PLAYERS.clear();
        if (BytePhase.mc != null && BytePhase.mc.inGameHud != null) {
            BytePhase.mc.inGameHud.getChatHud().addMessage(Text.literal("Removed " + count + " fake player(s)."));
        }
        return count;
    }
}
