package dev.bytephase.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.bytephase.BytePhase;
import dev.bytephase.data.LocationStore.Location;
import dev.bytephase.data.LocationStore;
import dev.bytephase.module.misc.CoordSnapper;
import dev.bytephase.module.world.Navigator;
import dev.bytephase.ui.ProfileScreen;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** All commands execute locally; coordinate tools never send chat or teleport requests to a server. */
public final class BytephaseCommand {
    private static final int PAGE_SIZE = 20;
    private static List<Location> listedFindings = List.of();
    private static String listedServer;
    private static String listedDimension;

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private BytephaseCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("bytephase")
                        .executes(context -> run(BytephaseCommand::help))
                        .then(literal("coords")
                                .executes(context -> run(() -> message("Copied "
                                        + CoordSnapper.copy(playerPosition(), true))))
                                .then(literal("look").executes(context -> run(() ->
                                        message("Copied " + CoordSnapper.copy(CoordSnapper.lookedAt(256, false), true))))))
                        .then(literal("waypoint")
                                .then(literal("list")
                                        .executes(context -> run(() -> showLocations(true, 1)))
                                        .then(argument("page", IntegerArgumentType.integer(1, 52))
                                                .executes(context -> run(() -> showLocations(true,
                                                        IntegerArgumentType.getInteger(context, "page"))))))
                                .then(literal("add")
                                        .then(argument("name", StringArgumentType.string())
                                                .executes(context -> run(() -> addWaypoint(
                                                        StringArgumentType.getString(context, "name"), playerPosition())))
                                                .then(literal("look").executes(context -> run(() -> addWaypoint(
                                                        StringArgumentType.getString(context, "name"),
                                                        CoordSnapper.lookedAt(256, false)))))))
                                .then(literal("track")
                                        .then(argument("name", StringArgumentType.string())
                                                .suggests((context, builder) -> {
                                                    BytePhase.locations.current().stream().filter(Location::waypoint)
                                                            .map(Location::name).filter(name -> name.toLowerCase(java.util.Locale.ROOT)
                                                                    .startsWith(builder.getRemainingLowerCase()))
                                                            .forEach(name -> builder.suggest(StringArgumentType.escapeIfRequired(name)));
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> run(() -> {
                                                    Location target = BytePhase.locations.select(
                                                            StringArgumentType.getString(context, "name"));
                                                    BytePhase.modules.get(Navigator.class).setEnabled(true);
                                                    message("Tracking " + describe(target));
                                                }))))
                                .then(literal("remove")
                                        .then(argument("name", StringArgumentType.string())
                                                .executes(context -> run(() -> {
                                                    String name = StringArgumentType.getString(context, "name");
                                                    if (!BytePhase.locations.removeWaypoint(name)) {
                                                        throw new IllegalArgumentException("No waypoint named " + name);
                                                    }
                                                    message("Removed waypoint " + name);
                                                })))))
                        .then(literal("findings")
                                .then(literal("list")
                                        .executes(context -> run(() -> showLocations(false, 1)))
                                        .then(argument("page", IntegerArgumentType.integer(1, 52))
                                                .executes(context -> run(() -> showLocations(false,
                                                        IntegerArgumentType.getInteger(context, "page"))))))
                                .then(literal("clear").executes(context -> run(() -> {
                                    int removed = BytePhase.locations.clearFindings();
                                    listedFindings = List.of();
                                    message("Removed " + removed + " findings in this server and dimension.");
                                })))
                                .then(literal("prune").then(argument("days", IntegerArgumentType.integer(1, 3650))
                                        .executes(context -> run(() -> message("Removed "
                                                + BytePhase.locations.prune(IntegerArgumentType.getInteger(context, "days"))
                                                + " old findings. Saved waypoints were retained.")))))
                                .then(literal("save")
                                        .then(argument("index", IntegerArgumentType.integer(1, 1024))
                                                .then(argument("name", StringArgumentType.string())
                                                        .executes(context -> run(() -> {
                                                            int index = IntegerArgumentType.getInteger(context, "index") - 1;
                                                            if (listedFindings.isEmpty()
                                                                    || !java.util.Objects.equals(listedServer, LocationStore.server())
                                                                    || !java.util.Objects.equals(listedDimension, LocationStore.dimension())) {
                                                                throw new IllegalArgumentException("List findings in this world first.");
                                                            }
                                                            if (index >= listedFindings.size()) {
                                                                throw new IllegalArgumentException("No finding with that index in the last listing.");
                                                            }
                                                            addWaypoint(StringArgumentType.getString(context, "name"),
                                                                    listedFindings.get(index).pos());
                                                        }))))))
                        .then(literal("profile")
                                .executes(context -> run(() ->
                                        BytePhase.mc.setScreen(new ProfileScreen(BytePhase.mc.currentScreen))))
                                .then(literal("list").executes(context -> run(() ->
                                        message("Profiles: " + String.join(", ", BytePhase.profiles.list())))))
                                .then(literal("save").then(argument("name", StringArgumentType.word())
                                        .executes(context -> run(() -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            BytePhase.profiles.save(name);
                                            message("Saved profile " + name);
                                        }))))
                                .then(literal("load").then(argument("name", StringArgumentType.word())
                                        .executes(context -> run(() -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            BytePhase.profiles.load(name);
                                            message("Loaded profile " + name);
                                        }))))
                                .then(literal("delete").then(argument("name", StringArgumentType.word())
                                        .executes(context -> run(() -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            if (!BytePhase.profiles.delete(name)) {
                                                throw new IllegalArgumentException("No profile named " + name);
                                            }
                                            message("Deleted profile " + name);
                                        })))))));
    }

    private static int run(Action action) {
        try {
            action.run();
            return 1;
        } catch (Exception error) {
            message(error.getMessage() == null ? "Could not complete that command." : error.getMessage());
            BytePhase.LOGGER.debug("Bytephase command failed", error);
            return 0;
        }
    }

    private static BlockPos playerPosition() {
        if (BytePhase.mc.player == null || BytePhase.mc.world == null) {
            throw new IllegalStateException("Join a world first.");
        }
        return BytePhase.mc.player.getBlockPos();
    }

    private static void addWaypoint(String name, BlockPos pos) {
        Location location = BytePhase.locations.addWaypoint(name, pos);
        BytePhase.modules.get(Navigator.class).setEnabled(true);
        message("Saved and tracking " + describe(location));
    }

    private static void showLocations(boolean waypoints, int page) {
        List<Location> locations = BytePhase.locations.current().stream()
                .filter(value -> value.waypoint() == waypoints).toList();
        if (locations.isEmpty()) {
            if (!waypoints) listedFindings = List.of();
            message("No " + (waypoints ? "waypoints" : "findings") + " in this server and dimension.");
            return;
        }
        int pages = (locations.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (page > pages) throw new IllegalArgumentException("Choose a page from 1 to " + pages + ".");
        if (!waypoints) {
            // Keep displayed indices stable while background scanners refresh findings.
            listedFindings = locations;
            listedServer = LocationStore.server();
            listedDimension = LocationStore.dimension();
        }
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(locations.size(), start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            message((index + 1) + ". " + describe(locations.get(index)));
        }
        if (pages > 1) message("Page " + page + "/" + pages + " — /bytephase "
                + (waypoints ? "waypoint" : "findings") + " list <page>");
    }

    private static String describe(Location location) {
        return location.name() + " (" + location.kind() + ") at " + location.x() + " "
                + location.y() + " " + location.z();
    }

    private static void message(String message) {
        if (BytePhase.mc != null && BytePhase.mc.inGameHud != null) {
            BytePhase.mc.inGameHud.getChatHud().addMessage(Text.literal("[Bytephase] " + message));
        }
    }

    private static void help() {
        message("/bytephase coords [look] — copy coordinates");
        message("/bytephase waypoint add <name> [look] | track <name> | remove <name> | list [page]");
        message("/bytephase findings list [page] | save <index> <name> | clear | prune <days>");
        message("/bytephase profile — open profiles; save <name> | load <name> | delete <name> | list");
        message("Use quotes around waypoint names containing spaces.");
    }
}
