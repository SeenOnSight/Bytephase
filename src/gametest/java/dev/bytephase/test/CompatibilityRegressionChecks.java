package dev.bytephase.test;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Module;
import dev.bytephase.module.combat.AutoCrystal;
import dev.bytephase.module.combat.KillAura;
import dev.bytephase.module.combat.Reach;
import dev.bytephase.module.movement.Blink;
import dev.bytephase.module.movement.FakeLag;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.util.SilentRotation;
import java.util.List;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityPosition;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

/** Targeted checks kept separate from the rendering/combat scenario suite. */
final class CompatibilityRegressionChecks {
    static void run(MinecraftClient client) {
        checkYawContinuity();
        var blink = BytePhase.modules.get(Blink.class);
        var lag = BytePhase.modules.get(FakeLag.class);
        var crystal = BytePhase.modules.get(AutoCrystal.class);
        var aura = BytePhase.modules.get(KillAura.class);
        var reach = BytePhase.modules.get(Reach.class);
        var oldWorld = client.world;
        var oldVelocity = client.player.getVelocity();
        boolean oldAttack = client.options.attackKey.isPressed();
        var modules = List.of(blink, lag, crystal, aura, reach);
        var enabled = new java.util.HashMap<Module, Boolean>();
        var settings = new java.util.HashMap<dev.bytephase.setting.Setting<?>, com.google.gson.JsonElement>();
        for (Module module : modules) {
            enabled.put(module, module.isEnabled());
            for (var setting : module.getSettings()) settings.put(setting, setting.toJson());
            module.setEnabled(false);
        }
        try {
            number(blink, "PulseDelay").setValue(0.0);
            number(blink, "CorrectionPause").setValue(10.0);
            number(lag, "CorrectionPause").setValue(10.0);
            blink.setEnabled(true);
            lag.setEnabled(true);
            var movement = new PlayerMoveC2SPacket.Full(client.player.getX(), client.player.getY(),
                    client.player.getZ(), client.player.getYaw(), client.player.getPitch(),
                    client.player.isOnGround(), client.player.horizontalCollision);
            for (Module module : List.of(blink, lag)) {
                require(module.onPacketSend(movement), module.getName() + " did not buffer movement");
                // A dimension change can keep the connection but replace the world.
                setField(module, "bufferWorld", null);
                module.onPacketSend(movement);
                require(buffer(module).size() == 1, module.getName() + " retained old-world movement");
            }
            bool(crystal, "Conservative").setValue(true);
            bool(aura, "Conservative").setValue(true);
            crystal.setEnabled(true);
            aura.setEnabled(true);
            SilentRotation.set(45.0f, 20.0f);
            client.getNetworkHandler().onPlayerPositionLook(new PlayerPositionLookS2CPacket(
                    0, EntityPosition.fromEntity(client.player), Set.of()));
            require(!SilentRotation.isActive(), "Correction retained a stale server rotation");
            for (Module module : List.of(blink, lag)) {
                require(buffer(module).isEmpty(), module.getName() + " buffered the correction acknowledgement");
                require(!module.onPacketSend(movement), module.getName() + " ignored its correction pause");
                for (int i = 0; i < 10; i++) module.onTick();
                require(module.onPacketSend(movement), module.getName() + " never resumed after correction");
                module.onServerCorrection();
            }
            require((int) field(crystal, "correctionTicks") == 10, "Crystal correction pause missing");
            require((int) field(aura, "correctionTicks") == 10, "Aura correction pause missing");
            crystal.onFrame();
            require((long) field(crystal, "cycleCount") == 0, "Crystal ran during correction pause");
            crystal.setEnabled(false);
            aura.setEnabled(false);
            blink.setEnabled(false);
            lag.setEnabled(false);

            // Age and count caps release through the re-entry guard even with Blink active.
            number(lag, "MaxPackets").setValue(5.0);
            number(lag, "MaxDelay").setValue(3.0);
            blink.setEnabled(true);
            lag.setEnabled(true);
            for (int i = 0; i < 5; i++) lag.onPacketSend(movement);
            require(buffer(lag).isEmpty(), "FakeLag exceeded MaxPackets before flushing");
            require(buffer(blink).isEmpty(), "FakeLag flush re-entered Blink");
            lag.onPacketSend(movement);
            lag.onTick();
            lag.onTick();
            require(buffer(lag).size() == 1, "FakeLag flushed before MaxDelay");
            lag.onTick();
            require(buffer(lag).isEmpty(), "FakeLag held stationary movement past MaxDelay");
            for (Module module : List.of(blink, lag)) module.onPacketSend(movement);
            client.world = null;
            blink.onTick();
            lag.onTick();
            require(buffer(blink).isEmpty() && buffer(lag).isEmpty(), "Disconnect retained movement");
            client.world = oldWorld;
            blink.setEnabled(false);
            lag.setEnabled(false);

            // Reach must not enlarge Conservative's attribute-based limits.
            number(aura, "Range").setValue(6.0);
            number(crystal, "PlaceRange").setValue(6.0);
            number(crystal, "BreakRange").setValue(6.0);
            reach.setEnabled(true);
            require(invokeDouble(aura, "effectiveRange") <= client.player.getAttributeValue(
                    EntityAttributes.ENTITY_INTERACTION_RANGE), "Conservative aura used extended Reach");
            require(invokeDouble(crystal, "effectivePlaceRange") <= client.player.getAttributeValue(
                    EntityAttributes.BLOCK_INTERACTION_RANGE), "Conservative placement used extended Reach");
            require(invokeDouble(crystal, "effectiveBreakRange") <= client.player.getAttributeValue(
                    EntityAttributes.ENTITY_INTERACTION_RANGE), "Conservative crystal attack used extended Reach");
            reach.setEnabled(false);
            number(crystal, "CyclesPerSecond").setValue(100.0);
            crystal.setEnabled(true);
            for (int i = 0; i < 10; i++) crystal.onFrame();
            require((long) field(crystal, "cycleCount") == 0, "Conservative crystal executed frame-rate cycles");
            bool(crystal, "RequireAttack").setValue(true);
            bool(aura, "RequireAttack").setValue(true);
            client.options.attackKey.setPressed(false);
            SilentRotation.set(45.0f, 20.0f);
            crystal.onTick();
            require(!SilentRotation.isActive(), "Paused crystal retained silent aim");
            SilentRotation.set(45.0f, 20.0f);
            aura.onTick();
            require(!SilentRotation.isActive(), "Aura ignored RequireAttack");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not exercise compatibility state", e);
        } finally {
            client.world = oldWorld;
            // Discard test packets rather than flushing after restoring user state.
            blink.onServerCorrection();
            lag.onServerCorrection();
            for (Module module : modules) module.setEnabled(false);
            settings.forEach((setting, value) -> setting.fromJson(value));
            enabled.forEach(Module::setEnabled);
            client.player.setVelocityClient(oldVelocity);
            client.options.attackKey.setPressed(oldAttack);
            SilentRotation.clear();
        }
    }

