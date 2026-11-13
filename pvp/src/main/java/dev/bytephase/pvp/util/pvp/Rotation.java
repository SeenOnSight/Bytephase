package dev.bytephase.pvp.util.pvp;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record Rotation(float yaw, float pitch) {
}
