package dev.bytephase.module.misc;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public final class CoordLogger extends Module {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BooleanSetting announce = add(new BooleanSetting("ChatAnnounce", true));
    private boolean wasDead;
    private Vec3d deathPos;
    private String deathWorld;
    private volatile String lastKnownLocation;

    public CoordLogger() {
        super("CoordLogger", Category.MISC, "Logs your coordinates when you log or die");
    }

    @Override
    protected void onEnable() {
        wasDead = false;
        deathPos = null;
        captureLocation();
    }

    @Override
    protected void onDisable() {
        wasDead = false;
        deathPos = null;
        deathWorld = null;
        lastKnownLocation = null;
    }

    @Override
    public void onTick() {
        var mc = BytePhase.mc;
        if (mc.player == null || mc.world == null) {
            wasDead = false;
            return;
        }
        captureLocation();
        if (mc.player.isDead()) {
            if (!wasDead) {
                wasDead = true;
                deathPos = mc.player.getEntityPos();
                deathWorld = mc.world.getRegistryKey().getValue().toString();
                writeLine("death " + deathWorld + " " + fmt(deathPos));
            }
            return;
        }
        if (wasDead && deathPos != null) {
            wasDead = false;
            if (announce.getValue()) {
                mc.inGameHud.getChatHud().addMessage(Text.literal(
                        "[BytePhase] Last logged death: " + deathWorld + " " + fmt(deathPos)));
            }
            deathPos = null;
            deathWorld = null;
        }
    }

    private static String fmt(Vec3d pos) {
        return String.format("%.1f %.1f %.1f", pos.x, pos.y, pos.z);
    }

    public void onDisconnect() {
        String location = lastKnownLocation;
        if (location == null) {
            return;
        }
        writeLine("logout " + location);
    }

    private void captureLocation() {
        var mc = BytePhase.mc;
        if (mc != null && mc.player != null && mc.world != null) {
            lastKnownLocation = mc.world.getRegistryKey().getValue() + " " + fmt(mc.player.getEntityPos());
        }
    }

    private void writeLine(String line) {
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("bytephase-coords.txt");
            Files.write(file, (LocalDateTime.now().format(TIME) + " " + line + "\n")
                            .getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            BytePhase.LOGGER.warn("Failed to write coordinate log {}", line, e);
        }
    }
}