    private static void checkYawContinuity() {
        var aim = new dev.bytephase.util.AimSmoother();
        aim.reset(359.99f, 0.0f);
        aim.tick(0.0f, 0.0f, 45.0f, 0.5f, false);
        require(Math.abs(aim.yaw() - 360.0f) < 0.001f, "Initial yaw snap lost its revolution");
        aim.reset(359.0f, 0.0f);
        aim.tick(1.0f, 0.0f, 45.0f, 0.0f, false);
        require(Math.abs(aim.yaw() - 361.0f) < 0.001f, "Completed yaw turn snapped across 360 degrees");
        aim.reset(-359.0f, 0.0f);
        aim.tick(-1.0f, 0.0f, 45.0f, 0.0f, false);
        require(Math.abs(aim.yaw() + 361.0f) < 0.001f, "Negative yaw turn lost continuity");
        aim.reset(720.01f, 0.0f);
        aim.tick(0.0f, 90.0f, 45.0f, 0.0f, false);
        require(Math.abs(aim.yaw() - 720.0f) < 0.001f, "Multi-revolution yaw snap reset to zero");
        require(aim.pitch() > 0 && aim.pitch() <= 45.0f, "Yaw continuity changed pitch limiting");
    }

    private static NumberSetting number(Module module, String name) {
        return (NumberSetting) module.getSettings().stream()
                .filter(setting -> setting.getName().equals(name)).findFirst().orElseThrow();
    }

    private static BooleanSetting bool(Module module, String name) {
        return (BooleanSetting) module.getSettings().stream()
                .filter(setting -> setting.getName().equals(name)).findFirst().orElseThrow();
    }

    private static Object field(Object instance, String name) throws ReflectiveOperationException {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static void setField(Object instance, String name, Object value) throws ReflectiveOperationException {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static List<?> buffer(Module module) throws ReflectiveOperationException {
        return (List<?>) field(module, "buffer");
    }

    private static double invokeDouble(Object instance, String name) throws ReflectiveOperationException {
        var method = instance.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return (double) method.invoke(instance);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
