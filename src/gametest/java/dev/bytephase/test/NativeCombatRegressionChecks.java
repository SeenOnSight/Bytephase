package dev.bytephase.test;

import com.mojang.authlib.GameProfile;
import dev.bytephase.BytePhase;
import dev.bytephase.module.Module;
import dev.bytephase.module.combat.AutoCrystal;
import dev.bytephase.module.combat.KillAura;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.ServerFacing;
import dev.bytephase.util.SilentRotation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class NativeCombatRegressionChecks {
    private static boolean tracing;
    private static final List<Packet<?>> packets = new ArrayList<>();

    public static void record(Packet<?> packet) {
        if (tracing && BytePhase.mc.isOnThread()) packets.add(packet);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var aura = BytePhase.modules.get(KillAura.class);
        var crystal = BytePhase.modules.get(AutoCrystal.class);
        var savedSettings = new java.util.HashMap<dev.bytephase.setting.Setting<?>, com.google.gson.JsonElement>();
        var originalAngles = new float[2];
        var target = new OtherClientPlayerEntity[1];
        var base = new BlockPos[1];
        var originalBase = new net.minecraft.block.BlockState[1];
        var originalOffhand = new ItemStack[1];
        try {
            context.runOnClient(client -> {
                for (Module module : List.of(aura, crystal)) {
                    module.setEnabled(false);
                    for (var setting : module.getSettings()) savedSettings.put(setting, setting.toJson());
                    bool(module, "VanillaPackets").setValue(true);
                    bool(module, "RequireAttack").setValue(false);
                    bool(module, "Swing").setValue(false); // Native mode must still swing.
                    number(module, "RotationSpeed").setValue(360.0);
                    number(module, "Smoothing").setValue(0.0);
                }
                bool(aura, "Humanize").setValue(false);
                net.minecraft.client.option.KeyBinding.unpressAll();
                originalAngles[0] = client.player.getYaw();
                originalAngles[1] = client.player.getPitch();
                client.player.setYaw(0);
                client.player.setPitch(0);
                client.player.setVelocityClient(Vec3d.ZERO);
                target[0] = new OtherClientPlayerEntity(client.world, new GameProfile(UUID.randomUUID(), "NativeTarget"));
                target[0].refreshPositionAndAngles(client.player.getX() + 2.5, client.player.getY(), client.player.getZ(), 0, 0);
                client.world.addEntity(target[0]);
                packets.clear();
                tracing = true;
                aura.setEnabled(true);
            });
            context.waitFor(client -> packets.stream().anyMatch(p -> p instanceof PlayerInteractEntityC2SPacket), 60);
            context.runOnClient(client -> {
                aura.setEnabled(false);
                require(!SilentRotation.isActive(), "Native aura set silent rotation");
                require(Math.abs(client.player.getYaw() + 90.0f) < 1.0f, "Native aura did not steer camera");
                checkOrder();
                target[0].remove(Entity.RemovalReason.DISCARDED);
                base[0] = client.player.getBlockPos().east(2).up();
            });
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                originalBase[0] = server.getOverworld().getBlockState(base[0]);
                originalOffhand[0] = player.getOffHandStack().copy();
                server.getOverworld().setBlockState(base[0], net.minecraft.block.Blocks.OBSIDIAN.getDefaultState());
                player.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.END_CRYSTAL, 64));
                player.currentScreenHandler.sendContentUpdates();
            });
            world.getServer().runCommand("gamemode creative @a");
            context.waitFor(client -> client.player.isCreative() && client.player.getOffHandStack().isOf(Items.END_CRYSTAL)
                    && client.world.getBlockState(base[0]).isOf(net.minecraft.block.Blocks.OBSIDIAN));
            context.runOnClient(client -> {
                // Eye is below this base's top: UP would be an impossible clicked face.
                require(client.player.getEyePos().y < base[0].getY() + 1.0, "Elevated-base fixture invalid");
                target[0] = new OtherClientPlayerEntity(client.world, new GameProfile(UUID.randomUUID(), "NativeCrystalTarget"));
                // Keep the damage target above the base: an entirely lower
                // target is shielded by the obsidian and legitimately scores zero.
                target[0].refreshPositionAndAngles(client.player.getX() + 4.5, base[0].getY() + 1.0,
                        client.player.getZ(), 0, 0);
                client.world.addEntity(target[0]);
                bool(crystal, "Break").setValue(false);
                packets.clear();
                crystal.setEnabled(true);
            });
            context.waitFor(client -> packets.stream().anyMatch(p -> p instanceof PlayerInteractBlockC2SPacket), 60);
            context.runOnClient(client -> {
                crystal.setEnabled(false);
                var place = (PlayerInteractBlockC2SPacket) packets.stream()
                        .filter(p -> p instanceof PlayerInteractBlockC2SPacket).findFirst().orElseThrow();
                require(place.getBlockHitResult().getBlockPos().equals(base[0]), "Native crystal chose wrong base");
                require(place.getBlockHitResult().getSide() == Direction.WEST, "Native crystal fabricated UP instead of visible side");
                checkOrder();
                require(!SilentRotation.isActive(), "Native crystal set silent rotation");
                Packet<?> tickEnd = packets.stream().filter(p -> p instanceof ClientTickEndC2SPacket)
                        .findFirst().orElseThrow();
                tracing = false;
                checkEvidence(client, tickEnd);
                ServerFacing.reset();
                require(!ServerFacing.canAct(), "Reset retained server-facing evidence");
            });
        } finally {
            context.runOnClient(client -> {
                tracing = false;
                aura.setEnabled(false);
                crystal.setEnabled(false);
                if (target[0] != null) target[0].remove(Entity.RemovalReason.DISCARDED);
                savedSettings.forEach((setting, value) -> setting.fromJson(value));
                client.player.setYaw(originalAngles[0]);
                client.player.setPitch(originalAngles[1]);
                client.player.setVelocityClient(Vec3d.ZERO);
                ServerFacing.startTick(false);
                packets.clear();
            });
            if (originalBase[0] != null) world.getServer().runOnServer(server -> {
                server.getOverworld().setBlockState(base[0], originalBase[0]);
                var player = server.getPlayerManager().getPlayerList().getFirst();
                player.equipStack(EquipmentSlot.OFFHAND, originalOffhand[0]);
                player.currentScreenHandler.sendContentUpdates();
                for (var entity : server.getOverworld().getEntitiesByType(net.minecraft.entity.EntityType.END_CRYSTAL, e -> true)) entity.discard();
            });
            world.getServer().runCommand("gamemode survival @a");
            context.waitFor(client -> !client.player.isCreative());
        }
    }

    private static void checkOrder() {
        int moves = 0;
        boolean afterMove = false;
        boolean hasLook = false;
        float lastYaw = 0, lastPitch = 0;
        int attacks = 0;
        for (Packet<?> packet : packets) {
            if (packet instanceof PlayerMoveC2SPacket move) {
                require(++moves <= 1, "Native combat emitted multiple movement packets before tick end");
                afterMove = true;
                if (move.changesLook()) {
                    float yaw = move.getYaw(0), pitch = move.getPitch(0);
                    require(!hasLook || yaw != lastYaw || pitch != lastPitch, "Native combat emitted duplicate look");
                    hasLook = true;
                    lastYaw = yaw;
                    lastPitch = pitch;
                }
            } else if (packet instanceof ClientTickEndC2SPacket) {
                moves = 0;
                afterMove = false;
            } else if (packet instanceof PlayerInteractEntityC2SPacket || packet instanceof PlayerInteractBlockC2SPacket) {
                require(!afterMove, "Native action sent between movement and tick end");
                require(hasLook, "Native combat acted without transmitted aim evidence");
                attacks++;
            }
        }
        require(attacks > 0, "Packet test did not exercise combat");
    }

    private static void checkEvidence(net.minecraft.client.MinecraftClient client, Packet<?> tickEnd) {
        var connection = client.getNetworkHandler().getConnection();
        var move = new PlayerMoveC2SPacket.Full(client.player.getX(), client.player.getY(), client.player.getZ(),
                0, 0, client.player.isOnGround(), client.player.horizontalCollision);
        ServerFacing.startTick(true);
        ServerFacing.reset();
        require(!ServerFacing.canAct(), "Native combat did not wait for outgoing evidence");
        ServerFacing.observe(connection, move);
        require(!ServerFacing.canAct(), "Native combat acted before tick end");
        ServerFacing.observe(connection, tickEnd);
        require(ServerFacing.canAct(), "Valid movement/tick-end evidence was not usable");
        var eye = client.player.getEyePos();
        var bounds = new net.minecraft.util.math.Box(eye.x - 0.1, eye.y - 0.1, eye.z + 2,
                eye.x + 0.1, eye.y + 0.1, eye.z + 2.2);
        var blink = BytePhase.modules.get(dev.bytephase.module.movement.Blink.class);
        try {
            blink.setEnabled(true);
            client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    90, 0, client.player.isOnGround(), client.player.horizontalCollision));
            require(ServerFacing.hasConflicts(), "Native combat did not detect Blink");
            require(ServerFacing.hitsEntity(bounds, 3), "Cancelled look advanced native-facing evidence");
        } finally {
            blink.onServerCorrection();
            blink.setEnabled(false);
        }
        ServerFacing.observe(connection, move);
        ServerFacing.observe(connection, move);
        ServerFacing.observe(connection, tickEnd);
        require(!ServerFacing.canAct(), "Multiple movement packets were treated as a clean tick");
    }

    private static BooleanSetting bool(Module module, String name) {
        return (BooleanSetting) module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private static NumberSetting number(Module module, String name) {
        return (NumberSetting) module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
