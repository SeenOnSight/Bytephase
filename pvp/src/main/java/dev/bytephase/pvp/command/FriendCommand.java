package dev.bytephase.pvp.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.bytephase.pvp.config.FriendStore;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class FriendCommand {
    private FriendCommand() { }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) ->
                dispatcher.register(literal("bytephase").then(literal("friend")
                        .then(literal("list").executes(context -> {
                            context.getSource().sendFeedback(Component.literal(
                                    "[Bytephase] Friends: " + String.join(", ", FriendStore.getInstance().all())));
                            return 1;
                        }))
                        .then(literal("add").then(argument("name", StringArgumentType.word()).executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            boolean added = FriendStore.getInstance().add(name);
                            context.getSource().sendFeedback(Component.literal(
                                    added ? "[Bytephase] Added friend " + name : "[Bytephase] Name is invalid or already a friend"));
                            return added ? 1 : 0;
                        })))
                        .then(literal("remove").then(argument("name", StringArgumentType.word()).executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            boolean removed = FriendStore.getInstance().remove(name);
                            context.getSource().sendFeedback(Component.literal(
                                    removed ? "[Bytephase] Removed friend " + name : "[Bytephase] Friend not found"));
                            return removed ? 1 : 0;
                        })))
                )));
    }
}
